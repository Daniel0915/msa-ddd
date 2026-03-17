package com.back.boundedContext.market.app;

import com.back.boundedContext.market.domain.Cart;
import com.back.global.rsData.RsData;
import com.back.shared.market.evnet.MarketMemberDto;
import com.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketFacade {
    private final MarketSupport marketSupport;
    private final MarketSyncMemberUseCase marketSyncMemberUseCase;
    private final MarketCreateCartUseCase marketCreateCartUseCase;


    @Transactional
    public void syncMember(MemberDto member) {
        marketSyncMemberUseCase.syncMember(member);
    }

    @Transactional
    public RsData<Cart> createCart(MarketMemberDto buyer) {
        return marketCreateCartUseCase.createCart(buyer);
    }

}
