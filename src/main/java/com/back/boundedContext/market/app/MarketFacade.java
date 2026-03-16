package com.back.boundedContext.market.app;

import com.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketFacade {
    private final MarketSupport marketSupport;
    private final MarketSyncMemberUseCase marketSyncMemberUseCase;


    @Transactional
    public void syncMember(MemberDto member) {
        marketSyncMemberUseCase.syncMember(member);
    }
}
