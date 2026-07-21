package com.fitmeet.member.application;

import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member signupVerifiedMember(String email, String rawPassword, String nickname) {
        if (memberRepository.existsByEmail(email)) {
            throw new BaseException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.createVerified(
                email,
                passwordEncoder.encode(rawPassword),
                nickname
        );
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Member getById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Member getByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
