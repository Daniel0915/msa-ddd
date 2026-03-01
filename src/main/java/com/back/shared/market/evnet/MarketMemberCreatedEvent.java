package com.back.shared.market.evnet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class MarketMemberCreatedEvent {
    private final MarketMemberDto member;
}
