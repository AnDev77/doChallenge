package com.fitmeet.financechat;

import java.time.Instant;

public record FinanceMessageView(Long id, String symbol, Long authorId, String content, Instant createdAt) {
    public static FinanceMessageView from(FinanceMessage message) {
        return new FinanceMessageView(message.getId(), message.getSymbol(), message.getAuthorId(),
                message.getContent(), message.getCreatedAt());
    }
}
