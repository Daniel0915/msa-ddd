# DDD 바운디드 컨텍스트 가이드

## 1. DDD (Domain-Driven Design)란?

소프트웨어의 복잡성을 **도메인(비즈니스) 중심으로 설계**하여 해결하는 방법론이다.
기술이 아닌 비즈니스 로직이 코드의 중심이 된다.

---

## 2. 핵심 개념

### 2.1 바운디드 컨텍스트 (Bounded Context)

하나의 도메인 모델이 적용되는 **경계**이다. 같은 용어라도 컨텍스트마다 의미가 다를 수 있다.

```
예: "Member"
├── 회원 컨텍스트: 로그인, 프로필 관리
├── 주문 컨텍스트: 주문자 정보 (이름, 주소만 필요)
└── 결제 컨텍스트: 결제 수단, 결제 이력
```

### 2.2 현재 프로젝트 구조

```
src/main/java/com/back/
├── boundedContext/              ← 바운디드 컨텍스트 (도메인별 분리)
│   └── member/
│       └── domain/
│           ├── Member.java          ← 엔티티 (Aggregate Root)
│           ├── BaseMember.java      ← 공통 필드
│           └── SourceMember.java    ← 확장 포인트
│
├── shared/                      ← 컨텍스트 간 공유 계층
│   └── member/
│       ├── dto/
│       │   └── MemberDto.java       ← 외부 노출용 DTO
│       └── event/
│           └── MemberModifiedEvent.java  ← 도메인 이벤트
│
└── global/                      ← 전역 인프라
    ├── jpa/entity/
    │   ├── BaseEntity.java
    │   └── HasModelTypeCode.java
    ├── eventPublisher/
    │   └── EventPublisher.java
    └── global/
        └── GlobalConfig.java
```

---

## 3. 전략적 설계

### 3.1 패키지 구조 설계 원칙

```
boundedContext/
├── member/                  ← 바운디드 컨텍스트
│   ├── domain/              ← 도메인 계층 (엔티티, VO, Repository 인터페이스)
│   ├── application/         ← 응용 계층 (서비스, 유스케이스)
│   ├── infrastructure/      ← 인프라 계층 (Repository 구현체, 외부 API)
│   └── presentation/        ← 표현 계층 (Controller)
│
├── order/                   ← 다른 바운디드 컨텍스트
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── presentation/
```

### 3.2 컨텍스트 간 통신

바운디드 컨텍스트끼리 직접 참조하면 안 된다. **이벤트 또는 shared 계층**을 통해 통신한다.

```
[Member 컨텍스트]                    [Order 컨텍스트]
      │                                    │
      │ publishEvent(                      │
      │   MemberModifiedEvent)             │
      │         │                          │
      └─────────┼──── shared 계층 ─────────┘
                │
                └→ @EventListener로 수신
```

현재 프로젝트에서의 구현:

```java
// shared/member/event/MemberModifiedEvent.java
// 컨텍스트 간 공유되는 이벤트 - DTO를 포함
@Getter
@AllArgsConstructor
public class MemberModifiedEvent {
    private final MemberDto member;  // 엔티티가 아닌 DTO로 전달!
}
```

---

## 4. 전술적 설계

### 4.1 엔티티 (Entity)

고유한 식별자(ID)를 가지며 생명주기 동안 연속성을 가지는 객체.

```java
@Entity
public class Member extends SourceMember {
    // id로 식별 → 같은 id면 같은 Member
}
```

### 4.2 값 객체 (Value Object)

식별자 없이 속성 값으로만 동등성을 판단하는 객체.

```java
// 예시 - 현재 프로젝트에 추가 가능
@Embeddable
public class Address {
    private String city;
    private String street;
    private String zipCode;
    // city, street, zipCode 모두 같으면 같은 Address
}
```

### 4.3 애그리거트 (Aggregate)

관련 엔티티와 VO를 하나의 트랜잭션 단위로 묶은 것. **Aggregate Root**를 통해서만 내부 객체에 접근한다.

```
[Member Aggregate]
├── Member (Aggregate Root) ← 외부에서는 여기로만 접근
├── MemberProfile (Entity)
└── Address (Value Object)
```

규칙:
- 외부에서 Aggregate 내부 객체를 직접 참조하지 않는다
- 하나의 트랜잭션에서 하나의 Aggregate만 수정한다
- 다른 Aggregate 참조는 ID로만 한다

### 4.4 도메인 이벤트 (Domain Event)

도메인에서 발생한 사건을 표현하는 객체. 과거형으로 명명한다.

```java
// 현재 프로젝트의 도메인 이벤트
MemberModifiedEvent   // "회원이 수정되었다"

// 추가 예시
OrderCreatedEvent     // "주문이 생성되었다"
PaymentCompletedEvent // "결제가 완료되었다"
```

### 4.5 도메인 서비스 (Domain Service)

하나의 엔티티에 속하지 않는 도메인 로직을 처리한다.

```java
// 예: 두 Member 간의 로직은 어느 Member에도 넣기 애매함
@Service
public class MemberTransferService {
    public void transferScore(Member from, Member to, int score) {
        from.decreaseScore(score);
        to.increaseScore(score);
    }
}
```

---

## 5. Shared 계층의 역할

### 5.1 왜 필요한가?

```
[문제] Member 컨텍스트의 Member 엔티티를 Order 컨텍스트에서 직접 참조하면?
→ 컨텍스트 간 강결합 발생! Member 변경 시 Order도 영향 받음

[해결] shared 계층에 DTO와 이벤트를 두고 간접 통신
→ 컨텍스트는 서로의 내부 구현을 모른다
```

### 5.2 현재 프로젝트의 shared

```java
// shared/member/dto/MemberDto.java
// 엔티티 대신 외부에 노출하는 불변 DTO
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
    // password 미포함 → 외부에 민감 정보 노출 방지
}
```

---

## 6. DDD vs 기존 개발 비교

| 항목 | 기존 (계층형) | DDD |
|---|---|---|
| 패키지 구조 | `controller/`, `service/`, `repository/` | `boundedContext/member/domain/` |
| 비즈니스 로직 위치 | Service 클래스 | Entity/Domain Service |
| 엔티티 역할 | 단순 데이터 보관 (빈약한 도메인 모델) | 비즈니스 로직 포함 (풍부한 도메인 모델) |
| 모듈 간 통신 | 직접 Service 호출 | 이벤트/DTO를 통한 간접 통신 |
| 설계 기준 | 기술 (DB, API) | 비즈니스 도메인 |

---

## 7. 핵심 정리

| 개념 | 설명 |
|---|---|
| 바운디드 컨텍스트 | 도메인 모델의 경계, 컨텍스트마다 독립적 |
| 애그리거트 | 트랜잭션 일관성 단위, Root를 통해서만 접근 |
| 도메인 이벤트 | 컨텍스트 간 느슨한 결합을 위한 통신 수단 |
| Shared 계층 | DTO/이벤트로 컨텍스트 간 간접 통신 |
| 풍부한 도메인 모델 | 엔티티가 비즈니스 로직을 갖는 것 |
