package com.fitmeet.chat.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmeet.chat.application.ChatMessageResult;
import com.fitmeet.chat.application.ChatRecentMessageCache;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisChatRecentMessageCache implements ChatRecentMessageCache {

    private static final Logger log = LoggerFactory.getLogger(RedisChatRecentMessageCache.class);
    private static final int RECENT_MESSAGE_LIMIT = 30;
    private static final Duration RECENT_MESSAGE_TTL = Duration.ofHours(6);
    private static final String RECENT_MESSAGE_KEY_FORMAT = "chat:room:%d:recent-messages";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatRecentMessageCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<List<ChatMessageResult>> findRecentMessages(Long roomId) {
        String key = recentMessageKey(roomId);
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return Optional.empty();
            }

            List<String> values = redisTemplate.opsForList().range(key, 0, RECENT_MESSAGE_LIMIT - 1);
            if (values == null) {
                return Optional.empty();
            }

            return Optional.of(values.stream()
                    .map(this::deserialize)
                    .toList());
        } catch (RuntimeException exception) {
            log.warn("Failed to read chat recent message cache. roomId={}", roomId, exception);
            evictQuietly(key);
            return Optional.empty();
        }
    }

    @Override
    public void cacheRecentMessages(Long roomId, List<ChatMessageResult> messages) {
        String key = recentMessageKey(roomId);
        try {
            redisTemplate.delete(key);
            if (messages.isEmpty()) {
                return;
            }

            List<String> values = messages.stream()
                    .map(this::serialize)
                    .toList();
            redisTemplate.opsForList().rightPushAll(key, values);
            redisTemplate.opsForList().trim(key, 0, RECENT_MESSAGE_LIMIT - 1);
            redisTemplate.expire(key, RECENT_MESSAGE_TTL);
        } catch (RuntimeException exception) {
            evictQuietly(key);
            log.warn("Failed to write chat recent message cache. roomId={}", roomId, exception);
        }
    }

    @Override
    public void appendRecentMessage(ChatMessageResult message) {
        String key = recentMessageKey(message.roomId());
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return;
            }

            redisTemplate.opsForList().rightPush(key, serialize(message));
            redisTemplate.opsForList().trim(key, -RECENT_MESSAGE_LIMIT, -1);
            redisTemplate.expire(key, RECENT_MESSAGE_TTL);
        } catch (RuntimeException exception) {
            evictQuietly(key);
            log.warn("Failed to append chat recent message cache. roomId={}", message.roomId(), exception);
        }
    }

    private String recentMessageKey(Long roomId) {
        return RECENT_MESSAGE_KEY_FORMAT.formatted(roomId);
    }

    private void evictQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Failed to evict chat recent message cache. key={}", key, exception);
        }
    }

    private String serialize(ChatMessageResult message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chat message cache value.", exception);
        }
    }

    private ChatMessageResult deserialize(String value) {
        try {
            return objectMapper.readValue(value, ChatMessageResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chat message cache value.", exception);
        }
    }
}
