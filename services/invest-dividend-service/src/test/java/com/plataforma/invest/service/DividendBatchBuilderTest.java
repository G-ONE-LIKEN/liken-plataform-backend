package com.plataforma.invest.service;

import com.plataforma.invest.client.HoldingsClient;
import com.plataforma.invest.client.HoldingsClient.Holder;
import com.plataforma.invest.event.DividendPayoutBatchPublisher;
import com.plataforma.invest.event.DividendPayoutBatchPublisher.PayoutItem;
import com.plataforma.invest.model.DividendBatch;
import com.plataforma.invest.model.ProjectEnergyAccumulator;
import com.plataforma.invest.repository.DividendBatchRepository;
import com.plataforma.invest.repository.ProjectEnergyAccumulatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class DividendBatchBuilderTest {

    private HoldingsClient holdingsClient;
    private ProjectEnergyAccumulatorRepository accumulatorRepo;
    private DividendBatchRepository batchRepo;
    private DividendPayoutBatchPublisher publisher;
    private DividendBatchBuilder builder;

    @BeforeEach
    void setUp() {
        holdingsClient = mock(HoldingsClient.class);
        accumulatorRepo = mock(ProjectEnergyAccumulatorRepository.class);
        batchRepo = mock(DividendBatchRepository.class);
        publisher = mock(DividendPayoutBatchPublisher.class);
        builder = new DividendBatchBuilder(holdingsClient, accumulatorRepo, batchRepo, publisher);
    }

    private ProjectEnergyAccumulator accWithPending(BigDecimal pending) {
        return ProjectEnergyAccumulator.builder()
                .projectId(7L)
                .pendingKwh(new BigDecimal("100"))
                .pendingUsdc(pending)
                .inFlightUsdc(BigDecimal.ZERO)
                .build();
    }

    @Test
    void unSoloHolder_recibe100Pct() {
        when(holdingsClient.fetchHolders(7L)).thenReturn(List.of(
                new Holder(1L, "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", new BigDecimal("10"))));

        builder.flush(accWithPending(new BigDecimal("5.00")));

        ArgumentCaptor<List<PayoutItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(publisher).publish(anyString(), eqOk(7L), cap.capture());
        List<PayoutItem> items = cap.getValue();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).amount()).isEqualByComparingTo("5.00");
        assertThat(items.get(0).walletAddress()).isEqualTo("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void dosHolders5050_dividenIgual() {
        when(holdingsClient.fetchHolders(7L)).thenReturn(List.of(
                new Holder(1L, "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", new BigDecimal("5")),
                new Holder(2L, "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", new BigDecimal("5"))));

        builder.flush(accWithPending(new BigDecimal("3.00")));

        ArgumentCaptor<List<PayoutItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(publisher).publish(anyString(), eqOk(7L), cap.capture());
        List<PayoutItem> items = cap.getValue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).amount()).isEqualByComparingTo("1.50");
        assertThat(items.get(1).amount()).isEqualByComparingTo("1.50");
    }

    @Test
    void polvoSeFiltra_holderMuyChicoSeSkippea() {
        // Holder grande con 999 LKN, holder chico con 1 LKN sobre $1 pending.
        // chico recibiria 1/1000 = $0.001 → debajo del umbral $0.01 polvo → skip.
        when(holdingsClient.fetchHolders(7L)).thenReturn(List.of(
                new Holder(1L, "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", new BigDecimal("999")),
                new Holder(2L, "0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", new BigDecimal("1"))));

        builder.flush(accWithPending(new BigDecimal("1.00")));

        ArgumentCaptor<List<PayoutItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(publisher).publish(anyString(), eqOk(7L), cap.capture());
        List<PayoutItem> items = cap.getValue();
        // Solo el holder grande sobrevive.
        assertThat(items).hasSize(1);
        assertThat(items.get(0).walletAddress()).contains("aaaa");
    }

    @Test
    void sinHolders_noTocaAcumuladorNiPublica() {
        when(holdingsClient.fetchHolders(7L)).thenReturn(List.of());

        ProjectEnergyAccumulator acc = accWithPending(new BigDecimal("5.00"));
        builder.flush(acc);

        verifyNoInteractions(publisher);
        verify(accumulatorRepo, never()).save(any());
        verify(batchRepo, never()).save(any());
        // El acumulador sigue intacto: pending no cambia, in_flight queda 0.
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("5.00");
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("0");
    }

    @Test
    void todosPolvo_noTocaNada() {
        // 1000 holders chiquitos sobre $0.05 → cada uno recibiria 0.00005 → todos polvo.
        when(holdingsClient.fetchHolders(7L)).thenReturn(List.of(
                new Holder(1L, "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", new BigDecimal("1")),
                new Holder(2L, "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", new BigDecimal("1"))));

        ProjectEnergyAccumulator acc = accWithPending(new BigDecimal("0.005"));
        builder.flush(acc);

        verifyNoInteractions(publisher);
        verify(batchRepo, never()).save(any());
        // Pending intacto.
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0.005");
    }

    @Test
    void cuandoPublica_actualizaInFlightYPersisteBatch() {
        when(holdingsClient.fetchHolders(7L)).thenReturn(List.of(
                new Holder(1L, "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", new BigDecimal("10"))));

        ProjectEnergyAccumulator acc = accWithPending(new BigDecimal("2.00"));
        builder.flush(acc);

        // In-flight crecio, pending quedo en 0 (sin polvo), kwh reseteado.
        assertThat(acc.getInFlightUsdc()).isEqualByComparingTo("2.00");
        assertThat(acc.getPendingUsdc()).isEqualByComparingTo("0");
        assertThat(acc.getPendingKwh()).isEqualByComparingTo("0");
        assertThat(acc.getLastFlushedAt()).isNotNull();
        verify(accumulatorRepo).save(acc);

        ArgumentCaptor<DividendBatch> batchCap = ArgumentCaptor.forClass(DividendBatch.class);
        verify(batchRepo).save(batchCap.capture());
        assertThat(batchCap.getValue().getProjectId()).isEqualTo(7L);
        assertThat(batchCap.getValue().getTotalPayouts()).isEqualTo(1);
        assertThat(batchCap.getValue().getTotalAmountUsdc()).isEqualByComparingTo("2.00");
    }

    private static Long eqOk(Long v) {
        return org.mockito.ArgumentMatchers.eq(v);
    }
}
