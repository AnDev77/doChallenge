package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.EmailVerificationSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailVerificationSender implements EmailVerificationSender {

    private final String from;

    private final JavaMailSender javaMailSender;

    public SmtpEmailVerificationSender(JavaMailSender javaMailSender, @Value("${app.mail.from:}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String code) {
        if (from == null || from.isBlank()) throw new MailSendException("Sender not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[ShockGraph] 이메일 인증번호");
        message.setText("ShockGraph 인증번호는 " + code + "입니다.\n\n5분 안에 입력해 주세요. 요청하지 않았다면 무시하세요.");
        javaMailSender.send(message);
    }
}
