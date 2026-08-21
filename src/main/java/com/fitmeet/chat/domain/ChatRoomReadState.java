package com.fitmeet.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_room_read_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_room_read_state_room_id_member_id", columnNames = {"room_id", "member_id"})
        },
        indexes = {
                @Index(name = "idx_chat_room_read_state_member_id", columnList = "member_id")
        }
)
public class ChatRoomReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;

    protected ChatRoomReadState() {
    }

    private ChatRoomReadState(Long roomId, Long memberId, Long lastReadMessageId) {
        this.roomId = roomId;
        this.memberId = memberId;
        this.lastReadMessageId = lastReadMessageId;
        this.lastReadAt = LocalDateTime.now();
    }

    public static ChatRoomReadState create(Long roomId, Long memberId, Long lastReadMessageId) {
        return new ChatRoomReadState(roomId, memberId, lastReadMessageId);
    }

    public void markAsRead(Long messageId) {
        if (messageId == null) {
            return;
        }
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            this.lastReadMessageId = messageId;
            this.lastReadAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public LocalDateTime getLastReadAt() {
        return lastReadAt;
    }
}
