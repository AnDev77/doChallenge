package com.fitmeet.auth.infrastructure;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SmtpDeliverySmokeTest {
    @Test void diagnosticPrintsOnlyKnownSettingNames() {
        for (String key : valid().keySet()) {
            var env = valid();
            env.remove(key);
            String result = SmtpDeliverySmoke.diagnostic(env);
            assertThat(result).isEqualTo("CHECK_" + key);
            assertThat(result).doesNotContain("@", "synthetic-not-a-secret");
        }
    }

    @Test void productionSenderAddressesOneMailboxWithoutCcOrBcc() {
        var mail = mock(JavaMailSender.class);
        var sender = new SmtpEmailVerificationSender(mail, "sender@example.invalid");
        sender.send("recipient@example.invalid", "synthetic-code");
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captured.capture());
        verifyNoMoreInteractions(mail);
        assertThat(captured.getValue().getFrom()).isEqualTo("sender@example.invalid");
        assertThat(captured.getValue().getTo()).containsExactly("recipient@example.invalid");
        assertThat(captured.getValue().getCc()).isNull();
        assertThat(captured.getValue().getBcc()).isNull();
    }

    private Map<String, String> valid() {
        return new HashMap<>(Map.of(
                "MAIL_SMOKE_CONFIRM", "true", "MAIL_HOST", "smtp.gmail.com", "MAIL_PORT", "587",
                "MAIL_USERNAME", "sender@example.invalid", "MAIL_FROM", "sender@example.invalid",
                "MAIL_TEST_TO", "recipient@example.invalid", "MAIL_PASSWORD", "synthetic-not-a-secret"));
    }

    @Test void explicitOptInAndEverySettingAreRequiredBeforeSending() {
        for (String key : valid().keySet()) {
            var env = valid();
            env.remove(key);
            assertThat(SmtpDeliverySmoke.run(env, ignored -> { throw new AssertionError("Must not send"); }))
                    .isEqualTo("CONFIGURATION_REJECTED");
        }
    }

    @Test void rejectsRecipientListsHeaderInjectionAndDisplayNames() {
        for (String recipient : new String[]{"a@example.invalid,b@example.invalid", "a@example.invalid;b@example.invalid",
                "a@example.invalid\r\nBcc: b@example.invalid", "Name <a@example.invalid>", " "}) {
            var env = valid();
            env.put("MAIL_TEST_TO", recipient);
            assertThat(SmtpDeliverySmoke.run(env, ignored -> { throw new AssertionError("Must not send"); }))
                    .isEqualTo("CONFIGURATION_REJECTED");
        }
    }

    @Test void rejectsUnexpectedTransportSenderAndMalformedCredential() {
        for (var change : Map.of("MAIL_HOST", "other.invalid", "MAIL_PORT", "25",
                "MAIL_FROM", "other@example.invalid", "MAIL_PASSWORD", "has spaces",
                "MAIL_SMOKE_CONFIRM", "false").entrySet()) {
            var env = valid();
            env.put(change.getKey(), change.getValue());
            assertThat(SmtpDeliverySmoke.run(env, ignored -> { throw new AssertionError("Must not send"); }))
                    .isEqualTo("CONFIGURATION_REJECTED");
        }
    }

    @Test void acceptanceDoesNotClaimInboxDeliveryAndCallsExactlyOnce() {
        var calls = new AtomicInteger();
        assertThat(SmtpDeliverySmoke.run(valid(), settings -> {
            assertThat(settings.recipient).isEqualTo("recipient@example.invalid");
            calls.incrementAndGet();
        })).isEqualTo("SMTP_ACCEPTED_INBOX_UNCONFIRMED");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void failuresAreSanitizedWithoutAutomaticRetry() {
        for (var error : new RuntimeException[]{new MailAuthenticationException("private details"),
                new MailSendException("private details"), new IllegalStateException("private details")}) {
            var calls = new AtomicInteger();
            String result = SmtpDeliverySmoke.run(valid(), ignored -> { calls.incrementAndGet(); throw error; });
            assertThat(result).isEqualTo(error instanceof MailAuthenticationException
                    ? "SMTP_AUTHENTICATION_FAILED" : "SMTP_FAILED_DELIVERY_UNKNOWN");
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test void requiresTlsCertificateIdentityTimeoutsAndNoMailDebug() {
        var mail = SmtpDeliverySmoke.transport(new SmtpDeliverySmoke.Settings(valid()));
        assertThat(mail.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(mail.getPort()).isEqualTo(587);
        var properties = mail.getJavaMailProperties();
        for (String key : new String[]{"auth", "starttls.enable", "starttls.required", "ssl.checkserveridentity"}) {
            assertThat(properties.getProperty("mail.smtp." + key)).isEqualTo("true");
        }
        for (String key : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            assertThat(properties.getProperty("mail.smtp." + key)).isEqualTo("5000");
        }
        assertThat(properties.getProperty("mail.debug")).isEqualTo("false");
    }
}
