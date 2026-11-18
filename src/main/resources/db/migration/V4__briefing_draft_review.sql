-- A draft and the exact source versions reviewed together. Only approved rows may be read as results.
CREATE TABLE briefing_draft (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(12) NOT NULL,
    as_of DATETIME(6) NOT NULL,
    summary VARCHAR(4000) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    reviewer VARCHAR(100) NULL,
    INDEX idx_briefing_draft_public (symbol, status, reviewed_at)
) ENGINE=InnoDB;

CREATE TABLE briefing_draft_evidence (
    draft_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    text_sha256 CHAR(64) NOT NULL,
    raw_sha256 CHAR(64) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (draft_id, chunk_id),
    CONSTRAINT fk_briefing_evidence_draft FOREIGN KEY (draft_id) REFERENCES briefing_draft (id),
    CONSTRAINT fk_briefing_evidence_chunk FOREIGN KEY (chunk_id) REFERENCES rag_chunk (id)
) ENGINE=InnoDB;
