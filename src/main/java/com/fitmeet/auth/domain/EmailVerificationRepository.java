package com.fitmeet.auth.domain;

public interface EmailVerificationRepository {
    void save(String email, EmailVerificationCode code);
    Confirmation confirm(String email, String code, String proofHash);
    boolean consumeProof(String email, String proofHash);

    enum Confirmation { CONFIRMED, NOT_FOUND, MISMATCH }
}
