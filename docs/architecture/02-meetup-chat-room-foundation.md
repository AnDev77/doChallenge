# 02 - Meetup and Chat Room Foundation

## Scope

This work unit creates the base structure for meetup-driven chat.

```text
POST /api/v1/meetups
  -> create meetup
  -> create HOST meetup member
  -> create meetup chat room

POST /api/v1/meetups/{meetupId}/join
  -> validate meetup
  -> validate active member
  -> prevent duplicate join
  -> validate capacity
  -> create MEMBER meetup member
```

Chat messages, WebSocket, read cursor, Redis unread count, and SSE notifications are intentionally deferred.

## Design Decision

Do Challenge uses meetups as the entry point for chat. A chat room is created in the same transaction as the meetup so that the system never has a meetup without a default room.

```text
MeetupService.create()
  1. Validate host member
  2. Save Meetup
  3. Save MeetupMember as HOST
  4. Save ChatRoom as MEETUP room
  5. Return meetupId and chatRoomId
```

Meetup joining is handled separately because joining is the future basis for chat permission checks.

```text
MeetupService.join()
  1. Validate active member
  2. Load meetup
  3. Reject duplicate membership
  4. Count JOINED members
  5. Validate recruiting status and capacity
  6. Save MeetupMember as MEMBER
```

## Domain Boundaries

The meetup domain does not reference the `Member` entity directly. It stores `hostMemberId` and `memberId` values instead.

The chat domain also stores `meetupId` instead of referencing the `Meetup` entity.

```text
meetup.hostMemberId -> member.id
meetup_member.memberId -> member.id
chat_room.meetupId -> meetup.id
```

This keeps domains loosely coupled and makes later modularization easier.

## Authentication Context

The meetup APIs receive the member id from the authenticated JWT principal.

```http
Authorization: Bearer {accessToken}
```

The controller does not trust a client-supplied member id header. `JwtAuthenticationFilter` validates the token and stores `AuthenticatedMember` in the Spring Security context. The controller then reads it through `@AuthenticationPrincipal`.

## APIs

```http
POST /api/v1/meetups
```

Request:

```json
{
  "title": "Mon Wed Fri 3km running",
  "description": "Running meetup near Gangnam station.",
  "region": "SEOUL_GANGNAM",
  "address": "Gangnam-daero, Gangnam-gu, Seoul",
  "latitude": 37.4979,
  "longitude": 127.0276,
  "maxMemberCount": 20
}
```

Response:

```json
{
  "success": true,
  "data": {
    "meetupId": 1,
    "chatRoomId": 1
  },
  "error": null
}
```

```http
POST /api/v1/meetups/{meetupId}/join
```

Response:

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

## Next Work Unit

```text
1. Chat message entity
2. Chat room read state
3. WebSocket message send/subscribe
4. Redis unread count
```
