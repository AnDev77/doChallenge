package com.fitmeet.notification.application;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationSseService {

    private static final long DEFAULT_TIMEOUT_MILLIS = 60L * 60L * 1000L;

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(memberId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(() -> remove(memberId, emitter));
        emitter.onError(error -> remove(memberId, emitter));

        sendToEmitter(memberId, emitter, "CONNECTED", "connected");
        return emitter;
    }

    public void send(Long memberId, NotificationEvent event) {
        Set<SseEmitter> memberEmitters = emitters.get(memberId);
        if (memberEmitters == null || memberEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : memberEmitters) {
            sendToEmitter(memberId, emitter, event.type(), event);
        }
    }

    private void sendToEmitter(Long memberId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException | IllegalStateException exception) {
            remove(memberId, emitter);
        }
    }

    private void remove(Long memberId, SseEmitter emitter) {
        Set<SseEmitter> memberEmitters = emitters.get(memberId);
        if (memberEmitters == null) {
            return;
        }
        memberEmitters.remove(emitter);
        if (memberEmitters.isEmpty()) {
            emitters.remove(memberId);
        }
    }
}
