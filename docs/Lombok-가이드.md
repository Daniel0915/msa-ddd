# Lombok 가이드

## 1. Lombok이란?

Java의 반복적인 보일러플레이트 코드(getter, setter, 생성자 등)를 **어노테이션으로 자동 생성**해주는 라이브러리이다.
컴파일 시점에 코드를 생성하므로 런타임 성능에 영향 없다.

---

## 2. 현재 프로젝트에서 사용 중인 Lombok

### build.gradle.kts 설정

```kotlin
compileOnly("org.projectlombok:lombok")           // 컴파일 시에만 사용
annotationProcessor("org.projectlombok:lombok")    // 어노테이션 프로세서 등록
```

---

## 3. 핵심 어노테이션

### 3.1 @Getter / @Setter

```java
// Lombok 없이
public class Member {
    private String username;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}

// Lombok 사용
@Getter
@Setter
public class Member {
    private String username;
    // getUsername(), setUsername() 자동 생성
}
```

**접근 수준 제어 (현재 프로젝트에서 사용 중):**

```java
// BaseMember.java
@Getter
@Setter(value = AccessLevel.PROTECTED)  // setter는 protected로 제한
public abstract class BaseMember extends BaseEntity {
    private String username;
    // getUsername() → public
    // setUsername() → protected (외부에서 직접 수정 불가)
}
```

> DDD에서 엔티티의 상태 변경은 도메인 메서드를 통해야 하므로 Setter를 제한하는 것이 중요하다.

### 3.2 @AllArgsConstructor / @NoArgsConstructor

```java
// 현재 프로젝트: MemberDto.java
@AllArgsConstructor  // 모든 필드를 받는 생성자
public class MemberDto {
    private final int id;
    private final String username;
    // → new MemberDto(1, "peter") 가능
}

// 현재 프로젝트: BaseMember.java
@NoArgsConstructor   // 파라미터 없는 기본 생성자
public abstract class BaseMember extends BaseEntity {
    // → JPA 엔티티는 기본 생성자 필수!
}
```

**JPA 엔티티에 @NoArgsConstructor가 필요한 이유:**
- JPA는 리플렉션으로 객체를 생성하므로 기본 생성자가 반드시 필요
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 권장

### 3.3 @RequiredArgsConstructor

`final` 필드만 받는 생성자를 생성한다. **Spring 생성자 주입**에 핵심적으로 사용.

```java
// Lombok 없이
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }
}

// Lombok 사용
@Service
@RequiredArgsConstructor  // final 필드에 대한 생성자 자동 생성
public class OrderService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    // 생성자 자동 생성 → Spring이 자동 주입 (생성자가 1개면 @Autowired 생략 가능)
}
```

현재 프로젝트:
```java
// EventPublisher.java
@Service
@RequiredArgsConstructor
public class EventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;
}
```

### 3.4 @Builder

빌더 패턴을 자동 생성한다.

```java
// 현재 프로젝트: MemberDto.java
@AllArgsConstructor
@Getter
@Builder
public class MemberDto {
    private final int           id;
    private final LocalDateTime createDate;
    private final LocalDateTime modifyDate;
    private final String username;
    private final String nickname;
    private final int activityScore;
}

// 사용
MemberDto dto = MemberDto.builder()
    .id(1)
    .username("peter")
    .nickname("피터")
    .activityScore(100)
    .createDate(LocalDateTime.now())
    .modifyDate(LocalDateTime.now())
    .build();
```

장점:
- 필드가 많을 때 가독성 향상
- 선택적 필드 지정 가능
- 불변 객체 생성에 적합

---

## 4. 자주 사용하는 추가 어노테이션

### 4.1 @ToString

```java
@ToString
public class Member {
    private String username;
    private String nickname;
    // toString() → "Member(username=peter, nickname=피터)"
}

@ToString(exclude = "password")  // 민감 정보 제외
public class Member {
    private String username;
    private String password;
}
```

### 4.2 @EqualsAndHashCode

```java
@EqualsAndHashCode(of = "id")  // id만으로 동등성 판단
public class Member {
    private int id;
    private String username;
}
```

### 4.3 @Data

`@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`를 한번에 적용.

```java
@Data
public class MemberForm {
    private String username;
    private String password;
}
```

> **주의: 엔티티에 @Data 사용 금지.** Setter가 public으로 열리고, 양방향 연관관계에서 toString/hashCode 무한루프 발생 가능.

### 4.4 @Slf4j

로깅 객체 자동 생성.

```java
@Slf4j
@Service
public class MemberService {
    public void createMember() {
        log.info("회원 생성");           // Logger 객체 직접 선언 불필요
        log.error("에러 발생: {}", e.getMessage());
    }
}
```

---

## 5. DDD/JPA에서의 Lombok 사용 가이드

| 대상 | 권장 | 비권장 |
|---|---|---|
| **Entity** | `@Getter`, `@NoArgsConstructor(PROTECTED)` | `@Data`, `@Setter` |
| **DTO** | `@Getter`, `@Builder`, `@AllArgsConstructor` | `@Setter` (불변이 좋음) |
| **Service** | `@RequiredArgsConstructor`, `@Slf4j` | - |
| **Value Object** | `@Getter`, `@EqualsAndHashCode`, `@AllArgsConstructor` | `@Setter` |

---

## 6. 핵심 정리

| 어노테이션 | 생성하는 것 | 주 사용처 |
|---|---|---|
| `@Getter` | getter 메서드 | 모든 클래스 |
| `@Setter(AccessLevel.PROTECTED)` | 접근 제한된 setter | 엔티티 (DDD 캡슐화) |
| `@NoArgsConstructor` | 기본 생성자 | JPA 엔티티 (필수) |
| `@RequiredArgsConstructor` | final 필드 생성자 | Spring Bean (생성자 주입) |
| `@AllArgsConstructor` | 전체 필드 생성자 | DTO |
| `@Builder` | 빌더 패턴 | DTO, 팩토리 |
| `@Slf4j` | Logger 객체 | Service, Controller |
