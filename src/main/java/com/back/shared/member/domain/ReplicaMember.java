package com.back.shared.member.domain;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class ReplicaMember extends BaseMember {
    @Id
    private int           id;
    /*
    별도의 @EntityListeners(AuditingEntityListener.class), @CreatedDate, @LastModifiedDate 사용하지 않는 이유는 레플리카 테이블로서,
    Member 도메인으로 이벤트로 받은 데이터로 저장해야 데이터 정합성을 위함
     */
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;

    public ReplicaMember(int id, LocalDateTime createDate, LocalDateTime modifyDate, String username, String password, String nickname, int activityScore) {
        super(username, password, nickname, activityScore);
        this.id = id;
        this.createDate = createDate;
        this.modifyDate = modifyDate;
    }
}
