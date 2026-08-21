package com.fitmeet.meetup.presentation.response;

import com.fitmeet.meetup.application.CreateMeetupResult;

public record CreateMeetupResponse(
        Long meetupId,
        Long chatRoomId
) {

    public static CreateMeetupResponse from(CreateMeetupResult result) {
        return new CreateMeetupResponse(result.meetupId(), result.chatRoomId());
    }
}
