# Do Challenge Backend

Spring Boot backend for a local exercise meetup and challenge service.

## Day 1 Scope

- Gradle Spring Boot project setup
- Docker Compose local environment
- Unified API response shape
- Global exception handling
- Member domain skeleton
- Redis-backed email verification code
- SMTP email delivery through Mailpit for local development
- Signup only after email verification

## Signup Flow

```text
1. POST /api/v1/auth/email-verifications/send
   - Generate a 6-digit code
   - Store the code in Redis for 5 minutes
   - Send the code by SMTP

2. POST /api/v1/auth/email-verifications/confirm
   - Compare the user input with the Redis code
   - Delete the code on success
   - Store a verified marker in Redis for 30 minutes

3. POST /api/v1/auth/signup
   - Check the verified marker in Redis
   - Create an ACTIVE member
   - Delete the verified marker
```

## Package Structure

```text
com.fitmeet
  ├── auth
  │   ├── application
  │   ├── domain
  │   ├── infrastructure
  │   └── presentation
  ├── member
  │   ├── application
  │   ├── domain
  │   ├── infrastructure
  │   └── presentation
  └── common
      ├── config
      ├── exception
      └── response
```

## Day 1 APIs

```text
POST /api/v1/auth/email-verifications/send
POST /api/v1/auth/email-verifications/confirm
POST /api/v1/auth/signup
GET  /api/v1/members/{memberId}
```

## Redis Keys

```text
auth:email-verification:code:{email}
TTL: 5 minutes

auth:email-verification:verified:{email}
TTL: 30 minutes
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
