package com.fitmeet.auth.application;

import com.fitmeet.auth.domain.EmailVerificationCode;
import com.fitmeet.auth.domain.AuthAttemptLimiter;
import java.util.Locale;
import org.springframework.mail.MailException;
import com.fitmeet.auth.domain.EmailVerificationCodeGenerator;
import com.fitmeet.auth.domain.EmailVerificationRepository;
import com.fitmeet.auth.domain.EmailVerificationSender;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.member.domain.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final AuthAttemptLimiter limiter;
    private final SecureRandom random = new SecureRandom();
    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationSender emailVerificationSender;

    public EmailVerificationService(
            MemberRepository memberRepository,
            EmailVerificationRepository emailVerificationRepository,
            EmailVerificationCodeGenerator codeGenerator,
            EmailVerificationSender emailVerificationSender,
            AuthAttemptLimiter limiter
    ) {
        this.limiter = limiter;
        this.memberRepository = memberRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.codeGenerator = codeGenerator;
        this.emailVerificationSender = emailVerificationSender;
    }

    @Transactional(readOnly = true)
    public void validateAvailableEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BaseException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    @Transactional
    public void send(String email) {
        String identity = email.strip().toLowerCase(Locale.ROOT);
        limiter.check("send-email-minute", identity, 1, 60);
        limiter.check("send-email-hour", identity, 5, 3600);
        validateAvailableEmail(email);
        EmailVerificationCode code = codeGenerator.generate();
        emailVerificationRepository.save(email, code);
        try {
            emailVerificationSender.send(email, code.value());
        } catch (MailException exception) {
            emailVerificationRepository.discardCode(email, code.value());
            throw new BaseException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }

    @Transactional
    public String confirm(String email, String code) {
        validateAvailableEmail(email);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var result = emailVerificationRepository.confirm(email, code, hash(proof));
        if (result == EmailVerificationRepository.Confirmation.NOT_FOUND) {
            throw new BaseException(ErrorCode.EMAIL_VERIFICATION_CODE_NOT_FOUND);
        }
        if (result == EmailVerificationRepository.Confirmation.ATTEMPTS_EXCEEDED) {
            throw new BaseException(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED);
        }
        if (result != EmailVerificationRepository.Confirmation.CONFIRMED) {
            throw new BaseException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }
        return proof;
    }

    public void consumeVerifiedEmail(String email, String proof) {
        if (proof == null || !proof.matches("[A-Za-z0-9_-]{43}")
                || !emailVerificationRepository.consumeProof(email, hash(proof))) {
            throw new BaseException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private String hash(String proof) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(proof.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
