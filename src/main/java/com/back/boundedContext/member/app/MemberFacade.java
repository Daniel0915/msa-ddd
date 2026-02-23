package com.back.boundedContext.member.app;

import com.back.boundedContext.member.domain.Member;
import com.back.global.reData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberSupport memberSupport;
    private final MemberJoinCase memberJoinCase;
    private final MemberGetRandomSecureTipUseCase memberGetRandomSecureTipUseCase;

    @Transactional
    public RsData<Member> join(String username, String password, String nickname) {
        return memberJoinCase.join(username, password, nickname);
    }

    public String getRandomSecureTip() {
        return memberGetRandomSecureTipUseCase.getRandomSecureTip();
    }

    @Transactional(readOnly = true)
    public long count() { return memberSupport.count(); }

    @Transactional(readOnly = true)
    public Optional<Member> findByUsername(String username) {
        return memberSupport.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<Member> findById(int id) {
        return memberSupport.findById(id);
    }
}
