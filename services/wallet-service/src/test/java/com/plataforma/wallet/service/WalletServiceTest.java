package com.plataforma.wallet.service;

import com.plataforma.event.WalletEventPublisher;
import com.plataforma.shared.exception.InsufficientFundsException;
import com.plataforma.shared.exception.WalletNotFoundException;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.model.Wallet;
import com.plataforma.wallet.model.WalletMovement;
import com.plataforma.wallet.repository.WalletMovementRepository;
import com.plataforma.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletMovementRepository movementRepository;
    @Mock private WalletEventPublisher eventPublisher;

    @InjectMocks private WalletService walletService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.builder()
                .id(1L)
                .userId(99L)
                .balance(new BigDecimal("100.00"))
                .currency("USD")
                .active(true)
                .build();
    }

    // ── getOrCreateWallet ──────────────────────────────────────────────────

    @Test
    void getOrCreateWallet_existente_devuelveExistente() {
        when(walletRepository.findByUserId(99L)).thenReturn(Optional.of(wallet));

        Wallet result = walletService.getOrCreateWallet(99L);

        assertEquals(wallet, result);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getOrCreateWallet_noExiste_creaNueva() {
        when(walletRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.getOrCreateWallet(99L);

        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(walletRepository, times(1)).save(any());
    }

    // ── deposit ────────────────────────────────────────────────────────────

    @Test
    void deposit_incrementaSaldo() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletMovement movement = walletService.deposit(99L, new BigDecimal("50.00"), "test", null);

        assertEquals(new BigDecimal("150.00"), wallet.getBalance());
        assertEquals(MovementType.DEPOSIT, movement.getType());
        assertEquals(new BigDecimal("100.00"), movement.getBalanceBefore());
        assertEquals(new BigDecimal("150.00"), movement.getBalanceAfter());
        verify(eventPublisher).publishFunded(eq(99L), eq(new BigDecimal("50.00")), eq(new BigDecimal("150.00")));
    }

    @Test
    void deposit_montoNegativo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                walletService.deposit(99L, new BigDecimal("-10.00"), "test", null));
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    // ── withdraw ───────────────────────────────────────────────────────────

    @Test
    void withdraw_saldoSuficiente_decrementaSaldo() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletMovement movement = walletService.withdraw(99L, new BigDecimal("40.00"), "test", null);

        assertEquals(new BigDecimal("60.00"), wallet.getBalance());
        assertEquals(MovementType.WITHDRAWAL, movement.getType());
        verify(eventPublisher).publishDebited(eq(99L), eq(new BigDecimal("40.00")), eq(new BigDecimal("60.00")));
    }

    @Test
    void withdraw_saldoInsuficiente_lanzaExcepcion() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientFundsException.class, () ->
                walletService.withdraw(99L, new BigDecimal("200.00"), "test", null));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void withdraw_billerterNoExiste_lanzaExcepcion() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class, () ->
                walletService.withdraw(99L, new BigDecimal("10.00"), "test", null));
    }

    // ── recordMovement ─────────────────────────────────────────────────────

    @Test
    void recordMovement_dividend_acreditaFondos() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.recordMovement(99L, MovementType.DIVIDEND,
                new BigDecimal("25.00"), "Dividendo proyecto 1", "div-001", "evt-1");

        assertEquals(new BigDecimal("125.00"), wallet.getBalance());
    }

    @Test
    void recordMovement_tokenPurchase_debitaFondos() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.recordMovement(99L, MovementType.TOKEN_PURCHASE,
                new BigDecimal("60.00"), "Compra tokens", "tx-001", "evt-2");

        assertEquals(new BigDecimal("40.00"), wallet.getBalance());
    }

    @Test
    void recordMovement_tokenPurchase_saldoInsuficiente_lanzaExcepcion() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientFundsException.class, () ->
                walletService.recordMovement(99L, MovementType.TOKEN_PURCHASE,
                        new BigDecimal("500.00"), "Compra tokens", "tx-001", "evt-3"));
    }

    // ── idempotencia (DD010 / paso 3) ──────────────────────────────────────

    @Test
    void recordMovement_eventoDuplicado_noProcesaSegundaVez() {
        // Primer call: el evento NO existe todavía → procesa normal
        when(movementRepository.existsByExternalEventId("evt-abc-123")).thenReturn(false);
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletMovement first = walletService.recordMovement(99L, MovementType.TOKEN_PURCHASE,
                new BigDecimal("30.00"), "Compra tokens", "tx-001", "evt-abc-123");

        assertNotNull(first);
        assertEquals(new BigDecimal("70.00"), wallet.getBalance());

        // Segundo call con MISMO eventId: ahora SÍ existe → no-op, retorna null,
        // no toca el balance ni inserta movimiento nuevo
        when(movementRepository.existsByExternalEventId("evt-abc-123")).thenReturn(true);

        WalletMovement second = walletService.recordMovement(99L, MovementType.TOKEN_PURCHASE,
                new BigDecimal("30.00"), "Compra tokens", "tx-001", "evt-abc-123");

        assertNull(second);
        assertEquals(new BigDecimal("70.00"), wallet.getBalance(), "balance no debe cambiar en duplicado");
        // movementRepository.save fue llamado solo una vez (en el primer call)
        verify(movementRepository, times(1)).save(any());
        // walletRepository.findByUserIdForUpdate también: el segundo call hace early return
        verify(walletRepository, times(1)).findByUserIdForUpdate(any());
    }

    @Test
    void recordMovement_eventIdNull_omiteCheckIdempotencia() {
        // Si eventId es null, ni siquiera se consulta existsByExternalEventId.
        // Esto permite que callers no-Kafka (ej. tests) usen recordMovement sin eventId.
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletMovement movement = walletService.recordMovement(99L, MovementType.DIVIDEND,
                new BigDecimal("10.00"), "test", "ref-x", null);

        assertNotNull(movement);
        assertNull(movement.getExternalEventId());
        verify(movementRepository, never()).existsByExternalEventId(any());
    }
}
