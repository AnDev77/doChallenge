package com.fitmeet.chat.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class ChatRoomPresenceService {

    private final Map<Long, Map<Long, AtomicInteger>> roomMemberCounts = new ConcurrentHashMap<>();
    private final Map<String, Set<PresenceKey>> sessionPresences = new ConcurrentHashMap<>();

    public void enter(String sessionId, Long roomId, Long memberId) {
        PresenceKey presenceKey = new PresenceKey(roomId, memberId);
        boolean added = sessionPresences
                .computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
                .add(presenceKey);
        if (!added) {
            return;
        }

        roomMemberCounts
                .computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(memberId, ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public void leave(String sessionId, Long roomId, Long memberId) {
        PresenceKey presenceKey = new PresenceKey(roomId, memberId);
        Set<PresenceKey> presences = sessionPresences.get(sessionId);
        if (presences == null || !presences.remove(presenceKey)) {
            return;
        }
        if (presences.isEmpty()) {
            sessionPresences.remove(sessionId);
        }

        decrease(roomId, memberId);
    }

    public void disconnect(String sessionId) {
        Set<PresenceKey> presences = sessionPresences.remove(sessionId);
        if (presences == null || presences.isEmpty()) {
            return;
        }

        for (PresenceKey presence : new HashSet<>(presences)) {
            decrease(presence.roomId(), presence.memberId());
        }
    }

    public boolean isInRoom(Long roomId, Long memberId) {
        Map<Long, AtomicInteger> memberCounts = roomMemberCounts.get(roomId);
        if (memberCounts == null) {
            return false;
        }

        AtomicInteger count = memberCounts.get(memberId);
        return count != null && count.get() > 0;
    }

    private void decrease(Long roomId, Long memberId) {
        Map<Long, AtomicInteger> memberCounts = roomMemberCounts.get(roomId);
        if (memberCounts == null) {
            return;
        }

        AtomicInteger count = memberCounts.get(memberId);
        if (count == null) {
            return;
        }

        if (count.decrementAndGet() <= 0) {
            memberCounts.remove(memberId);
        }
        if (memberCounts.isEmpty()) {
            roomMemberCounts.remove(roomId);
        }
    }

    private record PresenceKey(
            Long roomId,
            Long memberId
    ) {

        private PresenceKey {
            Objects.requireNonNull(roomId, "roomId must not be null");
            Objects.requireNonNull(memberId, "memberId must not be null");
        }
    }
}
