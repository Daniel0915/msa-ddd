# Spring ApplicationEventPublisher & TransactionalEventPublisher 가이드

## 1. 개요

Spring의 이벤트 시스템은 **발행-구독(Pub-Sub) 패턴**을 구현한다.
DDD에서 바운디드 컨텍스트 간 결합도를 낮추면서 통신할 때 핵심적으로 사용된다.

---

## 2. ApplicationEventPublisher

### 2.1 역할

등록된 모든 리스너에게 이벤트를 전달하는 인터페이스.

```java
@FunctionalInterface
public interface ApplicationEventPublisher {
    default void publishEvent(ApplicationEvent event) {
        publishEvent((Object) event);
    }
    void publishEvent(Object event);
}
```

### 2.2 이벤트 종류

| 종류 | 예시 | 설명 |
|---|---|---|
| 프레임워크 이벤트 | `ContextRefreshedEvent`, `ContextClosedEvent` | Spring 내부 라이프사이클 이벤트 |
| 도메인 이벤트 | `OrderCreatedEvent`, `PaymentCompletedEvent` | 직접 정의하는 비즈니스 이벤트 |

### 2.3 기본 사용법

**이벤트 정의**

```java
public class OrderCreatedEvent {
    private final Long orderId;
    private final Long memberId;
    private final LocalDateTime createdAt;

    public OrderCreatedEvent(Long orderId, Long memberId) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.createdAt = LocalDateTime.now();
    }
    // getters
}
```

**이벤트 발행**

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createOrder(OrderCommand command) {
        Order order = Order.create(command);
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), command.getMemberId()));
    }
}
```

**이벤트 수신**

```java
@Component
public class OrderEventListener {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 동기 실행, 같은 트랜잭션 공유
        log.info("주문 생성됨: {}", event.getOrderId());
    }
}
```

---

## 3. 트랜잭션과 이벤트

### 3.1 동기 @EventListener

```
Thread-1:  [서비스 메서드] → [publishEvent] → [리스너 실행]
            └──────────── 하나의 트랜잭션 ──────────────┘
```

- 같은 스레드, 같은 트랜잭션 공유
- 리스너에서 예외 발생 시 전체 롤백

### 3.2 @TransactionalEventListener

트랜잭션 상태에 따라 리스너 실행 시점을 제어한다.

```java
@Component
public class OrderEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 트랜잭션 커밋 성공 후에만 실행
        notificationService.sendOrderConfirmation(event.getOrderId());
    }
}
```

| TransactionPhase | 실행 시점 | 사용 예시 |
|---|---|---|
| `AFTER_COMMIT` | 커밋 성공 후 | 알림 발송, 외부 API 호출 |
| `AFTER_ROLLBACK` | 롤백 후 | 실패 로깅, 보상 처리 |
| `AFTER_COMPLETION` | 커밋/롤백 상관없이 완료 후 | 리소스 정리 |
| `BEFORE_COMMIT` | 커밋 직전 | 최종 검증 |

> **주의**: `AFTER_COMMIT` 리스너에서 예외가 발생해도 원본 트랜잭션은 이미 커밋된 상태이므로 롤백되지 않는다.

### 3.3 @Async + 이벤트

```
Thread-1:  [서비스 메서드] → [publishEvent] → 커밋
Thread-2:                     [리스너 실행]  ← 별도 트랜잭션
```

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional  // 새로운 트랜잭션 생성
public void handleOrderCreated(OrderCreatedEvent event) {
    // 1. 원본 트랜잭션 커밋 확인 (AFTER_COMMIT)
    // 2. 비동기 스레드에서 새 트랜잭션으로 실행
    paymentService.process(event.getOrderId());
}
```

**@Async 사용 시 주의사항:**
- ThreadLocal 기반의 원본 트랜잭션은 공유되지 않음
- 리스너에서 DB 작업이 필요하면 `@Transactional`로 새 트랜잭션 생성 필요
- `@EnableAsync` 설정 필수

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    // 필요 시 커스텀 Executor 설정
}
```

---

## 4. 리액티브 환경에서의 이벤트

### 4.1 문제점

리액티브(WebFlux)에서는 하나의 요청이 여러 스레드를 넘나든다.
Spring의 트랜잭션은 **ThreadLocal 기반**이므로 리액티브 스레드 전환 시 트랜잭션 컨텍스트가 유실된다.

```
Thread-1: [서비스] → publishEvent ──┐
Thread-3:                           └→ [리스너] ← ThreadLocal 비어있음!
```

### 4.2 리액티브에서 기본 이벤트 발행

```java
// 단순 hand-off (트랜잭션 컨텍스트 없음)
Mono.fromRunnable(() -> eventPublisher.publishEvent(new OrderCreatedEvent(orderId)))
    .then(...)
```

이 방식은 트랜잭션 컨텍스트를 전달하지 않는다.
이벤트에 필요한 **모든 상태를 이벤트 객체에 담아야** 한다.

### 4.3 TransactionalEventPublisher

리액티브 환경에서 **트랜잭션 컨텍스트를 이벤트와 함께 전달**하기 위한 클래스.

```java
org.springframework.transaction.reactive.TransactionalEventPublisher
```

**사용법:**

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final TransactionalEventPublisher transactionalEventPublisher;

    public Mono<Order> createOrder(OrderCommand command) {
        return orderRepository.save(Order.create(command))
            .flatMap(order ->
                transactionalEventPublisher
                    .publishEvent(savedOrder -> new OrderCreatedEvent(order.getId()))
                    .thenReturn(order)
            );
    }
}
```

**핵심 시그니처:**

```java
public class TransactionalEventPublisher {
    // Function을 받아서 현재 트랜잭션 컨텍스트와 함께 이벤트 발행
    public Mono<Void> publishEvent(Function<Object, Object> eventCreator);
}
```

**동작 원리:**

```
Reactor Context: [트랜잭션 정보]
        │
        ├→ TransactionalEventPublisher.publishEvent()
        │     └→ Reactor Context에서 트랜잭션 정보 추출
        │     └→ 이벤트 생성 함수 실행
        │     └→ 트랜잭션 컨텍스트와 함께 이벤트 발행
        │
        └→ 리스너가 트랜잭션 컨텍스트를 받아서 처리
```

- ThreadLocal 대신 **Reactor Context**를 통해 트랜잭션 전파
- `@Transactional` 리액티브 트랜잭션 내에서 사용해야 함

### 4.4 리액티브 vs MVC 트랜잭션 비교

| 항목 | Spring MVC | Spring WebFlux |
|---|---|---|
| 트랜잭션 저장소 | ThreadLocal | Reactor Context |
| 이벤트 발행 | `ApplicationEventPublisher` | `TransactionalEventPublisher` |
| 트랜잭션 전파 | 자동 (같은 스레드) | 명시적 (Reactor Context) |
| 비동기 처리 | `@Async` + 새 트랜잭션 | Reactor 스케줄러 |

---

## 5. 전체 비교 요약

| 방식 | 트랜잭션 공유 | 실행 스레드 | 사용 환경 |
|---|---|---|---|
| `@EventListener` | O (원본 공유) | 동일 스레드 | MVC |
| `@TransactionalEventListener` | X (커밋 후 실행) | 동일 스레드 | MVC |
| `@Async` + `@TransactionalEventListener` | X (새 트랜잭션) | 별도 스레드 | MVC |
| `Mono.fromRunnable(publishEvent)` | X | 리액티브 스레드 | WebFlux |
| `TransactionalEventPublisher` | O (Reactor Context) | 리액티브 스레드 | WebFlux |

---

## 6. DDD에서의 권장 패턴

### 6.1 도메인 이벤트 발행 (MVC 기준)

```java
// 1. 도메인 이벤트를 엔티티 내부에서 등록
@Entity
public class Order {
    @Transient
    private List<Object> domainEvents = new ArrayList<>();

    public void complete() {
        this.status = OrderStatus.COMPLETED;
        domainEvents.add(new OrderCompletedEvent(this.id));
    }

    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}

// 2. 서비스에서 이벤트 발행
@Service
@RequiredArgsConstructor
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.complete();
        order.getDomainEvents().forEach(eventPublisher::publishEvent);
        order.clearDomainEvents();
    }
}

// 3. 다른 바운디드 컨텍스트에서 수신
@Component
public class PaymentEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        // 결제 처리 등
    }
}
```

### 6.2 Spring Data의 AbstractAggregateRoot 활용

```java
@Entity
public class Order extends AbstractAggregateRoot<Order> {

    public void complete() {
        this.status = OrderStatus.COMPLETED;
        registerEvent(new OrderCompletedEvent(this.id));
        // save() 호출 시 자동으로 이벤트 발행 & 클리어
    }
}
```

`AbstractAggregateRoot`를 상속하면 `repository.save()` 시점에 등록된 이벤트가 자동 발행된다.