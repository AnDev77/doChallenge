package com.fitmeet.meetup.presentation;

import com.fitmeet.common.response.ApiResponse;
import com.fitmeet.meetup.application.CreateMeetupResult;
import com.fitmeet.meetup.application.MeetupService;
import com.fitmeet.meetup.presentation.request.CreateMeetupRequest;
import com.fitmeet.meetup.presentation.response.CreateMeetupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetups")
public class MeetupController {

    private final MeetupService meetupService;

    public MeetupController(MeetupService meetupService) {
        this.meetupService = meetupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateMeetupResponse> create(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CreateMeetupRequest request
    ) {
        CreateMeetupResult result = meetupService.create(
                memberId,
                request.title(),
                request.description(),
                request.region(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.maxMemberCount()
        );
        return ApiResponse.success(CreateMeetupResponse.from(result));
    }

    @PostMapping("/{meetupId}/join")
    public ApiResponse<Void> join(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long meetupId
    ) {
        meetupService.join(meetupId, memberId);
        return ApiResponse.success(null);
    }
}
