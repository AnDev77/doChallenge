package com.fitmeet.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitmeet.chat.domain.ChatMessage;
import com.fitmeet.chat.domain.ChatMessageRepository;
import com.fitmeet.chat.domain.ChatRoom;
import com.fitmeet.chat.domain.ChatRoomReadStateRepository;
import com.fitmeet.chat.domain.ChatRoomRepository;
import com.fitmeet.meetup.domain.MeetupMemberRepository;
import com.fitmeet.meetup.domain.MeetupMemberStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long MEETUP_ID = 10L;
    private static final Long MEMBER_ID = 100L;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomReadStateRepository readStateRepository;

    @Mock
    private MeetupMemberRepository meetupMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ChatRecentMessageCache recentMessageCache;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                readStateRepository,
                meetupMemberRepository,
                eventPublisher,
                recentMessageCache
        );
    }

    @Test
    void getMessagesReturnsCacheWhenRecentMessageCacheHits() {
        ChatMessageResult cachedMessage = new ChatMessageResult(
                1L,
                ROOM_ID,
                MEMBER_ID,
                "cached message",
                LocalDateTime.now()
        );
        givenAccessibleRoom();
        when(recentMessageCache.findRecentMessages(ROOM_ID)).thenReturn(Optional.of(List.of(cachedMessage)));

        List<ChatMessageResult> results = chatService.getMessages(ROOM_ID, MEMBER_ID, null);

        assertThat(results).containsExactly(cachedMessage);
        verify(chatMessageRepository, never()).findTop30ByRoomIdOrderByIdDesc(ROOM_ID);
        verify(recentMessageCache, never()).cacheRecentMessages(eq(ROOM_ID), any());
    }

    @Test
    void getMessagesLoadsFromDatabaseAndCachesWhenRecentMessageCacheMisses() {
        ChatMessage firstMessage = message(1L, "first");
        ChatMessage secondMessage = message(2L, "second");
        givenAccessibleRoom();
        when(recentMessageCache.findRecentMessages(ROOM_ID)).thenReturn(Optional.empty());
        when(chatMessageRepository.findTop30ByRoomIdOrderByIdDesc(ROOM_ID))
                .thenReturn(List.of(secondMessage, firstMessage));

        List<ChatMessageResult> results = chatService.getMessages(ROOM_ID, MEMBER_ID, null);

        assertThat(results).extracting(ChatMessageResult::messageId).containsExactly(1L, 2L);
        verify(recentMessageCache).cacheRecentMessages(eq(ROOM_ID), eq(results));
    }

    @Test
    void sendMessageAppendsSavedMessageToRecentMessageCache() {
        ChatMessage savedMessage = message(1L, "hello");
        givenAccessibleRoom();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        SendChatMessageResult result = chatService.sendMessage(ROOM_ID, MEMBER_ID, "hello");

        assertThat(result.messageId()).isEqualTo(1L);
        verify(recentMessageCache).appendRecentMessage(new ChatMessageResult(
                result.messageId(),
                result.roomId(),
                result.senderMemberId(),
                result.content(),
                result.createdAt()
        ));
    }

    private void givenAccessibleRoom() {
        ChatRoom room = ChatRoom.createMeetupRoom(MEETUP_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(meetupMemberRepository.existsByMeetupIdAndMemberIdAndStatus(
                MEETUP_ID,
                MEMBER_ID,
                MeetupMemberStatus.JOINED
        )).thenReturn(true);
    }

    private ChatMessage message(Long messageId, String content) {
        ChatMessage message = ChatMessage.text(ROOM_ID, MEMBER_ID, content);
        ReflectionTestUtils.setField(message, "id", messageId);
        return message;
    }
}
