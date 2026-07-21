package com.fitmeet.auth.presentation.response;

import com.fitmeet.member.domain.Member;

public record SignupResponse(
        Long memberId,
        String email,
        String nickname,
        String status
) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getStatus().name()
        );
    }
}
