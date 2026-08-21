package com.fitmeet.chat.infrastructure;

import com.fitmeet.chat.domain.ChatRoom;
import com.fitmeet.chat.domain.ChatRoomRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChatRoomRepository extends JpaRepository<ChatRoom, Long>, ChatRoomRepository {
}
