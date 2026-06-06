package com.plataforma.blockchain.events;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

import java.util.List;

/**
 * Definiciones {@link Event} de web3j para los logs que el indexer escucha.
 *
 * <p>Hay un evento por cada contrato productivo:
 * <ul>
 *   <li>{@code LinkenToken}: {@code Transfer(address,address,uint256)} ERC-20 estándar.</li>
 *   <li>{@code ProjectRegistry}: {@code StageChanged(uint256,uint8)}.</li>
 *   <li>{@code OfferingContract}: {@code TokensPurchased / RoundFinalized / RoundFailed / Refunded}.</li>
 *   <li>{@code DividendDistributor}: {@code DividendsDeposited / DividendsWithdrawn}.</li>
 * </ul>
 *
 * <p>Para cada evento se cachea el "topic[0]" (keccak256 de la firma) — así el
 * indexer mapea logs → handler con un {@code Map.get}.
 */
public final class ContractEvents {

    private ContractEvents() {}

    // ── LinkenToken.Transfer(address indexed from, address indexed to, uint256 value) ──
    public static final Event TRANSFER = new Event(
            "Transfer",
            List.of(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class, false)));
    public static final String TRANSFER_TOPIC = EventEncoder.encode(TRANSFER);

    // ── ProjectRegistry.StageChanged(uint256 indexed projectId, uint8 newStage) ──
    public static final Event STAGE_CHANGED = new Event(
            "StageChanged",
            List.of(
                    TypeReference.create(Uint256.class, true),
                    TypeReference.create(Uint8.class, false)));
    public static final String STAGE_CHANGED_TOPIC = EventEncoder.encode(STAGE_CHANGED);

    // ── OfferingContract.TokensPurchased(address indexed buyer, uint256 usdcAmount, uint256 lknAmount) ──
    public static final Event TOKENS_PURCHASED = new Event(
            "TokensPurchased",
            List.of(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class, false),
                    TypeReference.create(Uint256.class, false)));
    public static final String TOKENS_PURCHASED_TOPIC = EventEncoder.encode(TOKENS_PURCHASED);

    // ── OfferingContract.RoundFinalized(uint256 totalRaised, uint256 lknSold) ──
    public static final Event ROUND_FINALIZED = new Event(
            "RoundFinalized",
            List.of(
                    TypeReference.create(Uint256.class, false),
                    TypeReference.create(Uint256.class, false)));
    public static final String ROUND_FINALIZED_TOPIC = EventEncoder.encode(ROUND_FINALIZED);

    // ── OfferingContract.RoundFailed(uint256 totalRaised, uint256 softCap) ──
    public static final Event ROUND_FAILED = new Event(
            "RoundFailed",
            List.of(
                    TypeReference.create(Uint256.class, false),
                    TypeReference.create(Uint256.class, false)));
    public static final String ROUND_FAILED_TOPIC = EventEncoder.encode(ROUND_FAILED);

    // ── OfferingContract.Refunded(address indexed investor, uint256 usdcAmount) ──
    public static final Event REFUNDED = new Event(
            "Refunded",
            List.of(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class, false)));
    public static final String REFUNDED_TOPIC = EventEncoder.encode(REFUNDED);

    // ── DividendDistributor.DividendsDeposited(address indexed depositor, uint256 amount) ──
    public static final Event DIVIDENDS_DEPOSITED = new Event(
            "DividendsDeposited",
            List.of(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class, false)));
    public static final String DIVIDENDS_DEPOSITED_TOPIC = EventEncoder.encode(DIVIDENDS_DEPOSITED);

    // ── DividendDistributor.DividendsWithdrawn(address indexed holder, uint256 amount) ──
    public static final Event DIVIDENDS_WITHDRAWN = new Event(
            "DividendsWithdrawn",
            List.of(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class, false)));
    public static final String DIVIDENDS_WITHDRAWN_TOPIC = EventEncoder.encode(DIVIDENDS_WITHDRAWN);
}
