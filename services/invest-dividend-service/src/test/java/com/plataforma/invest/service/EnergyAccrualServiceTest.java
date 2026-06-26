package com.plataforma.invest.service;

import com.plataforma.invest.event.DividendDepositRequestedPublisher;
import com.plataforma.invest.model.EnergyReadingLog;
import com.plataforma.invest.model.ProjectEnergyAccumulator;
import com.plataforma.invest.repository.EnergyReadingLogRepository;
import com.plataforma.invest.repository.ProjectEnergyAccumulatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
class EnergyAccrualServiceTest {

    private EnergyReadingLogRepository readingRepo;
    private ProjectEnergyAccumulatorRepository accumulatorRepo;
    private DividendDepositRequestedPublisher publisher;
    private EnergyAccrualService service;

    /** In-memory fake del acumulador para simular el `findByIdForUpdate` + save. */
    private Map<Long, ProjectEnergyAccumulator> store;

    @BeforeEach
    void setUp() {
        readingRepo = mock(EnergyReadingLogRepository.class);
        accumulatorRepo = mock(ProjectEnergyAccumulatorRepository.class);
        publisher = mock(DividendDepositRequestedPublisher.class);
        service = new EnergyAccrualService(readingRepo, accumulatorRepo, publisher);

        store = new HashMap<>();
        // ensureExists: crea row con ceros si no existe.
        doAnswer(inv -> {
            Long pid = inv.getArgument(0);
            store.computeIfAbsent(pid, id -> ProjectEnergyAccumulator.builder()
                    .projectId(id)
                    .pendingKwh(BigDecimal.ZERO)
                    .pendingUsdc(BigDecimal.ZERO)
                    .inFlightUsdc(BigDecimal.ZERO)
                    .build());
            return null;
        }).when(accumulatorRepo).ensureExists(anyLong());

        when(accumulatorRepo.findByIdForUpdate(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(store.get((Long) inv.getArgument(0))));

        when(accumulatorRepo.save(any(ProjectEnergyAccumulator.class)))
                .thenAnswer(inv -> {
                    ProjectEnergyAccumulator a = inv.getArgument(0);
                    store.put(a.getProjectId(), a);
                    return a;
                });

        when(readingRepo.existsByEventId(anyString())).thenReturn(false);
        when(readingRepo.save(any(EnergyReadingLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void primeraLectura_acumulaSinDisparar() {
        service.accrueReading(7L, new BigDecimal("5"), LocalDateTime.parse("2026-06-26T10:00:00"));

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getPendingKwh()).isEqualByComparingTo("5");
        // 5 kWh × 0.066 = 0.33 USDC, lejos del umbral.
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0.33");
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("0");
        verifyNoInteractions(publisher);
    }

    @Test
    void lecturaDuplicada_porEventId_seIgnora() {
        when(readingRepo.existsByEventId("energy:7:2026-06-26T10:00")).thenReturn(true);

        service.accrueReading(7L, new BigDecimal("5"), LocalDateTime.parse("2026-06-26T10:00"));

        verify(readingRepo, never()).save(any());
        verify(accumulatorRepo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void cruceDeUmbral_mueveAInFlightYPublica() {
        // 16 kWh × 0.066 = 1.056 USDC, cruza el umbral $1.
        service.accrueReading(7L, new BigDecimal("16"), LocalDateTime.parse("2026-06-26T10:00:00"));

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("1.056");
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0");
        assertThat(acc.getPendingKwh()).isEqualByComparingTo("0");
        assertThat(acc.getLastFlushedAt()).isNotNull();

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(publisher).publish(eq(7L), amount.capture(), anyString());
        assertThat(amount.getValue()).isEqualByComparingTo("1.056");
    }

    @Test
    void conInFlightActivo_acumulaYNoDispara() {
        // Pre-poblamos el store con in-flight > 0.
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(BigDecimal.ZERO)
                .inFlightUsdc(new BigDecimal("1.50"))
                .build());

        // Esta lectura cruzaria el umbral si in-flight fuera 0, pero como hay
        // un deposit pendiente NO debe disparar otro.
        service.accrueReading(7L, new BigDecimal("20"), LocalDateTime.parse("2026-06-26T11:00"));

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("1.50"); // sin tocar
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("1.32");  // 20×0.066
        verifyNoInteractions(publisher);
    }

    @Test
    void clearInFlight_reseteaSoloElInFlight() {
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(new BigDecimal("5"))
                .pendingUsdc(new BigDecimal("0.33"))
                .inFlightUsdc(new BigDecimal("2.00"))
                .build());

        service.clearInFlight(7L);

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("0");
        // El pending acumulado durante el in-flight queda intacto.
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0.33");
        assertThat(acc.getPendingKwh()).isEqualByComparingTo("5");
    }

    @Test
    void rollbackInFlight_devuelvePendingYResetea() {
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(new BigDecimal("0.50"))
                .inFlightUsdc(new BigDecimal("2.00"))
                .build());

        service.rollbackInFlight(7L);

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("0");
        // 0.50 viejo + 2.00 rescatado = 2.50 listo para el proximo intento.
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("2.50");
    }

    @Test
    void rollbackInFlight_sinInFlight_esNoOp() {
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(new BigDecimal("0.50"))
                .inFlightUsdc(BigDecimal.ZERO)
                .build());

        service.rollbackInFlight(7L);

        assertThat(store.get(7L).getPendingUsdc()).isEqualByComparingTo("0.50");
        verify(accumulatorRepo, never()).save(any());
    }

    @Test
    void clearInFlight_yLuegoNuevoUmbral_disparaOtraVez() {
        // Simulamos un ciclo completo: cruzar umbral → confirmar → cruzar otra vez.
        service.accrueReading(7L, new BigDecimal("16"), LocalDateTime.parse("2026-06-26T10:00:00"));
        assertThat(store.get(7L).getInFlightUsdc()).isEqualByComparingTo("1.056");

        service.clearInFlight(7L);
        assertThat(store.get(7L).getInFlightUsdc()).isEqualByComparingTo("0");

        service.accrueReading(7L, new BigDecimal("17"), LocalDateTime.parse("2026-06-26T11:00:00"));
        // 17 × 0.066 = 1.122
        assertThat(store.get(7L).getInFlightUsdc()).isEqualByComparingTo("1.122");
        verify(publisher, times(2)).publish(eq(7L), any(), anyString());
    }
}
