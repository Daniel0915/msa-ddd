# Domain Event 실전 가이드

## 1. Domain Event란?

도메인에서 발생한 **비즈니스적으로 의미 있는 사건**을 객체로 표현한 것이다.
"회원이 가입했다", "회원 정보가 수정되었다" 같은 사건을 이벤트로 발행하여, 다른 바운디드 컨텍스트가 이에 반응할 수 있게 한다.

> 이 문서는 이벤트의 **실제 구현과 설계**에 집중한다.
> Spring Event Publisher의 기본 사용법은 `Spring-Event-Publisher-가이드.md`를 참고한다.

---

## 2. 현재 프로젝트의 이벤트 구조

### 패키지 구조

```
shared/member/                    ← 공유 계층 (다른 컨텍스트도 접근 가능)
├── dto/
│   └── MemberDto.java            ← 이벤트에 담기는 데이터
└── event/
    ├── MemberJoinedEvent.java    ← 회원가입 이벤트
    └── MemberModifiedEvent.java  ← 회원수정 이벤트
```

**핵심: 이벤트는 `shared` 패키지에 위치한다.**
- `boundedContext/member/` (내부)가 아닌 `shared/member/` (공유)에 있다
- 다른 바운디드 컨텍스트(Post, Cash, Market 등)가 이 이벤트를 구독할 수 있어야 하기 때문

---

## 3. 이벤트 클래스 설계

### MemberJoinedEvent

```java
@Getter
@AllArgsConstructor
public class MemberJoinedEvent {
    private final MemberDto member;   // Entity가 아닌 DTO를 담음!
}
```

### MemberModifiedEvent

```java
@Getter
@AllArgsConstructor
public class MemberModifiedEvent {
    private final MemberDto member;
}
```

### 왜 Entity가 아닌 DTO를 담는가?

```java
// 잘못된 예: Entity를 이벤트에 직접 담음
public class MemberJoinedEvent {
    private final Member member;      // Entity 직접 노출
    // 문제 1: 다른 컨텍스트가 Member 엔티티에 의존하게 됨
    // 문제 2: 트랜잭션 밖에서 Lazy Loading 에러 가능
    // 문제 3: 엔티티 변경이 이벤트 구독자에게 영향
}

// 올바른 예: DTO를 이벤트에 담음
public class MemberJoinedEvent {
    private final MemberDto member;   // 불변 DTO
    // 장점 1: 바운디드 컨텍스트 간 결합도 최소화
    // 장점 2: 스냅샷 데이터이므로 트랜잭션 독립적
    // 장점 3: 필요한 정보만 선별적으로 노출
}
```

### MemberDto 구조

```java
@Getter
@Builder
@AllArgsConstructor
public class MemberDto {
    private final int id;
    private final LocalDateTime createDate;
    private final LocalDateTime modifyDate;
    private final String username;
    private final String nickname;
    private final int activityScore;
    // password는 포함하지 않음! (보안)
}
```

---

## 4. 이벤트 발행 방법 (2가지)

### 4.1 UseCase에서 직접 발행

```java
// MemberJoinCase.java
@Service
@RequiredArgsConstructor
public class MemberJoinCase {
    private final EventPublisher eventPublisher;

    public RsData<Member> join(String username, String password, String nickname) {
        Member member = memberRepository.save(new Member(username, password, nickname));

        // UseCase에서 직접 이벤트 발행
        eventPublisher.publish(new MemberJoinedEvent(member.toDto()));

        return new RsData<>("201-1", "...", member);
    }
}
```

**사용 시점:** 새로운 엔티티 생성 등 UseCase 레벨의 비즈니스 이벤트

### 4.2 Entity 내부에서 발행

```java
// Member.java
@Entity
public class Member extends SourceMember {

    public int increaseActivityScore(int amount) {
        if (amount == 0) return getActivityScore();

        setActivityScore(getActivityScore() + amount);

        // 엔티티 내부에서 이벤트 발행
        publishEvent(new MemberModifiedEvent(toDto()));

        return getActivityScore();
    }
}
```

**사용 시점:** 엔티티 상태 변경 시 자동으로 알려야 하는 경우

### Entity에서 이벤트 발행이 가능한 이유

```java
// BaseEntity.java
@MappedSuperclass
public abstract class BaseEntity {
    protected void publishEvent(Object event) {
        GlobalConfig.getEventPublisher().publish(event);
    }
}

// GlobalConfig.java
@Configuration
public class GlobalConfig {
    private static EventPublisher eventPublisher;

    @Autowired
    public GlobalConfig(EventPublisher eventPublisher) {
        GlobalConfig.eventPublisher = eventPublisher;  // static으로 보관
    }

    public static EventPublisher getEventPublisher() {
        return eventPublisher;
    }
}
```

- `GlobalConfig`가 `EventPublisher`를 **static 필드**에 보관
- Entity는 Spring Bean이 아니지만, static 접근으로 이벤트 발행 가능

---

## 5. 이벤트 구독 (Listener)

### 다른 바운디드 컨텍스트에서 구독

```java
// 예: Post 컨텍스트에서 회원가입 이벤트 구독
@Service
public class PostMemberEventListener {

    @EventListener
    public void onMemberJoined(MemberJoinedEvent event) {
        // Post 컨텍스트의 회원 정보 생성
        MemberDto member = event.getMember();
        postMemberRepository.save(new PostMember(member.getId(), member.getNickname()));
    }

    @EventListener
    public void onMemberModified(MemberModifiedEvent event) {
        // Post 컨텍스트의 회원 정보 동기화
        MemberDto member = event.getMember();
        postMemberRepository.updateNickname(member.getId(), member.getNickname());
    }
}
```

---

## 6. 이벤트 흐름 다이어그램

### 회원가입 시

```
[MemberJoinCase]
    │
    ├── memberRepository.save(member)
    │
    ├── eventPublisher.publish(MemberJoinedEvent)
    │         │
    │         ├── [PostMemberEventListener]   → Post 컨텍스트 회원 생성
    │         ├── [CashMemberEventListener]   → Cash 컨텍스트 지갑 생성
    │         └── [MarketMemberEventListener] → Market 컨텍스트 회원 생성
    │
    └── return RsData
```

### 회원 수정 시

```
[Member.increaseActivityScore()]
    │
    ├── setActivityScore(...)
    │
    └── publishEvent(MemberModifiedEvent)
              │
              ├── [PostMemberEventListener]   → Post 컨텍스트 점수 동기화
              └── [CashMemberEventListener]   → Cash 컨텍스트 등급 갱신
```

---

## 7. 이벤트 설계 원칙

### 7.1 과거형으로 네이밍

```java
MemberJoinedEvent     // "회원이 가입했다" (O - 과거형)
MemberJoinEvent       // "회원이 가입한다" (X - 현재형)
```

- 이벤트는 **이미 발생한 사실**을 나타냄

### 7.2 이벤트에는 불변 DTO만

```java
// O: 불변 DTO
private final MemberDto member;

// X: 가변 Entity
private final Member member;
```

### 7.3 이벤트는 shared 패키지에

```
shared/member/event/    ← 다른 컨텍스트도 접근 가능
boundedContext/member/   ← 해당 컨텍스트만 접근
```

### 7.4 하나의 이벤트 = 하나의 사건

```java
// O: 명확한 하나의 사건
MemberJoinedEvent
MemberModifiedEvent
MemberWithdrawnEvent

// X: 모호한 이벤트
MemberEvent  // 무슨 사건인지 알 수 없음
```

---

## 8. 동기 이벤트 vs 비동기 이벤트

### 현재: 동기 이벤트

```java
eventPublisher.publish(event);
// → 모든 리스너가 같은 스레드, 같은 트랜잭션에서 실행
// → 리스너에서 예외 발생 시 전체 트랜잭션 롤백
```

### 향후: 비동기 이벤트 (MSA 확장 시)

```java
@Async
@EventListener
public void onMemberJoined(MemberJoinedEvent event) {
    // 별도 스레드에서 실행
    // 다른 마이크로서비스에 HTTP/메시지 전달
}
```

현재 모놀리식 구조에서는 **동기 이벤트**로 충분하며, MSA로 전환 시 메시지 큐(Kafka, RabbitMQ)로 교체 가능하다.

---

## 9. 핵심 정리

1. **Domain Event**는 비즈니스적으로 의미 있는 사건을 객체로 표현한다
2. 이벤트에는 **Entity가 아닌 DTO**를 담아 결합도를 최소화한다
3. 이벤트는 **shared 패키지**에 위치하여 다른 컨텍스트가 구독 가능하다
4. 발행 방법: **UseCase에서 직접** 또는 **Entity 내부**에서 가능하다
5. 바운디드 컨텍스트 간 **느슨한 결합**의 핵심 메커니즘이다
6. 이벤트 이름은 **과거형**으로, 하나의 이벤트는 **하나의 사건**을 나타낸다
