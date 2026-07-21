# Day 1 Notes

## Goal

Day 1 focuses on local infrastructure and the first auth/member slice.

## Implemented

```text
common response and exception shape
member domain skeleton
auth email verification
Redis TTL storage
SMTP sender adapter
Docker Compose for MySQL, Redis, and Mailpit
```

## Email Verification Decision

The service verifies email before creating a member.

```text
send code
confirm code
create ACTIVE member
```

This avoids storing unverified member rows in MySQL.

## DDD Layering

```text
presentation
- HTTP request and response DTOs
- input validation
- application service calls

application
- use case orchestration
- signup and email verification flow

domain
- Member status
- EmailVerificationCode value rules
- repository and sender ports

infrastructure
- JPA repository implementation
- Redis verification code storage
- SMTP email delivery
```

## Next Scope

```text
meetup creation
meetup join
chat room creation when meetup is created
```
