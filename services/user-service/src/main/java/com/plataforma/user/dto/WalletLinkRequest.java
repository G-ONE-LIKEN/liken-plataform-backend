package com.plataforma.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para vincular una wallet on-chain a un usuario.
 *
 * El usuario debe:
 *  1. Pedir un nonce a {@code POST /api/users/me/wallet/nonce}.
 *  2. Firmar con MetaMask el mensaje {@code "Linken wallet binding: <nonce>"}
 *     usando {@code personal_sign} (EIP-191).
 *  3. Enviar {@code walletAddress} + {@code signature} (hex 0x... de 65 bytes) aca.
 *
 * El backend verifica con ecrecover que la firma corresponda a la wallet
 * declarada. Si todo encaja, persiste la direccion en EIP-55 checksum.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WalletLinkRequest {
    /** Direccion 0x + 40 hex chars. Cualquier casing — se normaliza a EIP-55. */
    private String walletAddress;
    /** Firma personal_sign hex 0x... (65 bytes: r||s||v). */
    private String signature;
}
