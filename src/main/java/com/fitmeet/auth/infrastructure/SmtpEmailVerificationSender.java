package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.EmailVerificationSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailVerificationSender implements EmailVerificationSender {

    private final JavaMailSender javaMailSender;

    public SmtpEmailVerificationSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[Do Challenge] Email verification code");
        message.setText("Your Do Challenge verification code is " + code + ".\n\nThis code expires in 5 minutes.");
        javaMailSender.send(message);
    }
}
