package com.fitmeet.member.presentation;

import com.fitmeet.common.response.ApiResponse;
import com.fitmeet.member.application.MemberService;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.presentation.response.MemberResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/{memberId}")
    public ApiResponse<MemberResponse> getMember(@PathVariable Long memberId) {
        Member member = memberService.getById(memberId);
        return ApiResponse.success(MemberResponse.from(member));
    }

}
