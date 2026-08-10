package com.fitmeet.meetup.infrastructure;

import com.fitmeet.meetup.domain.MeetupMember;
import com.fitmeet.meetup.domain.MeetupMemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMeetupMemberRepository extends JpaRepository<MeetupMember, Long>, MeetupMemberRepository {
}
