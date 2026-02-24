# @TransactionalEventListener + REQUIRES_NEW 트랜잭션 분리 가이드

> 이 문서는 `AFTER_COMMIT` 리스너에서 왜 `REQUIRES_NEW`가 필요한지,
> 트랜잭션을 분리하는 이유와 선택 기준에 집중한다.
> Spring Event의 기본 사용법은 `Spring-Event-Publisher-가이드.md`를 참고한다.

---

## 1. AFTER_COMMIT 리스너에서 DB 작업이 안 되는 이유

```
트랜잭션 A 시작
  → member 저장
  → eventPublisher.publish(MemberJoinedEvent)
  → 트랜잭션 A 커밋 완료 ✅  ← DB 커넥션 반환, 트랜잭션 종료
  ─────────────────────────────
  → 리스너 실행 시점              ← 트랜잭션이 없는 상태
```

`AFTER_COMMIT`은 말 그대로 **커밋이 완료된 후**에 실행된다.
이 시점에서 원본 트랜잭션은 이미 종료되었으므로, 리스너에서 DB 작업을 하면 다음 문제가 발생한다.

- `TransactionRequiredException` 발생
- 또는 변경사항이 DB에 반영되지 않음

**이미 끝난 트랜잭션에 다시 참여할 수 없다.** 이것이 핵심이다.

---

## 2. REQUIRES_NEW로 해결

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleMemberJoinEvent(MemberJoinedEvent event) {
    // 새로운 트랜잭션 B에서 실행
    pointService.grantSignupBonus(event.getMember().getId());
}
```

```
트랜잭션 A 시작
  → member 저장
  → event 발행
  → 트랜잭션 A 커밋 ✅
─────────────────────────── 트랜잭션 A 종료
트랜잭션 B 시작 (REQUIRES_NEW)
  → 포인트 지급, 이메일 발송 등
  → 트랜잭션 B 커밋 ✅
─────────────────────────── 트랜잭션 B 종료
```

`REQUIRES_NEW`는 기존 트랜잭션과 무관하게 **완전히 새로운 트랜잭션**을 생성한다.

---

## 3. 트랜잭션을 같이 묶고 싶다면?

같은 트랜잭션에서 실행되길 원하는 경우 두 가지 방법이 있다.

### 방법 1: @EventListener (일반 이벤트 리스너)

```java
@EventListener
public void handleMemberJoinEvent(MemberJoinedEvent event) {
    // 트랜잭션 A 안에서 실행됨
    // 여기서 실패하면 회원가입도 함께 롤백
}
```

- 같은 스레드, 같은 트랜잭션에서 동기 실행
- 리스너 예외 → 원본 트랜잭션 전체 롤백

### 방법 2: BEFORE_COMMIT phase

```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void handleMemberJoinEvent(MemberJoinedEvent event) {
    // 트랜잭션 A 커밋 직전에 실행
    // 실패 시 트랜잭션 A 전체 롤백
}
```

- 커밋 직전에 실행되므로 같은 트랜잭션 안에서 동작

---

## 4. 분리 vs 통합, 언제 무엇을 쓰는가?

### 비교표

| | 같은 트랜잭션 | 분리된 트랜잭션 |
|---|---|---|
| 방식 | `@EventListener` 또는 `BEFORE_COMMIT` | `AFTER_COMMIT` + `REQUIRES_NEW` |
| 리스너 실패 시 | **원본도 함께 롤백** | 원본은 이미 커밋, 리스너만 실패 |
| 원본 커밋 실패 시 | 리스너도 실행 안 됨 | 리스너도 실행 안 됨 |
| 데이터 일관성 | 강한 일관성 (All or Nothing) | 최종적 일관성 (Eventual Consistency) |

### 판단 기준

**같은 트랜잭션으로 묶어야 하는 경우:**
- 리스너 작업이 실패하면 원본 작업도 무의미한 경우
- 예: 주문 생성 시 재고 차감이 반드시 함께 이루어져야 하는 경우

**분리해야 하는 경우:**
- 부가 작업 실패가 핵심 작업에 영향을 주면 안 되는 경우
- 예: 회원가입 후 환영 이메일 발송 — 이메일 서버 장애로 회원가입이 실패하면 안 됨
- 예: 주문 완료 후 알림 발송 — 알림 실패로 주문이 취소되면 안 됨

---

## 5. 주의사항

### 5.1 REQUIRES_NEW 없이 AFTER_COMMIT 리스너에서 DB 작업

```java
// 잘못된 예
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(MemberJoinedEvent event) {
    repository.save(something);  // 트랜잭션이 없어 실패하거나 반영 안 됨
}
```

### 5.2 리스너 실패 시 이벤트 유실

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handle(MemberJoinedEvent event) {
    externalApi.call();  // 여기서 예외 발생 시 이벤트는 유실됨
}
```

- 원본 트랜잭션은 이미 커밋되었으므로 롤백 불가
- 리스너의 작업만 롤백되고, 재시도 메커니즘이 없으면 이벤트가 유실됨
- 중요한 작업이라면 **Transactional Outbox 패턴** 또는 **메시지 큐(Kafka, RabbitMQ)** 도입을 고려

### 5.3 Propagation 종류 참고

| Propagation | 동작 |
|---|---|
| `REQUIRED` (기본값) | 기존 트랜잭션 참여, 없으면 새로 생성 |
| `REQUIRES_NEW` | 항상 새 트랜잭션 생성 (기존 트랜잭션 일시 중단) |
| `SUPPORTS` | 기존 트랜잭션 있으면 참여, 없으면 트랜잭션 없이 실행 |
| `NOT_SUPPORTED` | 트랜잭션 없이 실행 (기존 트랜잭션 일시 중단) |

`AFTER_COMMIT` 리스너에서는 기존 트랜잭션이 이미 종료된 상태이므로,
`REQUIRED`를 써도 새 트랜잭션이 생성되긴 하지만, **의도를 명확히 하기 위해 `REQUIRES_NEW`를 명시하는 것이 관례**이다.

---

## 6. 핵심 정리

1. `AFTER_COMMIT` 시점에서 원본 트랜잭션은 **이미 종료**되었다
2. 따라서 리스너에서 DB 작업을 하려면 `REQUIRES_NEW`로 **새 트랜잭션을 생성**해야 한다
3. 트랜잭션을 같이 묶고 싶다면 `@EventListener` 또는 `BEFORE_COMMIT`을 사용한다
4. 분리 여부는 **부가 작업 실패가 핵심 작업에 영향을 줘도 되는가?** 로 판단한다
5. 분리된 트랜잭션에서는 이벤트 유실 가능성이 있으므로, 중요도에 따라 보완 전략을 고려한다
