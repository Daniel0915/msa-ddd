package com.back.boundedContext.market.domain;

import com.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "MARKET_CART_ITEM")
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Getter
public class CartItem extends BaseIdAndTime {
    @ManyToOne(fetch = LAZY)
    private Cart cart;
    @ManyToOne(fetch = LAZY)
    private Product product;
}
