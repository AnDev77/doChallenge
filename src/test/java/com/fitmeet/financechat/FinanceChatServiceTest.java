package com.fitmeet.financechat;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.common.exception.RateLimitedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceChatServiceTest {
    private final FinanceMessageRepository repo = mock(FinanceMessageRepository.class);
    private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    private final FinancePostLimiter limiter = mock(FinancePostLimiter.class);
    private final FinanceChatService service = new FinanceChatService(repo, broker, limiter);

    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void onlyTwoFixedRoomsAndPositiveCursor() {
        assertThatThrownBy(() -> service.messages("TLT", null)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.messages("QQQ", -1L)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.post("qqq", 1L, UUID.randomUUID(), "hello"))
                .isInstanceOf(BaseException.class);
        verifyNoInteractions(repo, broker);
    }

    @Test void messagesUseAscendingCatchUpCursorAndLatestWindow() {
        when(repo.findBySymbolOrderByIdDesc(eq("QQQ"), any())).thenReturn(List.of());
        assertThat(service.messages("QQQ", null)).isEmpty();
        verify(repo).findBySymbolOrderByIdDesc("QQQ", PageRequest.of(0, 30));
        when(repo.findBySymbolAndIdGreaterThanOrderByIdAsc(eq("QQQ"), eq(5L), any())).thenReturn(List.of());
        assertThat(service.messages("QQQ", 5L)).isEmpty();
        verify(repo).findBySymbolAndIdGreaterThanOrderByIdAsc("QQQ", 5L, PageRequest.of(0, 30));
    }

    @Test void broadcastOnlyAfterCommitAndNoBroadcastOnRollback() {
        UUID request = UUID.randomUUID();
        when(repo.findBySymbolAndAuthorIdAndRequestId("SPY", 9L, request.toString())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        service.post("SPY", 9L, request, "hello");
        verifyNoInteractions(broker);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
        verifyNoInteractions(broker);
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        service.post("SPY", 9L, UUID.randomUUID(), "hello");
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(broker).convertAndSend(eq("/topic/finance/rooms/SPY"), any(FinanceMessageView.class));
    }

    @Test void duplicateRequestDoesNotSaveOrBroadcastAgainAndChangedTextConflicts() {
        UUID request = UUID.randomUUID();
        var prior = new FinanceMessage("QQQ", 7L, request, "original");
        when(repo.findBySymbolAndAuthorIdAndRequestId("QQQ", 7L, request.toString())).thenReturn(Optional.of(prior));
        assertThat(service.post("QQQ", 7L, request, "original").content()).isEqualTo("original");
        assertThatThrownBy(() -> service.post("QQQ", 7L, request, "different")).isInstanceOf(BaseException.class);
        verify(repo, never()).saveAndFlush(any());
        verifyNoInteractions(broker);
        verifyNoInteractions(limiter);
    }

    @Test void invalidBodyOrDbFailureNeverSchedulesBroadcast() {
        assertThatThrownBy(() -> service.post("QQQ", 1L, UUID.randomUUID(), " "))
                .isInstanceOf(BaseException.class);
        when(repo.saveAndFlush(any())).thenThrow(new IllegalStateException("db unavailable"));
        TransactionSynchronizationManager.initSynchronization();
        assertThatThrownBy(() -> service.post("QQQ", 1L, UUID.randomUUID(), "message"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(broker);
    }

    @Test void newPostIsLimitedBeforeDatabaseWriteButRetriesDoNotSpendQuota() {
        UUID request = UUID.randomUUID();
        when(repo.findBySymbolAndAuthorIdAndRequestId("QQQ", 7L, request.toString())).thenReturn(Optional.empty());
        doThrow(new RateLimitedException(ErrorCode.CHAT_RATE_LIMITED, 20)).when(limiter).check(7L);
        assertThatThrownBy(() -> service.post("QQQ", 7L, request, "hello"))
                .isInstanceOf(RateLimitedException.class);
        verify(repo, never()).saveAndFlush(any());
        verifyNoInteractions(broker);

        var prior = new FinanceMessage("QQQ", 7L, request, "hello");
        when(repo.findBySymbolAndAuthorIdAndRequestId("QQQ", 7L, request.toString()))
                .thenReturn(Optional.of(prior));
        assertThat(service.post("QQQ", 7L, request, "hello").content()).isEqualTo("hello");
        verify(limiter, times(1)).check(7L);
    }
}
