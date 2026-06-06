package com.plataforma.blockchain.service;

import com.plataforma.blockchain.events.ContractEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.plataforma.blockchain.service.KafkaEventPublisher.eventIdOf;

/**
 * Decodifica un {@link Log} on-chain y lo publica al topic Kafka correspondiente.
 *
 * <p>Mapeo (mismo contrato que documenta {@code implementar-con-blockchain.md}):
 * <pre>
 *   TokensPurchased       → investment.token_purchased
 *   RoundFinalized        → projects.round_finalized
 *   RoundFailed           → projects.round_failed
 *   Refunded              → wallet.refund
 *   StageChanged          → projects.state_changed
 *   DividendsDeposited    → dividends.deposited
 *   DividendsWithdrawn    → dividends.claimed
 *   Transfer (LKN)        → token.transferred
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventHandlerService {

    private final KafkaEventPublisher publisher;
    private final UserLookupClient userLookup;
    private final UnitConverter units;

    /**
     * @param log log on-chain ya filtrado por address y topic.
     * @param contractKind etiqueta libre del contrato (registry / offering / distributor / token).
     */
    public void handle(Log evtLog, ContractKind contractKind) {
        if (evtLog.getTopics() == null || evtLog.getTopics().isEmpty()) return;
        String topic0 = evtLog.getTopics().get(0);
        String eventId = eventIdOf(evtLog.getTransactionHash(), evtLog.getLogIndex().longValueExact());

        switch (topic0) {
            case String s when s.equals(ContractEvents.TOKENS_PURCHASED_TOPIC) -> handleTokensPurchased(evtLog, eventId);
            case String s when s.equals(ContractEvents.ROUND_FINALIZED_TOPIC) -> handleRoundFinalized(evtLog, eventId);
            case String s when s.equals(ContractEvents.ROUND_FAILED_TOPIC) -> handleRoundFailed(evtLog, eventId);
            case String s when s.equals(ContractEvents.REFUNDED_TOPIC) -> handleRefunded(evtLog, eventId);
            case String s when s.equals(ContractEvents.STAGE_CHANGED_TOPIC) -> handleStageChanged(evtLog, eventId);
            case String s when s.equals(ContractEvents.DIVIDENDS_DEPOSITED_TOPIC) -> handleDividendsDeposited(evtLog, eventId);
            case String s when s.equals(ContractEvents.DIVIDENDS_WITHDRAWN_TOPIC) -> handleDividendsWithdrawn(evtLog, eventId);
            case String s when s.equals(ContractEvents.TRANSFER_TOPIC) -> handleTransfer(evtLog, eventId, contractKind);
            default -> log.debug("Topic no manejado: {}", topic0);
        }
    }

    // ── Handlers ────────────────────────────────────────────────────────────

    private void handleTokensPurchased(Log log, String eventId) {
        String buyer = topicAsAddress(log, 1);
        List<Type> data = decodeData(log.getData(),
                TypeReference.create(Uint256.class), TypeReference.create(Uint256.class));
        BigInteger usdcRaw = ((Uint256) data.get(0)).getValue();
        BigInteger lknRaw = ((Uint256) data.get(1)).getValue();

        Map<String, Object> payload = baseMeta(log);
        payload.put("walletAddress", buyer);
        payload.put("userId", userLookup.userIdForWallet(buyer).orElse(null));
        // projectId no viaja en el log on-chain — lo resuelve project-service al
        // recibir el evento usando offering_contract_address.
        payload.put("offeringContractAddress", log.getAddress());
        payload.put("lknAmount", units.lknFromOnchain(lknRaw));
        payload.put("usdcAmount", units.usdcFromOnchain(usdcRaw));

        publisher.publish("investment.token_purchased", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleRoundFinalized(Log log, String eventId) {
        List<Type> data = decodeData(log.getData(),
                TypeReference.create(Uint256.class), TypeReference.create(Uint256.class));
        Map<String, Object> payload = baseMeta(log);
        payload.put("offeringContractAddress", log.getAddress());
        payload.put("totalRaised", units.usdcFromOnchain(((Uint256) data.get(0)).getValue()));
        payload.put("lknSold", units.lknFromOnchain(((Uint256) data.get(1)).getValue()));

        publisher.publish("projects.round_finalized", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleRoundFailed(Log log, String eventId) {
        List<Type> data = decodeData(log.getData(),
                TypeReference.create(Uint256.class), TypeReference.create(Uint256.class));
        Map<String, Object> payload = baseMeta(log);
        payload.put("offeringContractAddress", log.getAddress());
        payload.put("totalRaised", units.usdcFromOnchain(((Uint256) data.get(0)).getValue()));
        payload.put("softCap", units.usdcFromOnchain(((Uint256) data.get(1)).getValue()));

        publisher.publish("projects.round_failed", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleRefunded(Log log, String eventId) {
        String investor = topicAsAddress(log, 1);
        List<Type> data = decodeData(log.getData(), TypeReference.create(Uint256.class));
        BigInteger usdcRaw = ((Uint256) data.get(0)).getValue();

        Optional<Long> userId = userLookup.userIdForWallet(investor);
        Map<String, Object> payload = baseMeta(log);
        payload.put("walletAddress", investor);
        payload.put("userId", userId.orElse(null));
        payload.put("usdcAmount", units.usdcFromOnchain(usdcRaw));
        payload.put("offeringContractAddress", log.getAddress());

        publisher.publish("wallet.refund", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleStageChanged(Log log, String eventId) {
        BigInteger projectIdOnchain = new BigInteger(log.getTopics().get(1).substring(2), 16);
        List<Type> data = decodeData(log.getData(), TypeReference.create(Uint8.class));
        int newStage = ((Uint8) data.get(0)).getValue().intValue();

        Map<String, Object> payload = baseMeta(log);
        payload.put("registryProjectId", projectIdOnchain.longValueExact());
        payload.put("newStage", switch (newStage) {
            case 0 -> "FUNDING";
            case 1 -> "ACTIVE";
            case 2 -> "PAUSED";
            default -> "UNKNOWN_" + newStage;
        });

        publisher.publish("projects.state_changed", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleDividendsDeposited(Log log, String eventId) {
        String depositor = topicAsAddress(log, 1);
        List<Type> data = decodeData(log.getData(), TypeReference.create(Uint256.class));
        BigInteger amount = ((Uint256) data.get(0)).getValue();

        Map<String, Object> payload = baseMeta(log);
        payload.put("depositor", depositor);
        payload.put("amount", units.usdcFromOnchain(amount));

        publisher.publish("dividends.deposited", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleDividendsWithdrawn(Log log, String eventId) {
        String holder = topicAsAddress(log, 1);
        List<Type> data = decodeData(log.getData(), TypeReference.create(Uint256.class));
        BigInteger amount = ((Uint256) data.get(0)).getValue();

        Map<String, Object> payload = baseMeta(log);
        payload.put("walletAddress", holder);
        payload.put("userId", userLookup.userIdForWallet(holder).orElse(null));
        payload.put("amount", units.usdcFromOnchain(amount));

        publisher.publish("dividends.claimed", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    private void handleTransfer(Log log, String eventId, ContractKind kind) {
        // Solo nos interesan transferencias del LinkenToken (LKN), para reconciliar
        // holdings y para el futuro marketplace P2P. USDC transfers se ignoran.
        if (kind != ContractKind.LINKEN_TOKEN) return;
        String from = topicAsAddress(log, 1);
        String to = topicAsAddress(log, 2);
        List<Type> data = decodeData(log.getData(), TypeReference.create(Uint256.class));
        BigInteger amount = ((Uint256) data.get(0)).getValue();

        // Mint (from == 0x0) y burn (to == 0x0) no son transferencias entre holders;
        // los ignoramos para la analítica de holdings.
        String zero = "0x0000000000000000000000000000000000000000";
        if (from.equalsIgnoreCase(zero) || to.equalsIgnoreCase(zero)) return;

        Map<String, Object> payload = baseMeta(log);
        payload.put("from", from);
        payload.put("to", to);
        payload.put("amount", units.lknFromOnchain(amount));

        publisher.publish("token.transferred", eventId, log.getAddress(),
                log.getBlockNumber().longValueExact(), payload);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> baseMeta(Log log) {
        Map<String, Object> m = new HashMap<>();
        m.put("txHash", log.getTransactionHash());
        m.put("blockNumber", log.getBlockNumber().longValueExact());
        m.put("logIndex", log.getLogIndex().longValueExact());
        return m;
    }

    /**
     * Lee un topic indexado como Address (los topics están en hex con padding a 32 bytes,
     * la address son los últimos 20 bytes).
     */
    private String topicAsAddress(Log log, int index) {
        String topic = log.getTopics().get(index);
        // Remueve "0x" y el padding (12 bytes = 24 hex chars), deja los 40 hex de la address.
        return "0x" + topic.substring(topic.length() - 40);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Type> decodeData(String data, TypeReference<?>... types) {
        // FunctionReturnDecoder.decode espera List<TypeReference<Type>>; los
        // varargs vienen como TypeReference<?>. Cast seguro: web3j hace el upcast
        // internamente, el bound de tipo es solo para inferencia.
        return FunctionReturnDecoder.decode(data, (List) List.of(types));
    }

    public enum ContractKind {
        LINKEN_TOKEN, REGISTRY, OFFERING, DISTRIBUTOR, USDC
    }
}
