package com.fitmeet.meetup.domain;

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
        name = "meetup_member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_meetup_member_meetup_id_member_id", columnNames = {"meetup_id", "member_id"})
        },
        indexes = {
                @Index(name = "idx_meetup_member_member_status", columnList = "member_id,status"),
                @Index(name = "idx_meetup_member_meetup_status", columnList = "meetup_id,status")
        }
)
public class MeetupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetupMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetupMemberStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MeetupMember() {
    }

    private MeetupMember(Long meetupId, Long memberId, MeetupMemberRole role) {
        LocalDateTime now = LocalDateTime.now();
        this.meetupId = meetupId;
        this.memberId = memberId;
        this.role = role;
        this.status = MeetupMemberStatus.JOINED;
        this.joinedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static MeetupMember host(Long meetupId, Long memberId) {
        return new MeetupMember(meetupId, memberId, MeetupMemberRole.HOST);
    }

    public static MeetupMember member(Long meetupId, Long memberId) {
        return new MeetupMember(meetupId, memberId, MeetupMemberRole.MEMBER);
    }

    public Long getId() {
        return id;
    }

    public Long getMeetupId() {
        return meetupId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public MeetupMemberRole getRole() {
        return role;
    }

    public MeetupMemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
