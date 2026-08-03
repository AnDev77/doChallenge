package com.fitmeet.auth.application;

import com.fitmeet.auth.domain.AccessToken;
import com.fitmeet.auth.domain.TokenProvider;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional(readOnly = true)
    public AccessToken login(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!member.isActive()) {
            throw new BaseException(ErrorCode.INACTIVE_MEMBER);
        }

        return tokenProvider.createAccessToken(member);
    }
}
