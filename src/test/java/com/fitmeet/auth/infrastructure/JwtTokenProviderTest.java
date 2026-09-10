package com.fitmeet.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmeet.auth.domain.AccessToken;
import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            "test-secret-key-test-secret-key-test-secret-key",
            3600,
            new ObjectMapper()
    );

    @Test
    void createAndParseAccessToken() {
        Member member = Member.createVerified("runner@example.com", "encoded-password", "runner");
        ReflectionTestUtils.setField(member, "id", 1L);

        AccessToken accessToken = jwtTokenProvider.createAccessToken(member);

        AuthenticatedMember authenticatedMember = jwtTokenProvider.parseAccessToken(accessToken.value());

        assertThat(authenticatedMember.memberId()).isEqualTo(1L);
        assertThat(authenticatedMember.email()).isEqualTo("runner@example.com");
        assertThat(authenticatedMember.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    void parseAccessTokenRejectsTamperedToken() {
        Member member = Member.createVerified("runner@example.com", "encoded-password", "runner");
        ReflectionTestUtils.setField(member, "id", 1L);
        AccessToken accessToken = jwtTokenProvider.createAccessToken(member);
        String tamperedToken = accessToken.value().substring(0, accessToken.value().length() - 1) + "x";

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(tamperedToken))
                .isInstanceOf(BaseException.class);
    }
}
