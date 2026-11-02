package com.fitmeet.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fitmeet.auth.domain.*;
import com.fitmeet.member.domain.MemberRepository;
import com.fitmeet.common.exception.BaseException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailVerificationServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final EmailVerificationRepository repository = mock(EmailVerificationRepository.class);
    private final EmailVerificationCodeGenerator generator = mock(EmailVerificationCodeGenerator.class);
    private final EmailVerificationSender sender = mock(EmailVerificationSender.class);
    private final AuthAttemptLimiter limiter = mock(AuthAttemptLimiter.class);
    private final EmailVerificationService service = new EmailVerificationService(members, repository, generator, sender, limiter);
    private final String email = "test@example.invalid";

    @Test void confirmReturnsRandomProofButStoresOnlyHash() {
        when(repository.confirm(eq(email), eq("123456"), anyString()))
                .thenReturn(EmailVerificationRepository.Confirmation.CONFIRMED);
        String first = service.confirm(email, "123456");
        String second = service.confirm(email, "123456");
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
        verify(repository, times(2)).confirm(eq(email), eq("123456"), matches("[0-9a-f]{64}"));
    }

    @Test void missingAndWrongCodeNeverIssueProof() {
        when(repository.confirm(anyString(), anyString(), anyString()))
                .thenReturn(EmailVerificationRepository.Confirmation.NOT_FOUND,
                        EmailVerificationRepository.Confirmation.MISMATCH);
        assertThatThrownBy(() -> service.confirm(email, "123456")).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.confirm(email, "654321")).isInstanceOf(BaseException.class);
    }

    @Test void missingOrMalformedProofIsRejectedWithoutRedis() {
        assertThatThrownBy(() -> service.consumeVerifiedEmail(email, null)).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> service.consumeVerifiedEmail(email, "true")).isInstanceOf(BaseException.class);
        verifyNoInteractions(repository);
    }

    @Test void invalidOrConsumedProofIsRejected() {
        assertThatThrownBy(() -> service.consumeVerifiedEmail(email, "a".repeat(43)))
                .isInstanceOf(BaseException.class);
        verify(repository).consumeProof(eq(email), matches("[0-9a-f]{64}"));
    }

    @Test void smtpFailurePropagatesAndDoesNotGrantProof() {
        when(generator.generate()).thenReturn(EmailVerificationCode.of("123456", Duration.ofMinutes(5)));
        doThrow(new org.springframework.mail.MailSendException("delivery failed")).when(sender).send(email, "123456");
        assertThatThrownBy(() -> service.send(email)).isInstanceOf(BaseException.class);
        verify(repository, never()).confirm(anyString(), anyString(), anyString());
        verify(repository).discardCode(email, "123456");
    }
    @Test void throttledRequestNeverSendsMail() {
        doThrow(new com.fitmeet.common.exception.RateLimitedException(60)).when(limiter)
                .check("send-email-minute", email, 1, 60);
        assertThatThrownBy(() -> service.send(email)).isInstanceOf(BaseException.class);
        verifyNoInteractions(sender, generator, repository);
    }
    @Test void emailLimitUsesCaseFoldedIdentity() {
        when(generator.generate()).thenReturn(EmailVerificationCode.of("123456", Duration.ofMinutes(5)));
        service.send("TEST@example.invalid");
        verify(limiter).check("send-email-minute", email, 1, 60);
        verify(limiter).check("send-email-hour", email, 5, 3600);
    }

}
