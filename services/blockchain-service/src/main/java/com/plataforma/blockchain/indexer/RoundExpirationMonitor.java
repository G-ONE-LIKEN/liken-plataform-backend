package com.plataforma.blockchain.indexer;

import com.plataforma.blockchain.dto.OfferingContractRefResponse;
import com.plataforma.blockchain.service.KafkaEventPublisher;
import com.plataforma.blockchain.service.ProjectServiceClient;
import com.plataforma.blockchain.service.UnitConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detector de rondas vencidas (ADR-0024, hallazgo de auditoría).
 *
 * <p>Problema: on-chain, {@code OfferingContract} solo emite {@code RoundFailed}
 * cuando el PRIMER inversor llama {@code refund()}. Pero el frontend muestra el
 * botón de refund únicamente cuando el backend marca la ronda como FAILED — que
 * solo ocurre al consumir ese evento. Huevo y gallina: nadie podía iniciar el
 * refund desde la UI.
 *
 * <p>Solución: este monitor consulta cada offering por {@code eth_call} (gratis,
 * sin gas) y, si detecta una ronda OPEN con deadline vencido sin alcanzar el
 * soft cap, publica un {@code projects.round_failed} sintético con eventId
 * determinístico {@code expired:<address>}. La idempotencia del publisher
 * garantiza que se emite una sola vez por offering, por más que el monitor
 * corra cada minuto. ADR-0017 lo permite: la proyección refleja un estado que
 * en la cadena ya es verdadero ({@code _roundFailed()} devuelve true), solo que
 * todavía no se materializó en un evento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoundExpirationMonitor {

    private static final int STATE_OPEN = 1;

    private final Web3j web3j;
    private final ProjectServiceClient projectServiceClient;
    private final KafkaEventPublisher publisher;
    private final UnitConverter units;

    @Scheduled(fixedDelayString = "${web3.round-expiration-poll-seconds:60}000",
            initialDelayString = "${web3.round-expiration-initial-delay-seconds:30}000")
    public void checkExpiredRounds() {
        List<OfferingContractRefResponse> offerings;
        try {
            offerings = projectServiceClient.listOfferingContracts();
        } catch (Exception ex) {
            log.warn("RoundExpirationMonitor: no pude listar offerings: {}", ex.getMessage());
            return;
        }

        for (OfferingContractRefResponse ref : offerings) {
            String address = ref.getOfferingContractAddress();
            if (address == null || address.isBlank()) continue;
            try {
                checkOffering(address);
            } catch (Exception ex) {
                log.warn("RoundExpirationMonitor: error consultando {}: {}", address, ex.getMessage());
            }
        }
    }

    private void checkOffering(String address) throws Exception {
        int state = readUint(address, "state", true).intValueExact();
        if (state != STATE_OPEN) return;

        BigInteger deadline = readUint(address, "deadline", false);
        if (Instant.now().getEpochSecond() <= deadline.longValueExact()) return;

        BigInteger totalRaised = readUint(address, "totalRaised", false);
        BigInteger softCap = readUint(address, "softCap", false);
        if (totalRaised.compareTo(softCap) >= 0) {
            // Deadline pasado pero soft cap alcanzado: el emisor debe llamar
            // finalize(); no es una ronda fallida.
            return;
        }

        log.info("Ronda vencida sin soft cap detectada: offering={} raised={} softCap={}",
                address, totalRaised, softCap);

        Map<String, Object> payload = new HashMap<>();
        payload.put("offeringContractAddress", address);
        payload.put("totalRaised", units.usdcFromOnchain(totalRaised));
        payload.put("softCap", units.usdcFromOnchain(softCap));
        payload.put("txHash", "synthetic:expired");
        payload.put("blockNumber", 0L);
        payload.put("logIndex", 0L);
        payload.put("synthetic", true);

        // eventId determinístico: el publisher lo deduplica para siempre.
        publisher.publish("projects.round_failed", "expired:" + address.toLowerCase(),
                address, 0L, payload);
    }

    private BigInteger readUint(String contract, String fnName, boolean asUint8) throws Exception {
        Function fn = new Function(fnName, List.of(),
                List.of(asUint8 ? TypeReference.create(Uint8.class) : TypeReference.create(Uint256.class)));
        String encoded = FunctionEncoder.encode(fn);
        String result = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contract, encoded),
                DefaultBlockParameterName.LATEST).send().getValue();
        List<Type> decoded = FunctionReturnDecoder.decode(result, fn.getOutputParameters());
        if (decoded.isEmpty()) {
            throw new IllegalStateException("eth_call " + fnName + "() sin resultado para " + contract);
        }
        return ((org.web3j.abi.datatypes.Uint) decoded.get(0)).getValue();
    }
}
