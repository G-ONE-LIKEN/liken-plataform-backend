package com.plataforma.wallet.service;

import com.plataforma.event.WalletEventPublisher;
import com.plataforma.shared.exception.InsufficientFundsException;
import com.plataforma.shared.exception.WalletNotFoundException;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.model.Wallet;
import com.plataforma.wallet.model.WalletMovement;
import com.plataforma.wallet.repository.WalletMovementRepository;
import com.plataforma.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMovementRepository movementRepository;
    private final WalletEventPublisher eventPublisher;

    /**
     * Devuelve la billetera del usuario, creándola si no existe (lazy creation).
     * No requiere que el usuario la inicialice explícitamente.
     */
    @Transactional
    public Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creando billetera para usuario {}", userId);
                    return walletRepository.save(Wallet.builder()
                            .userId(userId)
                            .build());
                });
    }

    /**
     * Acredita fondos en la billetera del usuario.
     * Crea la billetera si no existe.
     */
    @Transactional
    public WalletMovement deposit(Long userId, BigDecimal amount, String description, String referenceId) {
        validatePositiveAmount(amount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        BigDecimal before = wallet.getBalance();
        BigDecimal after  = before.add(amount);
        wallet.setBalance(after);
        walletRepository.save(wallet);

        WalletMovement movement = movementRepository.save(WalletMovement.builder()
                .wallet(wallet)
                .type(MovementType.DEPOSIT)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .description(description)
                .referenceId(referenceId)
                .build());

        eventPublisher.publishFunded(userId, amount, after);
        log.info("Depósito de {} para usuario {} — nuevo saldo: {}", amount, userId, after);
        return movement;
    }

    /**
     * Debita fondos de la billetera del usuario.
     * Lanza InsufficientFundsException si el saldo es insuficiente.
     */
    @Transactional
    public WalletMovement withdraw(Long userId, BigDecimal amount, String description, String referenceId) {
        validatePositiveAmount(amount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(wallet.getBalance(), amount);
        }

        BigDecimal before = wallet.getBalance();
        BigDecimal after  = before.subtract(amount);
        wallet.setBalance(after);
        walletRepository.save(wallet);

        WalletMovement movement = movementRepository.save(WalletMovement.builder()
                .wallet(wallet)
                .type(MovementType.WITHDRAWAL)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .description(description)
                .referenceId(referenceId)
                .build());

        eventPublisher.publishDebited(userId, amount, after);
        log.info("Retiro de {} para usuario {} — nuevo saldo: {}", amount, userId, after);
        return movement;
    }

    /**
     * Registra un movimiento de tipo especial (dividendo, compra/venta de tokens).
     * Usado por los consumidores de Kafka.
     *
     * Idempotente: si {@code eventId} no es null y ya existe un movimiento con ese
     * external_event_id, retorna null sin hacer nada. Esto protege contra entregas
     * duplicadas de Kafka (at-least-once — ver DD010).
     *
     * @param eventId UUID único del evento Kafka. Puede ser null si la llamada no
     *                viene de un consumer (caso poco común — los callers actuales
     *                son los 3 consumers de wallet-service).
     * @return el movimiento creado, o {@code null} si el evento ya había sido procesado.
     */
    @Transactional
    public WalletMovement recordMovement(Long userId, MovementType type,
                                         BigDecimal amount, String description,
                                         String referenceId, String eventId) {
        // ⭐ Chequeo de idempotencia: si ya procesamos este eventId, no-op
        if (eventId != null && movementRepository.existsByExternalEventId(eventId)) {
            log.info("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return null;
        }

        validatePositiveAmount(amount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        BigDecimal before = wallet.getBalance();
        BigDecimal after;

        switch (type) {
            // Acreditaciones — el backend sólo refleja un evento ya ocurrido (on-chain o P2P).
            case DIVIDEND, P2P_SALE, REFUND -> after = before.add(amount);
            // Débitos por operaciones que sí pueden requerir saldo suficiente.
            case TOKEN_PURCHASE, P2P_PURCHASE -> {
                if (before.compareTo(amount) < 0) {
                    throw new InsufficientFundsException(before, amount);
                }
                after = before.subtract(amount);
            }
            default -> throw new IllegalArgumentException(
                    "Usar deposit() o withdraw() para el tipo: " + type);
        }

        wallet.setBalance(after);
        walletRepository.save(wallet);

        log.info("Movimiento {} de {} para usuario {} — nuevo saldo: {}", type, amount, userId, after);

        return movementRepository.save(WalletMovement.builder()
                .wallet(wallet)
                .type(type)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .description(description)
                .referenceId(referenceId)
                .externalEventId(eventId)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<WalletMovement> getMovements(Long userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        return movementRepository.findByWalletOrderByCreatedAtDesc(wallet, pageable);
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
    }
}
