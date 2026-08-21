package com.fitmeet.chat.domain;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_message_room_id_id", columnList = "room_id,id"),
                @Index(name = "idx_chat_message_sender_member_id", columnList = "sender_member_id")
        }
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_member_id", nullable = false)
    private Long senderMemberId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    private ChatMessage(Long roomId, Long senderMemberId, String content, ChatMessageType type) {
        validateContent(content);
        this.roomId = roomId;
        this.senderMemberId = senderMemberId;
        this.content = content.trim();
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatMessage text(Long roomId, Long senderMemberId, String content) {
        return new ChatMessage(roomId, senderMemberId, content, ChatMessageType.TEXT);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > 1000) {
            throw new BaseException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getSenderMemberId() {
        return senderMemberId;
    }

    public String getContent() {
        return content;
    }

    public ChatMessageType getType() {
        return type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
