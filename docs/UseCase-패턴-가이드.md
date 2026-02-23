# UseCase 패턴 가이드

## 1. UseCase 패턴이란?

하나의 비즈니스 행위(시나리오)를 **독립된 클래스**로 분리하는 패턴이다.
"회원가입", "주문생성" 같은 각 행위가 하나의 클래스에 대응된다.

---

## 2. 왜 UseCase로 분리하는가?

### 문제: 하나의 서비스에 모든 로직이 몰림

```java
@Service
public class MemberService {
    public RsData<Member> join(...) { /* 50줄 */ }
    public void changePassword(...) { /* 30줄 */ }
    public void withdraw(...) { /* 40줄 */ }
    public void updateProfile(...) { /* 25줄 */ }
    // → 수백~수천 줄의 God Class 탄생
}
```

### 해결: 행위별로 클래스 분리

```
MemberJoinCase                     → 회원가입만 담당
MemberChangePasswordCase           → 비밀번호 변경만 담당
MemberWithdrawCase                 → 회원탈퇴만 담당
MemberGetRandomSecureTipUseCase    → 보안팁 제공만 담당
```

---

## 3. 현재 프로젝트에서의 사용

### 3.1 MemberJoinCase (회원가입 유스케이스)

```java
@Service
@RequiredArgsConstructor
public class MemberJoinCase {
    private final MemberRepository memberRepository;
    private final EventPublisher   eventPublisher;

    public RsData<Member> join(String username, String password, String nickname) {
        // 1. 중복 검사 (비즈니스 규칙)
        memberRepository.findByUsername(username).ifPresent(_ -> {
            throw new DomainException("409-1", "이미 존재하는 username 입니다.");
        });

        // 2. 엔티티 생성 및 저장
        Member member = memberRepository.save(new Member(username, password, nickname));

        // 3. 도메인 이벤트 발행
        eventPublisher.publish(new MemberJoinedEvent(member.toDto()));

        // 4. 결과 반환
        return new RsData<>("201-1", "%d번 회원이 생성되었습니다.".formatted(member.getId()), member);
    }
}
```

**핵심 포인트:**
- 하나의 public 메서드 (`join`)만 가짐
- 필요한 의존성만 주입 (`MemberRepository`, `EventPublisher`)
- 비즈니스 흐름이 명확하게 읽힘

### 3.2 MemberGetRandomSecureTipUseCase (보안팁 유스케이스)

```java
@Service
@RequiredArgsConstructor
public class MemberGetRandomSecureTipUseCase {
    public final MemberPolicy memberPolicy;

    public String getRandomSecureTip() {
        return "비밀번호의 유효기간은 %d일 입니다.".formatted(memberPolicy.getNeedToChangePasswordDays());
    }
}
```

**핵심 포인트:**
- 도메인 정책(`MemberPolicy`)을 활용
- 단순하지만 독립된 비즈니스 시나리오

### 3.3 MemberSupport (조회 전담)

```java
@Service
@RequiredArgsConstructor
public class MemberSupport {
    private final MemberRepository memberRepository;

    public long count() { return memberRepository.count(); }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    public Optional<Member> findById(int id) {
        return memberRepository.findById(id);
    }
}
```

**핵심 포인트:**
- 조회 로직만 모아둔 Support 클래스
- 여러 Facade/UseCase에서 공유 가능

---

## 4. UseCase 패턴 구조

```
[Facade]
  ├── [~Case / ~UseCase]   ← 명령(Command) 성격의 비즈니스 로직
  │     ├── MemberJoinCase
  │     ├── MemberChangePasswordCase
  │     └── ...
  │
  ├── [~Support]            ← 조회(Query) 성격의 로직
  │     └── MemberSupport
  │
  └── [~UseCase]            ← 조회이면서 비즈니스 규칙이 포함된 경우
        └── MemberGetRandomSecureTipUseCase
```

---

## 5. 네이밍 규칙

### ~Case: 상태를 변경하는 명령

```java
MemberJoinCase              // 회원가입 (Create)
MemberChangePasswordCase    // 비밀번호 변경 (Update)
MemberWithdrawCase          // 회원탈퇴 (Delete)
```

### ~UseCase: 비즈니스 규칙이 포함된 조회

```java
MemberGetRandomSecureTipUseCase   // 보안팁 조회 (비즈니스 규칙 포함)
```

### ~Support: 단순 CRUD 조회

```java
MemberSupport                // 단순 조회 모음
```

---

## 6. Facade와 UseCase의 협력

```java
@Service
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberSupport memberSupport;                     // 조회
    private final MemberJoinCase memberJoinCase;                   // 가입
    private final MemberGetRandomSecureTipUseCase secureTipUseCase; // 보안팁

    @Transactional
    public RsData<Member> join(String username, String password, String nickname) {
        return memberJoinCase.join(username, password, nickname);   // 위임
    }

    @Transactional(readOnly = true)
    public long count() {
        return memberSupport.count();                               // 위임
    }
}
```

- **Facade**: 트랜잭션 관리 + 여러 UseCase 조합
- **UseCase**: 개별 비즈니스 로직 구현
- **Support**: 재사용 가능한 조회 로직

---

## 7. UseCase 패턴의 장점

| 장점 | 설명 |
|------|------|
| 단일 책임 | 하나의 클래스 = 하나의 비즈니스 행위 |
| 가독성 | 클래스 이름만으로 기능 파악 가능 |
| 테스트 용이 | 개별 유스케이스만 단위 테스트 가능 |
| 충돌 최소화 | 팀원이 서로 다른 UseCase를 동시 작업 가능 |
| 의존성 최소화 | 필요한 의존성만 주입 |
| 확장 용이 | 새 기능 = 새 UseCase 클래스 추가 |

---

## 8. CQRS와의 관계

UseCase 패턴은 자연스럽게 **CQRS (Command Query Responsibility Segregation)** 와 연결된다.

```
Command (명령) → ~Case         → 상태 변경 (Create, Update, Delete)
Query   (조회) → ~Support      → 상태 조회 (Read)
                 ~UseCase      → 비즈니스 규칙 포함 조회
```

---

## 9. 핵심 정리

1. **하나의 비즈니스 행위 = 하나의 UseCase 클래스**
2. UseCase는 **Facade를 통해** 외부에 노출된다
3. 명령은 `~Case`, 조회는 `~Support`, 규칙 포함 조회는 `~UseCase`로 네이밍한다
4. God Class 방지와 **팀 협업 효율**을 높인다
5. DDD의 Application Service 계층에 해당한다
