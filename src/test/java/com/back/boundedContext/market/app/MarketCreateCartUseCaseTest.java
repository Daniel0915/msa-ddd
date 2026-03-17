package com.back.boundedContext.market.app;

import com.back.boundedContext.market.domain.Cart;
import com.back.boundedContext.market.domain.MarketMember;
import com.back.boundedContext.market.out.CartRepository;
import com.back.boundedContext.market.out.MarketMemberRepository;
import com.back.global.rsData.RsData;
import com.back.shared.market.evnet.MarketMemberDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 단위 테스트 (Mock)

@ExtendWith(MockitoExtension.class)
class MarketCreateCartUseCaseTest {

    @InjectMocks
    private MarketCreateCartUseCase marketCreateCartUseCase;

    @Mock
    private MarketMemberRepository marketMemberRepository;

    @Mock
    private CartRepository cartRepository;

    @Test
    void 장바구니_생성_성공() {
        // given
        MarketMember mockBuyer = mock(MarketMember.class);
        given(mockBuyer.getId()).willReturn(1);
        given(marketMemberRepository.getReferenceById(1)).willReturn(mockBuyer);

        MarketMemberDto buyer = MarketMemberDto.builder()
                                               .id(1)
                                               .username("testUser")
                                               .nickname("테스트")
                                               .activityScore(0)
                                               .createDate(LocalDateTime.now())
                                               .modifyDate(LocalDateTime.now())
                                               .build();

        // when
        RsData<Cart> result = marketCreateCartUseCase.createCart(buyer);

        // Then
        assertThat(result.getResultCode()).isEqualTo("201-1");
        assertThat(result.getData()).isNotNull();
        verify(cartRepository).save(any(Cart.class));
    }

}