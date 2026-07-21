package com.fitmeet.member.presentation.response;

import com.fitmeet.member.domain.Member;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        String role,
        String status
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole().name(),
                member.getStatus().name()
        );
    }
}
