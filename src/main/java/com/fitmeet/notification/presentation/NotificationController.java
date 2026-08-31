package com.fitmeet.notification.presentation;

import com.fitmeet.notification.application.NotificationSseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationSseService notificationSseService;

    public NotificationController(NotificationSseService notificationSseService) {
        this.notificationSseService = notificationSseService;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-Member-Id") Long memberId) {
        return notificationSseService.subscribe(memberId);
    }
}
