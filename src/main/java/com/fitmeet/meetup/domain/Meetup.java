package com.fitmeet.meetup.domain;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "meetup",
        indexes = {
                @Index(name = "idx_meetup_region_status_created_at", columnList = "region,status,created_at"),
                @Index(name = "idx_meetup_host_member_id", columnList = "host_member_id")
        }
)
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_member_id", nullable = false)
    private Long hostMemberId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false)
    private int maxMemberCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetupStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Meetup() {
    }

    private Meetup(
            Long hostMemberId,
            String title,
            String description,
            String region,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            int maxMemberCount
    ) {
        validateCapacity(maxMemberCount);
        validateLocation(latitude, longitude);

        LocalDateTime now = LocalDateTime.now();
        this.hostMemberId = hostMemberId;
        this.title = title;
        this.description = description;
        this.region = region;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxMemberCount = maxMemberCount;
        this.status = MeetupStatus.RECRUITING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Meetup create(
            Long hostMemberId,
            String title,
            String description,
            String region,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            int maxMemberCount
    ) {
        return new Meetup(hostMemberId, title, description, region, address, latitude, longitude, maxMemberCount);
    }

    public void validateJoinable(int currentJoinedMemberCount) {
        if (status != MeetupStatus.RECRUITING) {
            throw new BaseException(ErrorCode.MEETUP_NOT_RECRUITING);
        }
        if (currentJoinedMemberCount >= maxMemberCount) {
            throw new BaseException(ErrorCode.MEETUP_CAPACITY_EXCEEDED);
        }
    }

    private void validateCapacity(int maxMemberCount) {
        if (maxMemberCount < 2) {
            throw new BaseException(ErrorCode.INVALID_MEETUP_CAPACITY);
        }
    }

    private void validateLocation(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new BaseException(ErrorCode.INVALID_MEETUP_LOCATION);
        }
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BaseException(ErrorCode.INVALID_MEETUP_LOCATION);
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BaseException(ErrorCode.INVALID_MEETUP_LOCATION);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getHostMemberId() {
        return hostMemberId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRegion() {
        return region;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public int getMaxMemberCount() {
        return maxMemberCount;
    }

    public MeetupStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
