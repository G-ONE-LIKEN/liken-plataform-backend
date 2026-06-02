package com.plataforma.notification.service;

import com.plataforma.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantiene los SseEmitter activos por usuario. Cuando un evento genera una
 * notificación, se busca aquí el set de emitters del destinatario y se le
 * pushea por SSE. Si el usuario no está conectado, simplemente queda en DB
 * y la verá al refrescar la lista o conectarse.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    /** 30 minutos. Browser reconecta automáticamente al expirar. */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable cleanup = () -> {
            Set<SseEmitter> set = emittersByUser.get(userId);
            if (set != null) {
                set.remove(emitter);
                if (set.isEmpty()) emittersByUser.remove(userId);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    public void push(Long userId, NotificationResponse notification) {
        Set<SseEmitter> set = emittersByUser.get(userId);
        if (set == null || set.isEmpty()) return;

        set.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
                return false;
            } catch (IOException e) {
                emitter.complete();
                return true;
            }
        });
    }

    public int activeConnections() {
        return emittersByUser.values().stream().mapToInt(Set::size).sum();
    }
}
