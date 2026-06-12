package com.plataforma.blockchain.indexer;

import com.plataforma.blockchain.config.ContractsProperties;
import com.plataforma.blockchain.config.Web3Properties;
import com.plataforma.blockchain.dto.OfferingContractRefResponse;
import com.plataforma.blockchain.service.EventHandlerService;
import com.plataforma.blockchain.service.EventHandlerService.ContractKind;
import com.plataforma.blockchain.service.ProjectServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainIndexer {

    private final Web3j web3j;
    private final Web3Properties web3Props;
    private final ContractsProperties contracts;
    private final IndexerCheckpointRepository checkpoints;
    private final EventHandlerService handler;
    private final ProjectServiceClient projectServiceClient;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private final Map<String, ContractKind> contractMap = new LinkedHashMap<>();

    /**
     * Métrica indexer.lag.blocks (ADR-0025): bloques entre el head confirmado
     * y el checkpoint más atrasado. Si crece sostenido, el indexer no sigue
     * el ritmo de la chain (RPC caído, errores repetidos) → alertar.
     */
    private final java.util.concurrent.atomic.AtomicLong lagBlocks = new java.util.concurrent.atomic.AtomicLong();

    @jakarta.annotation.PostConstruct
    void registerMetrics() {
        meterRegistry.gauge("indexer.lag.blocks", lagBlocks);
    }

    @Scheduled(fixedDelayString = "${web3.poll-interval-seconds:6}000",
            initialDelayString = "${web3.initial-delay-seconds:10}000")
    @Transactional
    public void scan() {
        if (!isConfigured()) {
            log.debug("Skip indexer: addresses on-chain aun no configuradas.");
            return;
        }
        ensureStaticContracts();
        refreshOfferingContracts();

        BigInteger headBlock;
        try {
            headBlock = web3j.ethBlockNumber().send().getBlockNumber();
        } catch (Exception ex) {
            log.warn("No pude leer head block del RPC: {}", ex.getMessage());
            return;
        }

        long safeHead = Math.max(0, headBlock.longValueExact() - web3Props.getConfirmations());
        for (Map.Entry<String, ContractKind> entry : contractMap.entrySet()) {
            try {
                scanContract(entry.getKey(), entry.getValue(), safeHead);
            } catch (Exception ex) {
                log.error("Error escaneando {} ({}): {}", entry.getKey(), entry.getValue(), ex.getMessage(), ex);
            }
        }
        updateLagMetric(safeHead);
    }

    private void updateLagMetric(long safeHead) {
        long maxLag = 0;
        for (String address : contractMap.keySet()) {
            long lag = checkpoints.findById(address.toLowerCase())
                    .map(cp -> Math.max(0, safeHead - cp.getLastProcessedBlock()))
                    .orElse(0L);
            maxLag = Math.max(maxLag, lag);
        }
        lagBlocks.set(maxLag);
    }

    private void scanContract(String address, ContractKind kind, long safeHead) throws Exception {
        IndexerCheckpoint cp = checkpoints.findById(address.toLowerCase())
                .orElseGet(() -> IndexerCheckpoint.builder()
                        .contractAddress(address.toLowerCase())
                        .lastProcessedBlock(Math.max(0, safeHead - 1))
                        .updatedAt(LocalDateTime.now())
                        .build());

        long from = cp.getLastProcessedBlock() + 1;
        if (from > safeHead) {
            return;
        }

        long range = web3Props.getMaxBlockRange();
        long to = Math.min(from + range - 1, safeHead);

        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(from)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(to)),
                address);
        EthLog response = web3j.ethGetLogs(filter).send();
        List<EthLog.LogResult> logs = response.getLogs();

        if (logs != null && !logs.isEmpty()) {
            log.info("Indexer: {} logs en {} ({}..{})", logs.size(), address, from, to);
            for (EthLog.LogResult lr : logs) {
                Log log = (Log) lr.get();
                handler.handle(log, kind);
            }
        }

        cp.setLastProcessedBlock(to);
        cp.setUpdatedAt(LocalDateTime.now());
        checkpoints.save(cp);
    }

    private void ensureStaticContracts() {
        if (!contractMap.isEmpty()) return;
        addIfSet(contracts.getLinkenToken(), ContractKind.LINKEN_TOKEN);
        addIfSet(contracts.getRegistry(), ContractKind.REGISTRY);
        addIfSet(contracts.getDistributor(), ContractKind.DISTRIBUTOR);
    }

    private void refreshOfferingContracts() {
        try {
            for (OfferingContractRefResponse ref : projectServiceClient.listOfferingContracts()) {
                addIfSet(ref.getOfferingContractAddress(), ContractKind.OFFERING);
            }
        } catch (Exception ex) {
            log.warn("No pude refrescar offering contracts desde project-service: {}", ex.getMessage());
        }
    }

    private void addIfSet(String address, ContractKind kind) {
        if (address != null && !address.isBlank()
                && !address.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
            ContractKind previous = contractMap.putIfAbsent(address, kind);
            if (previous == null) {
                log.info("Indexer monitorizando {} -> {}", kind, address);
            }
        }
    }

    private boolean isConfigured() {
        return notZero(contracts.getLinkenToken())
                || notZero(contracts.getRegistry())
                || notZero(contracts.getDistributor());
    }

    private boolean notZero(String s) {
        return s != null && !s.isBlank()
                && !s.equalsIgnoreCase("0x0000000000000000000000000000000000000000");
    }
}
