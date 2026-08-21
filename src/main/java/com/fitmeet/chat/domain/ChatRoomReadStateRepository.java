package com.fitmeet.chat.domain;

import java.util.Optional;

public interface ChatRoomReadStateRepository {

    ChatRoomReadState save(ChatRoomReadState readState);

    Optional<ChatRoomReadState> findByRoomIdAndMemberId(Long roomId, Long memberId);
}
