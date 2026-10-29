# 이메일 인증과 회원 로그인

2026-10-30: 현재 가입은 인증 확인 뒤 발급한 10분 일회용 증표를 요구한다. `docs/api/auth-member-api.md`와 `docs/reviews/email-rate-limit-and-web-contract-review.md`가 최신 계약이다. 아래 초기 구조 설명은 이력이며 금융 종목방 완료를 뜻하지 않는다.

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
