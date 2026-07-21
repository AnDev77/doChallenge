package com.fitmeet.auth.domain;

public interface EmailVerificationSender {

    void send(String email, String code);
}
