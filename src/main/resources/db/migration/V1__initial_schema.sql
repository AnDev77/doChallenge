-- Initial schema for a fresh MySQL 8.4 deployment. Existing databases need a
-- separately reviewed baseline after their schema and backup are verified.
CREATE TABLE member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(50) NOT NULL,
    email_verified_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_member_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE meetup (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    host_member_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    region VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    max_member_count INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_meetup_region_status_created_at (region, status, created_at),
    INDEX idx_meetup_host_member_id (host_member_id)
) ENGINE=InnoDB;

CREATE TABLE meetup_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    meetup_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_meetup_member_meetup_id_member_id UNIQUE (meetup_id, member_id),
    INDEX idx_meetup_member_member_status (member_id, status),
    INDEX idx_meetup_member_meetup_status (meetup_id, status)
) ENGINE=InnoDB;

CREATE TABLE chat_room (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    meetup_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    last_message_id BIGINT,
    last_message_content VARCHAR(500),
    last_message_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_chat_room_meetup_id_type UNIQUE (meetup_id, type),
    INDEX idx_chat_room_meetup_id (meetup_id)
) ENGINE=InnoDB;

CREATE TABLE chat_room_read_state (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    last_read_message_id BIGINT,
    last_read_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_chat_room_read_state_room_id_member_id UNIQUE (room_id, member_id),
    INDEX idx_chat_room_read_state_member_id (member_id)
) ENGINE=InnoDB;

CREATE TABLE chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    sender_member_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_chat_message_room_id_id (room_id, id),
    INDEX idx_chat_message_sender_member_id (sender_member_id)
) ENGINE=InnoDB;

CREATE TABLE finance_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(4) NOT NULL,
    author_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_finance_chat_request UNIQUE (symbol, author_id, request_id),
    INDEX idx_finance_chat_symbol_id (symbol, id)
) ENGINE=InnoDB;
