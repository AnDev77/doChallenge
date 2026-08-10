package com.fitmeet.meetup.application;

import com.fitmeet.chat.domain.ChatRoom;
import com.fitmeet.chat.domain.ChatRoomRepository;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.meetup.domain.Meetup;
import com.fitmeet.meetup.domain.MeetupMember;
import com.fitmeet.meetup.domain.MeetupMemberRepository;
import com.fitmeet.meetup.domain.MeetupMemberStatus;
import com.fitmeet.meetup.domain.MeetupRepository;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupService {

    private final MemberRepository memberRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupMemberRepository meetupMemberRepository;
    private final ChatRoomRepository chatRoomRepository;

    public MeetupService(
            MemberRepository memberRepository,
            MeetupRepository meetupRepository,
            MeetupMemberRepository meetupMemberRepository,
            ChatRoomRepository chatRoomRepository
    ) {
        this.memberRepository = memberRepository;
        this.meetupRepository = meetupRepository;
        this.meetupMemberRepository = meetupMemberRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    @Transactional
    public CreateMeetupResult create(
            Long hostMemberId,
            String title,
            String description,
            String region,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            int maxMemberCount
    ) {
        Member host = memberRepository.findById(hostMemberId)
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
        if (!host.isActive()) {
            throw new BaseException(ErrorCode.INACTIVE_MEMBER);
        }

        Meetup meetup = meetupRepository.save(Meetup.create(
                hostMemberId,
                title,
                description,
                region,
                address,
                latitude,
                longitude,
                maxMemberCount
        ));
        MeetupMember hostMember = MeetupMember.host(meetup.getId(), hostMemberId);
        meetupMemberRepository.save(hostMember);

        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.createMeetupRoom(meetup.getId()));

        return new CreateMeetupResult(meetup.getId(), chatRoom.getId());
    }

    @Transactional
    public void join(Long meetupId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
        if (!member.isActive()) {
            throw new BaseException(ErrorCode.INACTIVE_MEMBER);
        }

        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new BaseException(ErrorCode.MEETUP_NOT_FOUND));
        if (meetupMemberRepository.existsByMeetupIdAndMemberId(meetupId, memberId)) {
            throw new BaseException(ErrorCode.MEETUP_ALREADY_JOINED);
        }

        int joinedMemberCount = meetupMemberRepository.countByMeetupIdAndStatus(
                meetupId,
                MeetupMemberStatus.JOINED
        );
        meetup.validateJoinable(joinedMemberCount);

        meetupMemberRepository.save(MeetupMember.member(meetupId, memberId));
    }
}
