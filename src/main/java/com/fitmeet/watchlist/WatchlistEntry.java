package com.fitmeet.watchlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "member_watchlist", uniqueConstraints = @UniqueConstraint(
        name = "uk_member_watchlist_member_symbol", columnNames = {"member_id", "symbol"}))
public class WatchlistEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(nullable = false, length = 4)
    private String symbol;
    @Column(nullable = false)
    private Instant createdAt;

    protected WatchlistEntry() {}
    public WatchlistEntry(Long memberId, String symbol) {
        this.memberId = memberId;
        this.symbol = symbol;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getSymbol() { return symbol; }
    public Instant getCreatedAt() { return createdAt; }
}
