package com.fitmeet.watchlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {
    List<WatchlistEntry> findByMemberIdOrderBySymbolAsc(Long memberId);
    Optional<WatchlistEntry> findByMemberIdAndSymbol(Long memberId, String symbol);
}
