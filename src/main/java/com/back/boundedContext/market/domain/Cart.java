package com.back.boundedContext.market.domain;

import com.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.PERSIST;
import static jakarta.persistence.CascadeType.REMOVE;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "MARKET_CART")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class Cart extends BaseManualIdAndTime {
    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "buyer_id")
    private MarketMember buyer;

    @OneToMany(mappedBy = "cart", cascade = {PERSIST, REMOVE}, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    private int itemsCount;

    public Cart(MarketMember buyer) {
        super(buyer.getId());
        this.buyer = buyer;
    }

    public boolean hasItems() { return itemsCount > 0; }

    public void addItem(Product product) {
        CartItem cartItem = new CartItem(this, product);
        this.getItems().add(cartItem);
        this.itemsCount++;
    }

    public void clearItems() {
        this.getItems().clear();
    }
}
