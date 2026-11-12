package com.fitmeet.watchlist;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {
    private final WatchlistRepository repository;

    public WatchlistService(WatchlistRepository repository) { this.repository = repository; }

    private String supported(String symbol) {
        if (!"QQQ".equals(symbol) && !"SPY".equals(symbol)) {
            throw new BaseException(ErrorCode.WATCHLIST_SYMBOL_NOT_SUPPORTED);
        }
        return symbol;
    }

    @Transactional(readOnly = true)
    public List<String> list(Long memberId) {
        return repository.findByMemberIdOrderBySymbolAsc(memberId).stream()
                .map(WatchlistEntry::getSymbol).toList();
    }

    @Transactional
    public List<String> add(Long memberId, String symbol) {
        String checked = supported(symbol);
        if (repository.findByMemberIdAndSymbol(memberId, checked).isEmpty()) {
            repository.saveAndFlush(new WatchlistEntry(memberId, checked));
        }
        return list(memberId);
    }

    @Transactional
    public List<String> remove(Long memberId, String symbol) {
        String checked = supported(symbol);
        repository.findByMemberIdAndSymbol(memberId, checked).ifPresent(repository::delete);
        return list(memberId);
    }
}
