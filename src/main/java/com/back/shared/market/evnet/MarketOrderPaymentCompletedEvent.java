package com.back.shared.market.evnet;

import com.back.shared.market.dto.OrderDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MarketOrderPaymentCompletedEvent {
    private final OrderDto orderDto;
}
