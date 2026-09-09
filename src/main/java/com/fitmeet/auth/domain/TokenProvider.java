package com.fitmeet.auth.domain;

import com.fitmeet.member.domain.Member;

public interface TokenProvider {

    AccessToken createAccessToken(Member member);

    AuthenticatedMember parseAccessToken(String token);
}
