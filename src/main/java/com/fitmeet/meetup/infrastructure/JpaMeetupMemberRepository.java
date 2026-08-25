package com.fitmeet.meetup.infrastructure;

import com.fitmeet.meetup.domain.MeetupMember;
import com.fitmeet.meetup.domain.MeetupMemberRepository;
import com.fitmeet.meetup.domain.MeetupMemberStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface JpaMeetupMemberRepository extends JpaRepository<MeetupMember, Long>, MeetupMemberRepository {

    @Override
    @Query("""
            select meetupMember.memberId
            from MeetupMember meetupMember
            where meetupMember.meetupId = :meetupId
              and meetupMember.status = :status
            """)
    List<Long> findMemberIdsByMeetupIdAndStatus(
            @Param("meetupId") Long meetupId,
            @Param("status") MeetupMemberStatus status
    );
}
