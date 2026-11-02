package com.fitmeet.financechat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class FinanceMessageRepositoryTest {
    @Autowired FinanceMessageRepository repository;

    @Test void perTickerHistoryAndCatchUpUsePersistentIds() {
        repository.saveAndFlush(new FinanceMessage("QQQ", 1L, UUID.randomUUID(), "first"));
        var next = repository.saveAndFlush(new FinanceMessage("QQQ", 2L, UUID.randomUUID(), "second"));
        repository.saveAndFlush(new FinanceMessage("SPY", 1L, UUID.randomUUID(), "separate"));
        assertThat(repository.findBySymbolAndIdGreaterThanOrderByIdAsc("QQQ", next.getId() - 1,
                PageRequest.of(0, 30))).extracting(FinanceMessage::getContent).containsExactly("second");
        assertThat(repository.findBySymbolOrderByIdDesc("SPY", PageRequest.of(0, 30)))
                .extracting(FinanceMessage::getContent).containsExactly("separate");
    }

    @Test void concurrentDuplicateCannotProduceSecondStoredRow() {
        UUID request = UUID.randomUUID();
        repository.saveAndFlush(new FinanceMessage("QQQ", 1L, request, "first"));
        assertThatThrownBy(() -> repository.saveAndFlush(new FinanceMessage("QQQ", 1L, request, "duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
