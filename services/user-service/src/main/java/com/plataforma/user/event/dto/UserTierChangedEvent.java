package com.plataforma.user.event.dto;

import com.plataforma.user.model.Tier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Publicado por invest-dividend-service en el topico {@code user.tier_changed}
 * cuando el monto total invertido del usuario cruza un umbral de tier.
 *
 * user-service lo consume para actualizar el campo {@code tier} del usuario.
 *
 * Campos canonicos (ver DD010): eventId, occurredAt, version.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserTierChangedEvent {
    private String eventId;       // UUID v4
    private String occurredAt;    // ISO 8601 UTC
    private int version;          // version del schema

    private Long userId;
    private Tier oldTier;
    private Tier newTier;
}
