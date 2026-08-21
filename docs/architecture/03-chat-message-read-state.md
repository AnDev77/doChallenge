# 03 - Chat Message and Read State

## Scope

This work unit creates the HTTP-based chat message and read cursor foundation before adding WebSocket.

```text
POST /api/v1/chat/rooms/{roomId}/messages
GET  /api/v1/chat/rooms/{roomId}/messages?cursorId=
PATCH /api/v1/chat/rooms/{roomId}/read
```

WebSocket, Redis unread count, SSE notifications, and message fan-out are intentionally deferred.

## Why HTTP Before WebSocket

WebSocket should be treated as a real-time transport, not as the first place where business rules are implemented.

The message send and read use cases are first exposed through HTTP so they can be tested and debugged without socket connection concerns.

```text
HTTP Controller
  -> ChatService.sendMessage()
  -> ChatService.markAsRead()

Future WebSocket Handler
  -> ChatService.sendMessage()
  -> ChatService.markAsRead()
  -> broadcast result
```

The WebSocket layer should not call the HTTP API internally. Both HTTP and WebSocket entry points should call the same application service.

## Message Send Flow

```text
1. Load chat room
2. Check whether member joined the meetup linked to the room
3. Save ChatMessage
4. Update ChatRoom last message summary
5. Return saved message
```

`ChatRoom.lastMessageId`, `lastMessageContent`, and `lastMessageAt` are stored to make future chat room list queries cheaper.

## Message Query Flow

Messages use cursor pagination based on `chat_message.id`.

```sql
SELECT *
FROM chat_message
WHERE room_id = ?
  AND id < ?
ORDER BY id DESC
LIMIT 30;
```

The API returns messages in ascending order after fetching the newest page in descending order.

## Read Cursor Flow

Read state is stored per room and member.

```text
chat_room_read_state
  - roomId
  - memberId
  - lastReadMessageId
  - lastReadAt
```

When a member marks a room as read:

```text
1. Load accessible room
2. Read room.lastMessageId
3. Create or load ChatRoomReadState
4. Move lastReadMessageId forward only
5. Save read state
```

The read cursor never moves backward.

## WebSocket Read Handling

When WebSocket is added, read handling should not call the HTTP endpoint.

Recommended structure:

```text
SEND /app/chat/rooms/{roomId}/read
  -> ChatWebSocketController
  -> ChatService.markAsRead(roomId, memberId)
  -> broadcast ROOM_READ event to /topic/chat/rooms/{roomId}/reads
```

The same `ChatService.markAsRead()` method is used by:

```text
PATCH /api/v1/chat/rooms/{roomId}/read
SEND  /app/chat/rooms/{roomId}/read
```

This keeps the read rule consistent across HTTP and WebSocket.

## Next Work Unit

```text
1. Add spring-boot-starter-websocket
2. Configure STOMP endpoint /ws
3. Add WebSocket message send endpoint
4. Reuse ChatService.sendMessage()
5. Broadcast MESSAGE_CREATED
6. Add WebSocket read endpoint
7. Reuse ChatService.markAsRead()
8. Broadcast ROOM_READ
```
