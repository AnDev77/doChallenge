package com.fitmeet.financechat;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FinanceChatService {
    private final FinanceMessageRepository repository;
    private final SimpMessagingTemplate broker;

    public FinanceChatService(FinanceMessageRepository repository, SimpMessagingTemplate broker) {
        this.repository = repository;
        this.broker = broker;
    }

    static String symbol(String value) {
        if (!"QQQ".equals(value) && !"SPY".equals(value)) throw new BaseException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        return value;
    }

    @Transactional(readOnly = true)
    public List<FinanceMessageView> messages(String ticker, Long afterId) {
        String symbol = symbol(ticker);
        if (afterId != null && afterId < 0) throw new BaseException(ErrorCode.INVALID_INPUT);
        List<FinanceMessage> found;
        if (afterId == null) {
            found = new ArrayList<>(repository.findBySymbolOrderByIdDesc(symbol, PageRequest.of(0, 30)));
            Collections.reverse(found);
        } else {
            found = repository.findBySymbolAndIdGreaterThanOrderByIdAsc(symbol, afterId, PageRequest.of(0, 30));
        }
        return found.stream().map(FinanceMessageView::from).toList();
    }

    @Transactional
    public FinanceMessageView post(String ticker, Long authorId, UUID requestId, String rawContent) {
        String symbol = symbol(ticker);
        if (authorId == null || requestId == null || rawContent == null || rawContent.isBlank()
                || rawContent.length() > 1000 || rawContent.trim().isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
        String content = rawContent.trim();
        var previous = repository.findBySymbolAndAuthorIdAndRequestId(symbol, authorId, requestId.toString());
        if (previous.isPresent()) {
            if (!previous.get().getContent().equals(content)) throw new BaseException(ErrorCode.CHAT_REQUEST_CONFLICT);
            return FinanceMessageView.from(previous.get());
        }
        // Database unique key is the final guard against concurrent duplicate requests.
        FinanceMessageView saved = FinanceMessageView.from(
                repository.saveAndFlush(new FinanceMessage(symbol, authorId, requestId, content)));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Transactional delivery requires synchronization");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                broker.convertAndSend("/topic/finance/rooms/" + symbol, saved);
            }
        });
        return saved;
    }
}
