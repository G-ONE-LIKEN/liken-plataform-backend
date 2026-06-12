package com.plataforma.user.service;

import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.event.producer.WalletLinkedEventProducer;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vinculo de wallet on-chain a un usuario por firma de nonce (custodia hibrida).
 *
 * <p>Flujo:
 * <ol>
 *   <li>Usuario pide un nonce ({@link #generateNonce(Long)}).</li>
 *   <li>Frontend muestra al usuario el mensaje {@code "Linken wallet binding: <nonce>"}
 *       y pide a MetaMask que lo firme con {@code personal_sign}.</li>
 *   <li>Frontend envia {@code walletAddress} + {@code signature} a
 *       {@link #linkWallet(Long, String, String)}.</li>
 *   <li>Este servicio reconstruye la direccion con {@code ecrecover} y la compara con
 *       la declarada. Si coinciden, persiste la direccion en formato EIP-55.</li>
 * </ol>
 *
 * <p>El backend NUNCA toca la clave privada. La firma demuestra propiedad sin revelarla.
 *
 * <p>El nonce vive en memoria con TTL corto ({@link #NONCE_TTL}). Para produccion con
 * multiples réplicas, mover a Redis. Por ahora es suficiente porque el flujo es
 * sincronico (mismo proceso atiende /nonce y /wallet en pocos segundos).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletLinkingService {

    private static final Duration NONCE_TTL = Duration.ofMinutes(5);
    private static final String MESSAGE_PREFIX = "Linken wallet binding: ";

    private final UserRepository userRepository;
    private final WalletLinkedEventProducer walletLinkedEventProducer;
    private final ConcurrentHashMap<Long, NonceEntry> nonces = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    // ── API publica ────────────────────────────────────────────────────────

    /**
     * Genera un nonce nuevo para {@code userId} y lo guarda con TTL.
     * Si ya habia uno vigente, se reemplaza (igual que "Re-send code").
     *
     * @return nonce hex (32 chars). El mensaje a firmar es {@code MESSAGE_PREFIX + nonce}.
     */
    public String generateNonce(Long userId) {
        cleanExpired();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String nonce = HexFormat.of().formatHex(bytes);
        nonces.put(userId, new NonceEntry(nonce, Instant.now().plus(NONCE_TTL)));
        log.debug("Nonce generado para userId={}", userId);
        return nonce;
    }

    /**
     * Devuelve el mensaje completo que el frontend debe pedirle a MetaMask que firme.
     */
    public String buildMessage(String nonce) {
        return MESSAGE_PREFIX + nonce;
    }

    /**
     * Verifica la firma y persiste {@code walletAddress} si todo encaja.
     *
     * @throws IllegalArgumentException si la wallet esta mal formateada, ya pertenece a
     *         otro usuario, o la firma no corresponde.
     * @throws IllegalStateException si el nonce expiro o no existe.
     */
    @Transactional
    public User linkWallet(Long userId, String walletAddress, String signature) {
        NonceEntry entry = nonces.get(userId);
        if (entry == null || entry.expiresAt.isBefore(Instant.now())) {
            nonces.remove(userId);
            throw new IllegalStateException("Nonce expirado o no encontrado. Solicita uno nuevo.");
        }

        if (walletAddress == null || !walletAddress.matches("^0x[a-fA-F0-9]{40}$")) {
            throw new IllegalArgumentException("Formato de wallet invalido (esperado 0x + 40 hex).");
        }
        if (signature == null || !signature.matches("^0x[a-fA-F0-9]{130}$")) {
            throw new IllegalArgumentException("Formato de firma invalido (esperado 0x + 130 hex).");
        }

        String message = buildMessage(entry.nonce);
        String recovered;
        try {
            recovered = recoverSigner(message, signature);
        } catch (Exception ex) {
            log.warn("ecrecover fallo para userId={} wallet={}: {}", userId, walletAddress, ex.getMessage());
            throw new IllegalArgumentException("No se pudo verificar la firma: " + ex.getMessage());
        }
        if (!recovered.equalsIgnoreCase(walletAddress)) {
            throw new IllegalArgumentException("La firma no corresponde a la wallet declarada.");
        }

        String checksum = Keys.toChecksumAddress(walletAddress);
        userRepository.findByWalletAddress(checksum)
                .filter(u -> !u.getId().equals(userId))
                .ifPresent(u -> {
                    throw new IllegalArgumentException("Esa wallet ya esta vinculada a otro usuario.");
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setWalletAddress(checksum);
        User saved = userRepository.save(user);

        nonces.remove(userId);
        log.info("Wallet vinculada: userId={} address={}", userId, checksum);

        walletLinkedEventProducer.publish(userId, checksum);

        return saved;
    }

    // ── Crypto helpers ─────────────────────────────────────────────────────

    /**
     * EIP-191 personal_sign:
     *   prefix = "\x19Ethereum Signed Message:\n" + length(message)
     *   digest = keccak256(prefix || message)
     *   pub = ecrecover(digest, sig)
     *   address = keccak256(pub)[12:]
     *
     * Lo hace todo web3j-crypto con {@link Sign#signedPrefixedMessageToKey}.
     */
    private String recoverSigner(String message, String signatureHex) throws SignatureException {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] sigBytes = Numeric.hexStringToByteArray(signatureHex);
        if (sigBytes.length != 65) {
            throw new IllegalArgumentException("Firma debe ser exactamente 65 bytes (r||s||v).");
        }
        byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
        byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);
        byte v = sigBytes[64];
        // MetaMask envia v en 27/28, web3j lo acepta asi; algunos wallets envian 0/1 — normalizamos.
        if (v < 27) {
            v += 27;
        }
        Sign.SignatureData sigData = new Sign.SignatureData(v, r, s);
        BigInteger pubKey = Sign.signedPrefixedMessageToKey(messageBytes, sigData);
        return "0x" + Keys.getAddress(pubKey);
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        nonces.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private record NonceEntry(String nonce, Instant expiresAt) {}
}
