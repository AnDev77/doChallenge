package com.fitmeet.financechat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finance_chat_message", indexes = @Index(name = "idx_finance_chat_symbol_id", columnList = "symbol,id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_finance_chat_request", columnNames = {"symbol", "author_id", "request_id"}))
public class FinanceMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 4)
    private String symbol;
    @Column(name = "author_id", nullable = false)
    private Long authorId;
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(nullable = false)
    private Instant createdAt;

    protected FinanceMessage() {}
    public FinanceMessage(String symbol, Long authorId, UUID requestId, String content) {
        this.symbol = symbol;
        this.authorId = authorId;
        this.requestId = requestId.toString();
        this.content = content;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public Long getAuthorId() { return authorId; }
    public String getRequestId() { return requestId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
