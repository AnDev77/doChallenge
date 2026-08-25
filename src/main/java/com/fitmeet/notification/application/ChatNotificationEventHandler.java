package com.fitmeet.notification.application;

import com.fitmeet.chat.application.event.ChatMessageCreatedEvent;
import com.fitmeet.meetup.domain.MeetupMemberRepository;
import com.fitmeet.meetup.domain.MeetupMemberStatus;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatNotificationEventHandler {

    private final MeetupMemberRepository meetupMemberRepository;
    private final NotificationSseService notificationSseService;

    public ChatNotificationEventHandler(
            MeetupMemberRepository meetupMemberRepository,
            NotificationSseService notificationSseService
    ) {
        this.meetupMemberRepository = meetupMemberRepository;
        this.notificationSseService = notificationSseService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageCreatedEvent event) {
        List<Long> receiverIds = meetupMemberRepository.findMemberIdsByMeetupIdAndStatus(
                event.meetupId(),
                MeetupMemberStatus.JOINED
        );

        for (Long receiverId : receiverIds) {
            if (receiverId.equals(event.senderMemberId())) {
                continue;
            }
            notificationSseService.send(receiverId, NotificationEvent.chatMessageCreated(
                    receiverId,
                    event.roomId(),
                    event.messageId(),
                    event.senderMemberId(),
                    event.content(),
                    event.createdAt()
            ));
        }
    }
}
