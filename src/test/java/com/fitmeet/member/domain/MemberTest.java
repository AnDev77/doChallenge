package com.fitmeet.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void createdMemberIsActiveBecauseEmailWasAlreadyVerified() {
        Member member = Member.createVerified("user@example.com", "encoded-password", "runner");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getEmailVerifiedAt()).isNotNull();
        assertThat(member.isActive()).isTrue();
    }
}
