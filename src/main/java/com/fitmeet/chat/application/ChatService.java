package com.fitmeet.chat.application;

import com.fitmeet.chat.application.event.ChatMessageCreatedEvent;
import com.fitmeet.chat.domain.ChatMessage;
import com.fitmeet.chat.domain.ChatMessageRepository;
import com.fitmeet.chat.domain.ChatRoom;
import com.fitmeet.chat.domain.ChatRoomReadState;
import com.fitmeet.chat.domain.ChatRoomReadStateRepository;
import com.fitmeet.chat.domain.ChatRoomRepository;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.meetup.domain.MeetupMemberRepository;
import com.fitmeet.meetup.domain.MeetupMemberStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomReadStateRepository readStateRepository;
    private final MeetupMemberRepository meetupMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatRecentMessageCache recentMessageCache;

    public ChatService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ChatRoomReadStateRepository readStateRepository,
            MeetupMemberRepository meetupMemberRepository,
            ApplicationEventPublisher eventPublisher,
            ChatRecentMessageCache recentMessageCache
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.readStateRepository = readStateRepository;
        this.meetupMemberRepository = meetupMemberRepository;
        this.eventPublisher = eventPublisher;
        this.recentMessageCache = recentMessageCache;
    }

    @Transactional
    public SendChatMessageResult sendMessage(Long roomId, Long senderMemberId, String content) {
        ChatRoom room = getAccessibleRoom(roomId, senderMemberId);

        ChatMessage message = chatMessageRepository.save(ChatMessage.text(roomId, senderMemberId, content));
        room.recordLastMessage(message.getId(), message.getContent(), message.getCreatedAt());

        SendChatMessageResult result = new SendChatMessageResult(
                message.getId(),
                message.getRoomId(),
                message.getSenderMemberId(),
                message.getContent(),
                message.getCreatedAt()
        );
        appendRecentMessageAfterCommit(new ChatMessageResult(
                result.messageId(),
                result.roomId(),
                result.senderMemberId(),
                result.content(),
                result.createdAt()
        ));

        eventPublisher.publishEvent(new ChatMessageCreatedEvent(
                room.getId(),
                room.getMeetupId(),
                message.getId(),
                message.getSenderMemberId(),
                message.getContent(),
                message.getCreatedAt()
        ));

        return result;
    }

    private void appendRecentMessageAfterCommit(ChatMessageResult message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recentMessageCache.appendRecentMessage(message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recentMessageCache.appendRecentMessage(message);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResult> getMessages(Long roomId, Long memberId, Long cursorId) {
        getAccessibleRoom(roomId, memberId);

        if (cursorId == null) {
            Optional<List<ChatMessageResult>> cachedMessages = recentMessageCache.findRecentMessages(roomId);
            if (cachedMessages.isPresent()) {
                return cachedMessages.get();
            }
        }

        List<ChatMessage> messages = cursorId == null
                ? chatMessageRepository.findTop30ByRoomIdOrderByIdDesc(roomId)
                : chatMessageRepository.findTop30ByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursorId);

        List<ChatMessageResult> results = messages.stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .map(ChatMessageResult::from)
                .toList();
        if (cursorId == null) {
            recentMessageCache.cacheRecentMessages(roomId, results);
        }
        return results;
    }

    @Transactional
    public ReadChatRoomResult markAsRead(Long roomId, Long memberId) {
        ChatRoom room = getAccessibleRoom(roomId, memberId);

        ChatRoomReadState readState = readStateRepository.findByRoomIdAndMemberId(roomId, memberId)
                .orElseGet(() -> ChatRoomReadState.create(roomId, memberId, null));
        readState.markAsRead(room.getLastMessageId());
        ChatRoomReadState savedReadState = readStateRepository.save(readState);

        return new ReadChatRoomResult(
                savedReadState.getRoomId(),
                savedReadState.getMemberId(),
                savedReadState.getLastReadMessageId(),
                savedReadState.getLastReadAt()
        );
    }

    private ChatRoom getAccessibleRoom(Long roomId, Long memberId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BaseException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        boolean joined = meetupMemberRepository.existsByMeetupIdAndMemberIdAndStatus(
                room.getMeetupId(),
                memberId,
                MeetupMemberStatus.JOINED
        );
        if (!joined) {
            throw new BaseException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
        return room;
    }
}
