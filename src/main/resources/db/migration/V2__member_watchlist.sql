-- Additive: previous V1 applications continue to use member and chat tables.
CREATE TABLE member_watchlist (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    symbol VARCHAR(4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_member_watchlist_member_symbol UNIQUE (member_id, symbol),
    CONSTRAINT fk_member_watchlist_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB;
