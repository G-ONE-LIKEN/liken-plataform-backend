package com.plataforma.gateway.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserContextInvalidatedEvent {
    private Long userId;
}
