package com.fitmeet.meetup.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeetupMemberTest {

    @Test
    void hostMemberIsJoinedWithHostRole() {
        MeetupMember host = MeetupMember.host(1L, 10L);

        assertThat(host.getMeetupId()).isEqualTo(1L);
        assertThat(host.getMemberId()).isEqualTo(10L);
        assertThat(host.getRole()).isEqualTo(MeetupMemberRole.HOST);
        assertThat(host.getStatus()).isEqualTo(MeetupMemberStatus.JOINED);
        assertThat(host.getJoinedAt()).isNotNull();
    }

    @Test
    void regularMemberIsJoinedWithMemberRole() {
        MeetupMember member = MeetupMember.member(1L, 20L);

        assertThat(member.getMeetupId()).isEqualTo(1L);
        assertThat(member.getMemberId()).isEqualTo(20L);
        assertThat(member.getRole()).isEqualTo(MeetupMemberRole.MEMBER);
        assertThat(member.getStatus()).isEqualTo(MeetupMemberStatus.JOINED);
    }
}
