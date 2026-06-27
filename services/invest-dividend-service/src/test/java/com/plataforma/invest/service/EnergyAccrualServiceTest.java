package com.plataforma.invest.service;

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
    private DividendBatchBuilder batchBuilder;
    private EnergyAccrualService service;

    /** In-memory fake del acumulador para simular el `findByIdForUpdate` + save. */
    private Map<Long, ProjectEnergyAccumulator> store;

    @BeforeEach
    void setUp() {
        readingRepo = mock(EnergyReadingLogRepository.class);
        accumulatorRepo = mock(ProjectEnergyAccumulatorRepository.class);
        batchBuilder = mock(DividendBatchBuilder.class);
        service = new EnergyAccrualService(readingRepo, accumulatorRepo, batchBuilder);

        store = new HashMap<>();
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
    void primeraLectura_acumulaSinDispararBatch() {
        service.accrueReading(7L, new BigDecimal("50"), LocalDateTime.parse("2026-06-26T10:00:00"));

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getPendingKwh()).isEqualByComparingTo("50");
        // 50 kWh × 0.0066 = 0.33 USDC, lejos del umbral $1.
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0.33");
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("0");
        verifyNoInteractions(batchBuilder);
    }

    @Test
    void lecturaDuplicada_porEventId_seIgnora() {
        when(readingRepo.existsByEventId("energy:7:2026-06-26T10:00")).thenReturn(true);

        service.accrueReading(7L, new BigDecimal("50"), LocalDateTime.parse("2026-06-26T10:00"));

        verify(readingRepo, never()).save(any());
        verify(accumulatorRepo, never()).save(any());
        verifyNoInteractions(batchBuilder);
    }

    @Test
    void cruceDeUmbral_invocaBatchBuilder() {
        // 160 kWh × 0.0066 = 1.056 USDC, cruza el umbral $1.
        service.accrueReading(7L, new BigDecimal("160"), LocalDateTime.parse("2026-06-26T10:00:00"));

        ProjectEnergyAccumulator acc = store.get(7L);
        // El service guardo el pending, el batchBuilder se llama despues.
        // (el builder es el que mueve a in-flight, que esta mockeado).
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("1.056");

        ArgumentCaptor<ProjectEnergyAccumulator> captor =
                ArgumentCaptor.forClass(ProjectEnergyAccumulator.class);
        verify(batchBuilder).flush(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
        assertThat(captor.getValue().getPendingUsdc()).isEqualByComparingTo("1.056");
    }

    @Test
    void conInFlightActivo_acumulaYNoDisparaBatch() {
        // Pre-poblamos el store con in-flight > 0.
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(BigDecimal.ZERO)
                .inFlightUsdc(new BigDecimal("1.50"))
                .build());

        service.accrueReading(7L, new BigDecimal("200"), LocalDateTime.parse("2026-06-26T11:00"));

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("1.50"); // sin tocar
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("1.32");  // 200×0.0066
        verifyNoInteractions(batchBuilder);
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
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0.33");
        assertThat(acc.getPendingKwh()).isEqualByComparingTo("5");
    }

    @Test
    void subtractFromInFlight_restaMontoExacto() {
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(BigDecimal.ZERO)
                .inFlightUsdc(new BigDecimal("3.00"))
                .build());

        service.subtractFromInFlight(7L, new BigDecimal("1.25"));

        assertThat(store.get(7L).getInFlightUsdc()).isEqualByComparingTo("1.75");
    }

    @Test
    void subtractFromInFlight_clampea_aCero_siRestaria_negativo() {
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(BigDecimal.ZERO)
                .inFlightUsdc(new BigDecimal("0.50"))
                .build());

        service.subtractFromInFlight(7L, new BigDecimal("2.00"));

        assertThat(store.get(7L).getInFlightUsdc()).isEqualByComparingTo("0");
    }

    @Test
    void returnToPending_devuelveMontoSumandoYRestando() {
        store.put(7L, ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(BigDecimal.ZERO)
                .pendingUsdc(new BigDecimal("0.50"))
                .inFlightUsdc(new BigDecimal("2.00"))
                .build());

        service.returnToPending(7L, new BigDecimal("0.75"));

        ProjectEnergyAccumulator acc = store.get(7L);
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("1.25");
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("1.25");
    }

    @Test
    void clearInFlight_yLuegoNuevoUmbral_disparaOtraVez() {
        service.accrueReading(7L, new BigDecimal("160"), LocalDateTime.parse("2026-06-26T10:00:00"));
        // El builder fue invocado (lo mockeamos, no movio nada al in-flight realmente).
        // Para simular el ciclo, manualmente seteamos in-flight y luego limpiamos.
        store.get(7L).setInFlightUsdc(new BigDecimal("1.056"));
        store.get(7L).setPendingUsdc(BigDecimal.ZERO);
        store.get(7L).setPendingKwh(BigDecimal.ZERO);

        service.clearInFlight(7L);
        assertThat(store.get(7L).getInFlightUsdc()).isEqualByComparingTo("0");

        service.accrueReading(7L, new BigDecimal("170"), LocalDateTime.parse("2026-06-26T11:00:00"));
        // 170 × 0.0066 = 1.122 → cruza umbral, builder se llama otra vez.
        verify(batchBuilder, times(2)).flush(any());
    }
}
