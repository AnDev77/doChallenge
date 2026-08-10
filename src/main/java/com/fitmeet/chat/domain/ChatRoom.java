package com.fitmeet.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_room",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_room_meetup_id_type", columnNames = {"meetup_id", "type"})
        },
        indexes = {
                @Index(name = "idx_chat_room_meetup_id", columnList = "meetup_id")
        }
)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatRoomType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatRoomStatus status;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_content", length = 500)
    private String lastMessageContent;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ChatRoom() {
    }

    private ChatRoom(Long meetupId, ChatRoomType type) {
        LocalDateTime now = LocalDateTime.now();
        this.meetupId = meetupId;
        this.type = type;
        this.status = ChatRoomStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ChatRoom createMeetupRoom(Long meetupId) {
        return new ChatRoom(meetupId, ChatRoomType.MEETUP);
    }

    public Long getId() {
        return id;
    }

    public Long getMeetupId() {
        return meetupId;
    }

    public ChatRoomType getType() {
        return type;
    }

    public ChatRoomStatus getStatus() {
        return status;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
