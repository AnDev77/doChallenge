package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.EmailVerificationCode;
import com.fitmeet.auth.domain.EmailVerificationCodeGenerator;
import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RandomEmailVerificationCodeGenerator implements EmailVerificationCodeGenerator {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public EmailVerificationCode generate() {
        int number = secureRandom.nextInt(1_000_000);
        return EmailVerificationCode.of(String.format("%06d", number), DEFAULT_TTL);
    }
}
