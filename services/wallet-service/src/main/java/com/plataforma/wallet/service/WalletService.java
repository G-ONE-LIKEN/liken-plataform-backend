package com.plataforma.wallet.service;

import com.plataforma.event.WalletEventPublisher;
import com.plataforma.shared.exception.InsufficientFundsException;
import com.plataforma.shared.exception.WalletNotFoundException;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.model.PendingWalletMovement;
import com.plataforma.wallet.model.Wallet;
import com.plataforma.wallet.model.WalletMovement;
import com.plataforma.wallet.repository.PendingWalletMovementRepository;
import com.plataforma.wallet.repository.WalletMovementRepository;
import com.plataforma.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMovementRepository movementRepository;
    private final PendingWalletMovementRepository pendingRepository;
    private final WalletEventPublisher eventPublisher;
    private final UserContextClient userContextClient;

    @Transactional(readOnly = true)
    public java.util.Optional<Wallet> findByWalletAddress(String walletAddress) {
        return walletRepository.findByWalletAddress(walletAddress);
    }

    @Transactional
    public Wallet getOrCreateWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creando billetera para usuario {}", userId);
                    return walletRepository.save(Wallet.builder()
                            .userId(userId)
                            .build());
                });
        syncLinkedWallet(userId, wallet);
        return wallet;
    }

    @Transactional
    public WalletMovement deposit(Long userId, BigDecimal amount, String description, String referenceId) {
        validatePositiveAmount(amount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        BigDecimal before = wallet.getBalance();
        BigDecimal after = before.add(amount);
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
        log.info("Deposito de {} para usuario {} - nuevo saldo: {}", amount, userId, after);
        return movement;
    }

    @Transactional
    public WalletMovement withdraw(Long userId, BigDecimal amount, String description, String referenceId) {
        validatePositiveAmount(amount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(wallet.getBalance(), amount);
        }

        BigDecimal before = wallet.getBalance();
        BigDecimal after = before.subtract(amount);
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
        log.info("Retiro de {} para usuario {} - nuevo saldo: {}", amount, userId, after);
        return movement;
    }

    @Transactional
    public WalletMovement recordMovement(Long userId, MovementType type,
                                         BigDecimal amount, String description,
                                         String referenceId, String eventId) {
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
            case DIVIDEND, P2P_SALE, REFUND -> after = before.add(amount);
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

        log.info("Movimiento {} de {} para usuario {} - nuevo saldo: {}", type, amount, userId, after);

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

    @Transactional
    public WalletMovement recordExternalMovement(Long userId, MovementType type,
                                                 BigDecimal amount, String description,
                                                 String referenceId, String eventId) {
        if (eventId != null && movementRepository.existsByExternalEventId(eventId)) {
            log.info("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return null;
        }

        validatePositiveAmount(amount);

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        BigDecimal before = wallet.getBalance();
        BigDecimal after = before;

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
        Wallet wallet = getOrCreateWallet(userId);
        return movementRepository.findByWalletOrderByCreatedAtDesc(wallet, pageable);
    }

    @Transactional
    public void recordPendingMovement(String walletAddress, MovementType type,
                                      BigDecimal amount, String description,
                                      String referenceId, String eventId) {
        if (eventId != null && pendingRepository.existsByExternalEventId(eventId)) {
            log.info("Evento {} ya guardado como pending, ignorando", eventId);
            return;
        }
        pendingRepository.save(PendingWalletMovement.builder()
                .walletAddress(walletAddress)
                .type(type)
                .amount(amount)
                .description(description)
                .referenceId(referenceId)
                .externalEventId(eventId)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("Movement pendiente guardado: wallet={} type={} amount={}",
                walletAddress, type, amount);
    }

    @Transactional
    public void reconcilePendingMovements(Long userId, String walletAddress) {
        walletRepository.findByWalletAddress(walletAddress)
                .filter(existing -> !existing.getUserId().equals(userId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("La wallet " + walletAddress + " ya esta asociada al usuario " + existing.getUserId());
                });

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .userId(userId)
                        .walletAddress(walletAddress)
                        .build()));

        if (wallet.getWalletAddress() == null || !walletAddress.equalsIgnoreCase(wallet.getWalletAddress())) {
            wallet.setWalletAddress(walletAddress);
            walletRepository.save(wallet);
        }

        var pending = pendingRepository.findByWalletAddress(walletAddress);
        if (pending.isEmpty()) {
            log.info("No hay movements pendientes para wallet={}", walletAddress);
            return;
        }

        log.info("Reconciliando {} movements pendientes para userId={} wallet={}",
                pending.size(), userId, walletAddress);

        BigDecimal currentBalance = wallet.getBalance();
        for (PendingWalletMovement p : pending) {
            BigDecimal before = currentBalance;
            currentBalance = applyMovementToBalance(currentBalance, p.getType(), p.getAmount());
            movementRepository.save(WalletMovement.builder()
                    .wallet(wallet)
                    .type(p.getType())
                    .amount(p.getAmount())
                    .balanceBefore(before)
                    .balanceAfter(currentBalance)
                    .description(p.getDescription() + " (reconciliado)")
                    .referenceId(p.getReferenceId())
                    .externalEventId(p.getExternalEventId())
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        wallet.setBalance(currentBalance);
        walletRepository.save(wallet);
        pendingRepository.deleteAll(pending);

        log.info("Reconciliacion completada: userId={} nuevoBalance={}", userId, currentBalance);
    }

    private BigDecimal applyMovementToBalance(BigDecimal balance, MovementType type, BigDecimal amount) {
        return switch (type) {
            case DIVIDEND, P2P_SALE, REFUND -> balance.add(amount);
            case TOKEN_PURCHASE, P2P_PURCHASE -> balance.subtract(amount);
            default -> throw new IllegalArgumentException("Tipo no soportado: " + type);
        };
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
    }

    private void syncLinkedWallet(Long userId, Wallet wallet) {
        UserContextClient.UserContext context = userContextClient.fetch(userId);
        String walletAddress = context != null ? context.walletAddress() : null;
        if (walletAddress == null || walletAddress.isBlank()) {
            return;
        }

        if (wallet.getWalletAddress() == null || !walletAddress.equalsIgnoreCase(wallet.getWalletAddress())) {
            wallet.setWalletAddress(walletAddress);
            walletRepository.save(wallet);
        }

        reconcilePendingMovements(userId, walletAddress);
    }
}
