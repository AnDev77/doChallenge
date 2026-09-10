package com.fitmeet.auth.domain;

import com.fitmeet.member.domain.MemberRole;

public record AuthenticatedMember(
        Long memberId,
        String email,
        MemberRole role
) {
}
