# Do Challenge Backend

Spring Boot backend for a local exercise meetup and challenge service.

## Current Scope

- Docker Compose local environment
- Unified API response shape
- Global exception handling
- Redis-backed email verification
- SMTP email delivery through Mailpit
- Signup after email verification
- Login with BCrypt password matching
- JWT access token issuing

## Docs

- [Auth and Member API](docs/api/auth-member-api.md)
- [01 Auth Email Login](docs/architecture/01-auth-email-login.md)
- [02 Meetup and Chat Room Foundation](docs/architecture/02-meetup-chat-room-foundation.md)
- [03 Chat Message and Read State](docs/architecture/03-chat-message-read-state.md)
- [04 WebSocket Chat Structure](docs/architecture/04-websocket-chat-structure.md)

## Package Structure

```text
com.fitmeet
  |-- auth
  |   |-- application
  |   |-- domain
  |   |-- infrastructure
  |   `-- presentation
  |-- member
  |   |-- application
  |   |-- domain
  |   |-- infrastructure
  |   `-- presentation
  `-- common
      |-- config
      |-- exception
      `-- response
```

## Local Docker

```bash
docker compose up -d
```

Services:

```text
MySQL   localhost:3307
Redis   localhost:6379
Mailpit http://localhost:8025
SMTP    localhost:1025
```

## Local Run

```bash
./gradlew.bat bootRun --args=--spring.profiles.active=local
```

## Test

```bash
./gradlew.bat test
```
