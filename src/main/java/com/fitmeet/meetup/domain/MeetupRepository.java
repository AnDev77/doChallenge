package com.fitmeet.meetup.domain;

import java.util.Optional;

public interface MeetupRepository {

    Meetup save(Meetup meetup);

    Optional<Meetup> findById(Long id);
}
