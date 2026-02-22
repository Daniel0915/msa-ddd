package com.back.boundedContext.member.domain;

import com.back.shared.member.dto.MemberDto;
import com.back.shared.member.event.MemberModifiedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "MEMBER_MEMBER")
@Getter
@NoArgsConstructor
public class Member extends SourceMember {
    public Member(String username, String password, String nickname) {
        super(username, password, nickname);
    }

    public MemberDto toDto() {
        return MemberDto.builder()
                        .id(getId())
                        .createDate(getCreateDate())
                        .modifyDate(getModifyDate())
                        .username(getUsername())
                        .nickname(getNickname())
                        .activityScore(getActivityScore())
                        .build();
    }

    public int increaseActivityScore(int amount) {
        if (amount == 0) return getActivityScore();

        setActivityScore(getActivityScore() + amount);

        publishEvent(new MemberModifiedEvent(toDto()));

        return getActivityScore();
    }
}
