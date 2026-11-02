# 인증·회원 API

2026-10-30 갱신: 가입에 일회용 `verificationToken`이 필요하다. 운영 메일 수신·배포 연동은 별도 인수 조건이다.

Base URL:

```text
http://localhost:8080
```

All responses use the same envelope.

Success:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

Error:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_005",
    "message": "Invalid email or password."
  }
}
```

## Signup Flow

```text
1. Send email verification code.
2. Confirm email verification code.
3. Signup with the verified email and the returned verificationToken.
4. Login with email and password.
```

The service does not create a `member` row before email verification.

## Send Email Verification Code

```text
POST /api/v1/auth/email-verifications/send
```

Request:

```json
{
  "email": "test@example.com"
}
```

Response:

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

Behavior:

```text
1. Check that the email is not already registered.
2. Generate a 6-digit code.
3. Store the code in Redis for 5 minutes.
4. Send the code through SMTP.
```

Redis key:

```text
auth:email-verification:v2:{sha256(email)}:code
```

Local email inbox:

```text
http://localhost:8025
```

## Confirm Email Verification Code

```text
POST /api/v1/auth/email-verifications/confirm
```

Request:

```json
{
  "email": "test@example.com",
  "code": "123456"
}
```

Response:

```json
{
  "success": true,
  "data": { "verificationToken": "<가입용 일회용 증표>", "expiresInSeconds": 600 },
  "error": null
}
```

Behavior:

```text
1. Check that the email is not already registered.
2. Load the verification code from Redis.
3. Compare the request code with the Redis code.
4. Delete the code on success.
5. Store the proof hash in Redis for 10 minutes and return the raw proof once.
6. Reject the code after 5 incorrect attempts.
```

Redis key:

```text
auth:email-verification:v2:{sha256(email)}:proof
```

## Signup

```text
POST /api/v1/auth/signup
```

Request:

```json
{
  "email": "test@example.com",
  "password": "password1234",
  "nickname": "runner",
  "verificationToken": "<확인 응답으로 받은 43자 증표>"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "memberId": 1
  },
  "error": null
}
```

Behavior:

```text
1. Compare and consume the email-bound proof atomically in Redis.
2. Encode the raw password with BCrypt.
3. Create an ACTIVE member.
4. Save the member in MySQL.
5. If the DB write fails, reverify the email; the consumed proof is not restored.
```

The signup response intentionally returns only `memberId`.

## Login

```text
POST /api/v1/auth/login
```

Request:

```json
{
  "email": "test@example.com",
  "password": "password1234"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresInSeconds": 3600
  },
  "error": null
}
```

Behavior:

```text
1. Find member by email.
2. Compare raw password with stored passwordHash using PasswordEncoder.matches.
3. Reject inactive members.
4. Issue a Bearer access token.
```

Login does not encode the request password again. BCrypt hashes can differ for the same raw password.

```java
passwordEncoder.matches(rawPassword, member.getPasswordHash())
```

## Protected API Authentication

Login returns the access token used by protected APIs.

```http
Authorization: Bearer {accessToken}
```

Examples:

```http
POST /api/v1/meetups
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

```http
GET /api/v1/notifications/subscribe
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Accept: text/event-stream
```

The previous `X-Member-Id` header was a development-only bridge. REST and SSE APIs now resolve `memberId` from the verified JWT principal.

## Get Member

```text
GET /api/v1/members/{memberId}
```

Response:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "test@example.com",
    "nickname": "runner",
  "verificationToken": "<확인 응답으로 받은 43자 증표>",
    "role": "USER",
    "status": "ACTIVE"
  },
  "error": null
}
```

This endpoint is protected by the JWT authentication filter.

## Error Codes

| HTTP Status | Code | Message |
| --- | --- | --- |
| 400 | `COMMON_001` | Invalid request. |
| 404 | `MEMBER_001` | Member not found. |
| 409 | `MEMBER_002` | Email is already in use. |
| 409 | `AUTH_001` | Email is already registered. |
| 400 | `AUTH_002` | Email verification code is expired or not found. |
| 400 | `AUTH_003` | Email verification code does not match. |
| 400 | `AUTH_004` | Email verification is required. |
| 401 | `AUTH_005` | Invalid email or password. |
| 403 | `AUTH_006` | Member is not active. |
| 401 | `AUTH_007` | Authentication is required. |
| 401 | `AUTH_008` | Access token is invalid. |

## Manual Test Script

Run the backend first:

```powershell
.\gradlew.bat bootRun --args=--spring.profiles.active=local
```

Then run:

```powershell
$email = "test$(Get-Random)@example.com"
$password = "password1234"
$nickname = "runner"

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/email-verifications/send" `
  -ContentType "application/json" `
  -Body (@{ email = $email } | ConvertTo-Json)

Start-Sleep -Seconds 1

$messages = Invoke-RestMethod -Method Get -Uri "http://localhost:8025/api/v1/messages"
$message = $messages.messages | Where-Object { $_.To[0].Address -eq $email } | Select-Object -First 1
$code = [regex]::Match($message.Snippet, '\d{6}').Value

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/email-verifications/confirm" `
  -ContentType "application/json" `
  -Body (@{ email = $email; code = $code } | ConvertTo-Json)

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/signup" `
  -ContentType "application/json" `
  -Body (@{ email = $email; password = $password; nickname = $nickname } | ConvertTo-Json)

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/login" `
  -ContentType "application/json" `
  -Body (@{ email = $email; password = $password } | ConvertTo-Json)
```

## 재시도·오류·개발 환경

이메일별 발송은 60초당 1회·1시간당 5회다. IP별 10분 제한은 발송/가입 10회, 코드 확인 60회, 로그인 30회다. 제한 응답은 429와 Retry-After를 제공한다. 새 발송은 이전 증표를 무효화한다. SMTP·Redis·DB 오류는 503이며 가입 저장 실패 때 재인증이 필요할 수 있다. 비밀번호·증표·코드를 로그나 URL에 넣지 않는다.

`local`과 `integration` 프로필은 Mailpit에만 사용할 로컬 발신 주소와 TLS/SMTP 인증 비활성 설정이다. 운영에서는 이 프로필을 사용하지 않고 MAIL_HOST/PORT/USERNAME/PASSWORD/FROM과 정확한 CORS Origin을 설정한다. 운영 기본은 STARTTLS 필수이며 연결·읽기·쓰기 timeout은 각각 5초다.
