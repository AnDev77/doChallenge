package com.fitmeet.chat.infrastructure;

import com.fitmeet.chat.domain.ChatMessage;
import com.fitmeet.chat.domain.ChatMessageRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChatMessageRepository extends JpaRepository<ChatMessage, Long>, ChatMessageRepository {

    @Override
    List<ChatMessage> findTop30ByRoomIdOrderByIdDesc(Long roomId);

    @Override
    List<ChatMessage> findTop30ByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id);
}
