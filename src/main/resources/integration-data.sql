insert into member (
    id,
    email,
    password_hash,
    nickname,
    role,
    status,
    email_verified_at,
    created_at,
    updated_at
) values (
    1,
    'host@example.com',
    'integration-password-hash',
    'host',
    'USER',
    'ACTIVE',
    current_timestamp,
    current_timestamp,
    current_timestamp
);
