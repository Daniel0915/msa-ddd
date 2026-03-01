package com.back.shared.market.dto;

import com.back.standard.modelType.HasModelTypeCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class OrderDto implements HasModelTypeCode {
    private final int           id;
    private final LocalDateTime createDate;
    private final LocalDateTime modifyDate;
    private final int           customerId;
    private final String        customerName;
    private final long          price;
    private final long          salePrice;
    private final LocalDateTime requestPaymentDate;
    private final LocalDateTime paymentDate;

    @Override
    public String getModelTypeCode() {
        return "Order";
    }
}
