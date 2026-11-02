package com.fitmeet.auth.infrastructure;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Explicit opt-in delivery probe, excluded from the application JAR. */
public final class SmtpDeliverySmoke {
    private static final Pattern MAILBOX = Pattern.compile(
            "[A-Za-z0-9_+.-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}");

    private SmtpDeliverySmoke() {}

    // Deliberately not a record: the generated toString would expose credentials.
    static final class Settings {
        final String username;
        final String password;
        final String recipient;

        Settings(Map<String, String> env) {
            if (!"true".equals(env.get("MAIL_SMOKE_CONFIRM"))) {
                throw new IllegalArgumentException("Opt-in required");
            }
            if (!"smtp.gmail.com".equals(env.get("MAIL_HOST"))
                    || !"587".equals(env.get("MAIL_PORT"))) {
                throw new IllegalArgumentException("Unsupported transport");
            }
            username = mailbox(env.get("MAIL_USERNAME"));
            recipient = mailbox(env.get("MAIL_TEST_TO"));
            if (!username.equals(mailbox(env.get("MAIL_FROM")))) {
                throw new IllegalArgumentException("Sender mismatch");
            }
            password = env.get("MAIL_PASSWORD");
            if (password == null || password.isBlank() || password.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Credential missing or malformed");
            }
        }
    }

    private static String mailbox(String value) {
        if (value == null || value.length() > 254 || !MAILBOX.matcher(value).matches()) {
            throw new IllegalArgumentException("Single mailbox required");
        }
        return value;
    }

    static JavaMailSenderImpl transport(Settings settings) {
        var mail = new JavaMailSenderImpl();
        mail.setHost("smtp.gmail.com");
        mail.setPort(587);
        mail.setUsername(settings.username);
        mail.setPassword(settings.password);
        mail.setDefaultEncoding("UTF-8");
        var properties = mail.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.debug", "false");
        return mail;
    }

    static void deliver(Settings settings) {
        var sender = new SmtpEmailVerificationSender(transport(settings), settings.username);
        // No verification state is created; this text cannot be used for signup.
        sender.send(settings.recipient, "연결 시험용입니다. 이 메일로는 가입할 수 없습니다. 실제 인증번호가 아님");
    }

    static String run(Map<String, String> env, Consumer<Settings> delivery) {
        final Settings settings;
        try {
            settings = new Settings(env);
        } catch (RuntimeException ignored) {
            return "CONFIGURATION_REJECTED";
        }
        try {
            delivery.accept(settings); // One attempt only; never retry ambiguous sends.
            return "SMTP_ACCEPTED_INBOX_UNCONFIRMED";
        } catch (MailAuthenticationException ignored) {
            return "SMTP_AUTHENTICATION_FAILED";
        } catch (RuntimeException ignored) {
            // SMTP errors can include addresses. Do not print messages or stack traces.
            return "SMTP_FAILED_DELIVERY_UNKNOWN";
        }
    }

    // Identifies only the failing setting name; never exposes its value.
    static String diagnostic(Map<String, String> env) {
        if (!"true".equals(env.get("MAIL_SMOKE_CONFIRM"))) return "CHECK_MAIL_SMOKE_CONFIRM";
        if (!"smtp.gmail.com".equals(env.get("MAIL_HOST"))) return "CHECK_MAIL_HOST";
        if (!"587".equals(env.get("MAIL_PORT"))) return "CHECK_MAIL_PORT";
        if (!validMailbox(env.get("MAIL_USERNAME"))) return "CHECK_MAIL_USERNAME";
        if (!validMailbox(env.get("MAIL_TEST_TO"))) return "CHECK_MAIL_TEST_TO";
        if (!validMailbox(env.get("MAIL_FROM"))
                || !env.get("MAIL_FROM").equals(env.get("MAIL_USERNAME"))) return "CHECK_MAIL_FROM";
        String password = env.get("MAIL_PASSWORD");
        if (password == null || password.isBlank() || password.chars().anyMatch(Character::isWhitespace))
            return "CHECK_MAIL_PASSWORD";
        return "CHECK_CONFIGURATION";
    }

    private static boolean validMailbox(String value) {
        return value != null && value.length() <= 254 && MAILBOX.matcher(value).matches();
    }

    public static void main(String[] args) {
        String result = run(System.getenv(), SmtpDeliverySmoke::deliver);
        if ("CONFIGURATION_REJECTED".equals(result)) System.out.println(diagnostic(System.getenv()));
        System.out.println(result);
        if (!"SMTP_ACCEPTED_INBOX_UNCONFIRMED".equals(result)) System.exit(1);
    }
}
