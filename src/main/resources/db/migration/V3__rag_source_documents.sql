-- Immutable versions of SEC source documents. No model outputs or published results in this migration.
CREATE TABLE rag_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(12) NOT NULL,
    issuer_cik VARCHAR(10) NOT NULL,
    accession VARCHAR(20) NOT NULL,
    form_type VARCHAR(12) NOT NULL,
    document_name VARCHAR(120) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    accepted_at DATETIME(6) NOT NULL, -- UTC
    fetched_at DATETIME(6) NOT NULL, -- UTC
    raw_sha256 CHAR(64) NOT NULL,
    raw_storage_key VARCHAR(160) NOT NULL,
    parser_version VARCHAR(32) NOT NULL,
    chunk_version VARCHAR(32) NOT NULL,
    index_status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_rag_document_version UNIQUE (symbol, accession, document_name, raw_sha256),
    INDEX idx_rag_document_symbol_accepted (symbol, accepted_at),
    INDEX idx_rag_document_accession (accession)
) ENGINE=InnoDB;

CREATE TABLE rag_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    ordinal_no INTEGER NOT NULL,
    section_text VARCHAR(1600) NOT NULL,
    text_sha256 CHAR(64) NOT NULL,
    CONSTRAINT uk_rag_chunk_ordinal UNIQUE (document_id, ordinal_no),
    CONSTRAINT fk_rag_chunk_document FOREIGN KEY (document_id) REFERENCES rag_document (id)
) ENGINE=InnoDB;
