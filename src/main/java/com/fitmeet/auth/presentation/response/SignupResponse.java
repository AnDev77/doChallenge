package com.fitmeet.auth.presentation.response;

import com.fitmeet.member.domain.Member;

public record SignupResponse(
        Long memberId
) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId());
    }
}
