package com.fitmeet.chat.infrastructure;

import com.fitmeet.chat.domain.ChatRoomReadState;
import com.fitmeet.chat.domain.ChatRoomReadStateRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChatRoomReadStateRepository
        extends JpaRepository<ChatRoomReadState, Long>, ChatRoomReadStateRepository {

    @Override
    Optional<ChatRoomReadState> findByRoomIdAndMemberId(Long roomId, Long memberId);
}
