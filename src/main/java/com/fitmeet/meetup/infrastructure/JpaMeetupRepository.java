package com.fitmeet.meetup.infrastructure;

import com.fitmeet.meetup.domain.Meetup;
import com.fitmeet.meetup.domain.MeetupRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMeetupRepository extends JpaRepository<Meetup, Long>, MeetupRepository {
}
