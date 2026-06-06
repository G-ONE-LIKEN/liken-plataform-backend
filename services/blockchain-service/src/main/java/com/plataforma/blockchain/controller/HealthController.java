package com.plataforma.blockchain.controller;

import com.plataforma.blockchain.indexer.IndexerCheckpoint;
import com.plataforma.blockchain.indexer.IndexerCheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de salud y observabilidad del indexer.
 *  - {@code GET /health} → 200 OK.
 *  - {@code GET /indexer/state} → checkpoint actual + head block del RPC.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class HealthController {

    private final IndexerCheckpointRepository checkpoints;
    private final Web3j web3j;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/indexer/state")
    public ResponseEntity<Map<String, Object>> indexerState() {
        BigInteger head;
        String rpcStatus;
        try {
            head = web3j.ethBlockNumber().send().getBlockNumber();
            rpcStatus = "OK";
        } catch (Exception ex) {
            head = BigInteger.ZERO;
            rpcStatus = "ERROR: " + ex.getMessage();
        }
        List<IndexerCheckpoint> cps = checkpoints.findAll();
        return ResponseEntity.ok(Map.of(
                "headBlock", head,
                "rpcStatus", rpcStatus,
                "contracts", cps.stream().map(cp -> Map.of(
                        "address", cp.getContractAddress(),
                        "lastProcessedBlock", cp.getLastProcessedBlock(),
                        "updatedAt", cp.getUpdatedAt().toString())).toList()
        ));
    }
}
