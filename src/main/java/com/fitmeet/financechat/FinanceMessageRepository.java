package com.fitmeet.financechat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceMessageRepository extends JpaRepository<FinanceMessage, Long> {
    Optional<FinanceMessage> findBySymbolAndAuthorIdAndRequestId(String symbol, Long authorId, String requestId);
    List<FinanceMessage> findBySymbolOrderByIdDesc(String symbol, Pageable page);
    List<FinanceMessage> findBySymbolAndIdGreaterThanOrderByIdAsc(String symbol, Long id, Pageable page);
}
