# Facade 패턴 가이드

## 1. Facade 패턴이란?

복잡한 서브시스템을 **하나의 통합 인터페이스**로 감싸서, 외부(컨트롤러 등)에서 간단하게 사용할 수 있도록 하는 디자인 패턴이다.
클라이언트는 내부 구현 세부사항을 알 필요 없이 Facade만 호출하면 된다.

---

## 2. 왜 Facade가 필요한가?

### 문제: 컨트롤러가 여러 서비스를 직접 호출

```java
// Facade 없이 - 컨트롤러가 내부 구조를 알아야 함
@RestController
public class MemberController {
    private final MemberJoinCase memberJoinCase;
    private final MemberSupport memberSupport;
    private final MemberGetRandomSecureTipUseCase secureTipUseCase;

    // 컨트롤러가 어떤 서비스를 호출해야 하는지 모두 알아야 함
    // 서비스가 추가/변경될 때마다 컨트롤러도 수정 필요
}
```

### 해결: Facade로 통합

```java
// Facade 사용 - 컨트롤러는 Facade만 알면 됨
@RestController
public class MemberController {
    private final MemberFacade memberFacade;  // 하나만 의존
}
```

---

## 3. 현재 프로젝트에서의 사용

### 구조도

```
[Controller] → [MemberFacade] → [MemberJoinCase]           → [MemberRepository]
                               → [MemberSupport]            → [MemberRepository]
                               → [MemberGetRandomSecureTipUseCase] → [MemberPolicy]
```

### MemberFacade.java

```java
@Service
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberSupport memberSupport;                                   // 조회 전담
    private final MemberJoinCase memberJoinCase;                                 // 가입 유스케이스
    private final MemberGetRandomSecureTipUseCase memberGetRandomSecureTipUseCase; // 보안팁 유스케이스

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
```

---

## 4. Facade 내부 서비스 역할 분리

| 클래스 | 역할 | 설명 |
|--------|------|------|
| `MemberFacade` | 오케스트레이터 | 외부에 단일 진입점 제공, 트랜잭션 관리 |
| `MemberJoinCase` | 유스케이스 | 회원가입 비즈니스 로직 |
| `MemberSupport` | 조회 전담 | 읽기 전용 쿼리 처리 |
| `MemberGetRandomSecureTipUseCase` | 유스케이스 | 보안팁 제공 |

---

## 5. Facade 패턴의 장점

### 5.1 단일 진입점 (Single Entry Point)

```
컨트롤러 → Facade(1개) → 서비스들(N개)
```

- 외부에서는 **Facade만 주입**하면 모든 기능 사용 가능
- 내부 서비스 구조가 바뀌어도 Facade 인터페이스가 유지되면 외부 영향 없음

### 5.2 트랜잭션 경계 관리

```java
@Transactional                    // Facade에서 트랜잭션 시작
public RsData<Member> join(...) {
    return memberJoinCase.join(...);  // 내부 서비스는 같은 트랜잭션에 참여
}

@Transactional(readOnly = true)   // 읽기 전용 최적화
public long count() { ... }
```

- Facade에서 트랜잭션 범위를 결정
- 여러 서비스를 하나의 트랜잭션으로 묶을 수 있음

### 5.3 관심사 분리

```
MemberJoinCase    → "가입"만 책임
MemberSupport     → "조회"만 책임
MemberFacade      → "조합"만 책임
```

- 각 서비스는 하나의 책임만 가짐 (Single Responsibility)
- 테스트할 때 개별 서비스만 단위 테스트 가능

---

## 6. Facade vs Service 비교

### 일반적인 Service 패턴

```java
// 모든 로직이 하나의 서비스에 몰림
@Service
public class MemberService {
    public RsData<Member> join(...) { /* 가입 로직 */ }
    public long count() { /* 조회 로직 */ }
    public Optional<Member> findByUsername(...) { /* 조회 로직 */ }
    public String getRandomSecureTip() { /* 보안팁 로직 */ }
    // 기능이 늘어날수록 클래스가 비대해짐 (God Class)
}
```

### Facade + UseCase 패턴 (현재 프로젝트)

```java
// Facade: 조합만 담당
@Service
public class MemberFacade {
    private final MemberJoinCase memberJoinCase;
    private final MemberSupport memberSupport;
    // 각 기능은 별도 클래스에 위임
}

// UseCase: 개별 비즈니스 로직 담당
@Service
public class MemberJoinCase {
    public RsData<Member> join(...) { /* 가입 로직만 */ }
}
```

---

## 7. 네이밍 컨벤션

| 접미사 | 역할 | 예시 |
|--------|------|------|
| `~Facade` | 외부 진입점, 오케스트레이터 | `MemberFacade` |
| `~Case` / `~UseCase` | 개별 비즈니스 유스케이스 | `MemberJoinCase` |
| `~Support` | 조회/보조 기능 전담 | `MemberSupport` |
| `~Policy` | 도메인 정책/규칙 | `MemberPolicy` |

---

## 8. 핵심 정리

1. **Facade**는 여러 서비스를 하나의 통합 인터페이스로 묶는 패턴이다
2. 컨트롤러는 Facade만 의존하여 **결합도를 낮춘다**
3. **트랜잭션 경계**를 Facade에서 관리한다
4. 내부 서비스는 역할별로 분리하여 **단일 책임 원칙**을 지킨다
5. DDD에서 Application Layer의 역할과 일치한다