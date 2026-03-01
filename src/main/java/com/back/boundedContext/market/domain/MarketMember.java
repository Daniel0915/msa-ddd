package com.back.boundedContext.market.domain;

import com.back.shared.market.evnet.MarketMemberDto;
import com.back.shared.member.domain.ReplicaMember;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "MARKET_MEMBER")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class MarketMember extends ReplicaMember {
    public MarketMember(int id, LocalDateTime createDate, LocalDateTime modifyDate, String username, String password, String nickname, int activityScore) {
        super(id, createDate, modifyDate, username, password, nickname, activityScore);
    }

    public MarketMemberDto toDto() {
        return new MarketMemberDto(
                getId(),
                getCreateDate(),
                getModifyDate(),
                getUsername(),
                getNickname(),
                getActivityScore()
        );
    }
}
