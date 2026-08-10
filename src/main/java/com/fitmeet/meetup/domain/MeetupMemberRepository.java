package com.fitmeet.meetup.domain;

public interface MeetupMemberRepository {

    MeetupMember save(MeetupMember meetupMember);

    boolean existsByMeetupIdAndMemberId(Long meetupId, Long memberId);

    int countByMeetupIdAndStatus(Long meetupId, MeetupMemberStatus status);
}
