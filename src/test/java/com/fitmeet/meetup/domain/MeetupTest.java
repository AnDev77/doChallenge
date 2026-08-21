package com.fitmeet.meetup.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MeetupTest {

    @Test
    void createdMeetupStartsRecruiting() {
        Meetup meetup = createMeetup(20);

        assertThat(meetup.getHostMemberId()).isEqualTo(1L);
        assertThat(meetup.getStatus()).isEqualTo(MeetupStatus.RECRUITING);
        assertThat(meetup.getCreatedAt()).isNotNull();
        assertThat(meetup.getUpdatedAt()).isNotNull();
    }

    @Test
    void meetupCapacityMustBeAtLeastTwo() {
        assertThatThrownBy(() -> createMeetup(1))
                .isInstanceOf(BaseException.class)
                .extracting(exception -> ((BaseException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MEETUP_CAPACITY);
    }

    @Test
    void meetupLatitudeMustBeValid() {
        assertThatThrownBy(() -> Meetup.create(
                1L,
                "Invalid location meetup",
                "Latitude validation",
                "SEOUL_GANGNAM",
                "Gangnam-daero, Gangnam-gu, Seoul",
                BigDecimal.valueOf(91),
                BigDecimal.valueOf(127.0276),
                20
        ))
                .isInstanceOf(BaseException.class)
                .extracting(exception -> ((BaseException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MEETUP_LOCATION);
    }

    @Test
    void recruitingMeetupCanBeJoinedUntilCapacity() {
        Meetup meetup = createMeetup(2);

        meetup.validateJoinable(1);
    }

    @Test
    void meetupCannotBeJoinedWhenCapacityIsFull() {
        Meetup meetup = createMeetup(2);

        assertThatThrownBy(() -> meetup.validateJoinable(2))
                .isInstanceOf(BaseException.class)
                .extracting(exception -> ((BaseException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEETUP_CAPACITY_EXCEEDED);
    }

    private Meetup createMeetup(int maxMemberCount) {
        return Meetup.create(
                1L,
                "Mon Wed Fri 3km running",
                "Running meetup near Gangnam station.",
                "SEOUL_GANGNAM",
                "Gangnam-daero, Gangnam-gu, Seoul",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276),
                maxMemberCount
        );
    }
}
