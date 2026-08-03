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
- [Day 1 Notes](docs/architecture/day-01.md)

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
