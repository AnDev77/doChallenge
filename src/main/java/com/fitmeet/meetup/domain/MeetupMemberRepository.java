package com.fitmeet.meetup.domain;

import java.util.List;

public interface MeetupMemberRepository {

    MeetupMember save(MeetupMember meetupMember);

    boolean existsByMeetupIdAndMemberId(Long meetupId, Long memberId);

    boolean existsByMeetupIdAndMemberIdAndStatus(Long meetupId, Long memberId, MeetupMemberStatus status);

    int countByMeetupIdAndStatus(Long meetupId, MeetupMemberStatus status);

    List<Long> findMemberIdsByMeetupIdAndStatus(Long meetupId, MeetupMemberStatus status);
}
