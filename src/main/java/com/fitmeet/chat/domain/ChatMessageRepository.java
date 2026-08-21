package com.fitmeet.chat.domain;

import java.util.List;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage chatMessage);

    List<ChatMessage> findTop30ByRoomIdOrderByIdDesc(Long roomId);

    List<ChatMessage> findTop30ByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id);
}
