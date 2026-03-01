package com.back.boundedContext.market.app;

import com.back.boundedContext.market.domain.MarketMember;
import com.back.boundedContext.market.out.MarketMemberRepository;
import com.back.global.eventPublisher.EventPublisher;
import com.back.shared.market.evnet.MarketMemberCreatedEvent;
import com.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketSyncMemberUseCase {
    private final MarketMemberRepository marketMemberRepository;
    private final EventPublisher         eventPublisher;

    public MarketMember syncMember(MemberDto member) {
        boolean isNew = !marketMemberRepository.existsById(member.getId());

        // 신규 회원(MemberJoinedEvent) OR 회원 수정(MemberModifiedEvent)
        MarketMember _member = marketMemberRepository.save(
                new MarketMember(
                        member.getId(),
                        member.getCreateDate(),
                        member.getModifyDate(),
                        member.getUsername(),
                        "",
                        member.getNickname(),
                        member.getActivityScore()
                )
        );

        if (isNew) {
            eventPublisher.publish(new MarketMemberCreatedEvent(_member.toDto()));
        }

        return _member;
    }
}
