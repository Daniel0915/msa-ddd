package com.back.shared.market.evnet;

import com.back.shared.market.dto.OrderDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class MarketOrderPaymentRequestedEvent {
    private final OrderDto order;
    private final long pgPaymentAmount;
}
