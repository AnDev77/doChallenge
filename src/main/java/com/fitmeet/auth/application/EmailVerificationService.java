package com.fitmeet.auth.application;

import com.fitmeet.auth.domain.EmailVerificationCode;
import com.fitmeet.auth.domain.EmailVerificationCodeGenerator;
import com.fitmeet.auth.domain.EmailVerificationRepository;
import com.fitmeet.auth.domain.EmailVerificationSender;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.member.domain.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationSender emailVerificationSender;

    public EmailVerificationService(
            MemberRepository memberRepository,
            EmailVerificationRepository emailVerificationRepository,
            EmailVerificationCodeGenerator codeGenerator,
            EmailVerificationSender emailVerificationSender
    ) {
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
        validateAvailableEmail(email);
        EmailVerificationCode code = codeGenerator.generate();
        emailVerificationRepository.save(email, code);
        emailVerificationSender.send(email, code.value());
    }

    @Transactional
    public void confirm(String email, String code) {
        validateAvailableEmail(email);
        EmailVerificationCode savedCode = emailVerificationRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(ErrorCode.EMAIL_VERIFICATION_CODE_NOT_FOUND));

        if (!savedCode.matches(code)) {
            throw new BaseException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        emailVerificationRepository.deleteCode(email);
        emailVerificationRepository.markVerified(email);
    }

    @Transactional(readOnly = true)
    public void validateVerifiedEmail(String email) {
        if (!emailVerificationRepository.isVerified(email)) {
            throw new BaseException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    @Transactional
    public void consumeVerifiedEmail(String email) {
        validateVerifiedEmail(email);
        emailVerificationRepository.deleteVerified(email);
    }
}
