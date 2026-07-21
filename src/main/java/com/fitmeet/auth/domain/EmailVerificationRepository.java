package com.fitmeet.auth.domain;

import java.util.Optional;

public interface EmailVerificationRepository {

    void save(String email, EmailVerificationCode code);

    Optional<EmailVerificationCode> findByEmail(String email);

    void deleteCode(String email);

    void markVerified(String email);

    boolean isVerified(String email);

    void deleteVerified(String email);
}
