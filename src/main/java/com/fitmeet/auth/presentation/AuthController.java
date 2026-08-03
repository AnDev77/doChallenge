package com.fitmeet.auth.presentation;

import com.fitmeet.auth.application.AuthService;
import com.fitmeet.auth.application.EmailVerificationService;
import com.fitmeet.auth.domain.AccessToken;
import com.fitmeet.auth.presentation.request.ConfirmEmailVerificationRequest;
import com.fitmeet.auth.presentation.request.LoginRequest;
import com.fitmeet.auth.presentation.request.SendEmailVerificationRequest;
import com.fitmeet.auth.presentation.request.SignupRequest;
import com.fitmeet.auth.presentation.response.LoginResponse;
import com.fitmeet.auth.presentation.response.SignupResponse;
import com.fitmeet.common.response.ApiResponse;
import com.fitmeet.member.application.MemberService;
import com.fitmeet.member.domain.Member;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final EmailVerificationService emailVerificationService;
    private final AuthService authService;

    public AuthController(
            MemberService memberService,
            EmailVerificationService emailVerificationService,
            AuthService authService
    ) {
        this.memberService = memberService;
        this.emailVerificationService = emailVerificationService;
        this.authService = authService;
    }

    @PostMapping("/email-verifications/send")
    public ApiResponse<Void> sendEmailVerification(@Valid @RequestBody SendEmailVerificationRequest request) {
        emailVerificationService.send(request.email());
        return ApiResponse.success(null);
    }

    @PostMapping("/email-verifications/confirm")
    public ApiResponse<Void> confirmEmailVerification(@Valid @RequestBody ConfirmEmailVerificationRequest request) {
        emailVerificationService.confirm(request.email(), request.code());
        return ApiResponse.success(null);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        emailVerificationService.validateVerifiedEmail(request.email());
        Member member = memberService.signupVerifiedMember(request.email(), request.password(), request.nickname());
        emailVerificationService.consumeVerifiedEmail(member.getEmail());
        return ApiResponse.success(SignupResponse.from(member));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AccessToken accessToken = authService.login(request.email(), request.password());
        return ApiResponse.success(new LoginResponse("Bearer", accessToken.value(), accessToken.expiresInSeconds()));
    }
}
