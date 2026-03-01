package com.back.shared.market.dto;

import com.back.standard.modelType.HasModelTypeCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderItemDto implements HasModelTypeCode {
    private final int id;
    private final LocalDateTime createDate;
    private final LocalDateTime modifyDate;
    private final int orderId;
    private final int buyerId;
    private final String buyerName;
    private final int sellerId;
    private final String sellerName;
    private final int productId;
    private final String productName;
    private final long price;
    private final long salePrice;
    private final double payoutRate;
    private final long payoutFee;
    private final long salePriceWithoutFee;

    @Override
    public String getModelTypeCode() {
        return "OrderItem";
    }
}
