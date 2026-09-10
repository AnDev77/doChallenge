package com.fitmeet.chat.application;

import java.util.List;
import java.util.Optional;

public interface ChatRecentMessageCache {

    Optional<List<ChatMessageResult>> findRecentMessages(Long roomId);

    void cacheRecentMessages(Long roomId, List<ChatMessageResult> messages);

    void appendRecentMessage(ChatMessageResult message);
}
