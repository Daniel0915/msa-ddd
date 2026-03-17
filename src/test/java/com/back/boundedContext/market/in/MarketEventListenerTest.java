package com.back.boundedContext.market.in;

import com.back.boundedContext.market.app.MarketFacade;
import com.back.shared.member.dto.MemberDto;
import com.back.shared.member.event.MemberJoinedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@SpringBootTest
@EnableRetry
class MarketEventListenerTest {

    @MockitoBean
    private MarketFacade marketFacade;

    @Autowired
    private MarketEventListener listener;

    @Test
    void 재시도_backoff_테스트() {
        // 2번 실패 후 3번째 성공
        doThrow(new RuntimeException("1차 실패"))
                .doThrow(new RuntimeException("2차 실패"))
                .doNothing()
                .when(marketFacade).syncMember(any());

        long start = System.currentTimeMillis();
        listener.handel(new MemberJoinedEvent(testMember()));
        long elapsed = System.currentTimeMillis() - start;

        // 5초 backoff × 2회 = 약 10초 이상
        assertThat(elapsed).isGreaterThanOrEqualTo(10000);
        verify(marketFacade, times(3)).syncMember(any());
    }

    private MemberDto testMember() {
        return MemberDto.builder()
                .id(1)
                .username("testUser")
                .nickname("테스트")
                .activityScore(0)
                .createDate(LocalDateTime.now())
                .modifyDate(LocalDateTime.now())
                .build();
    }
}
