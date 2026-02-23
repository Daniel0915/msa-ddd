# Domain Policy (도메인 정책) 가이드

## 1. Domain Policy란?

특정 엔티티에 속하지 않는 **비즈니스 규칙/정책**을 캡슐화한 도메인 서비스이다.
DDD에서 말하는 "Domain Service"의 한 형태로, 설정값 기반의 비즈니스 판단 로직을 담는다.

---

## 2. Entity에 넣지 않는 이유

### 엔티티에 넣으면 안 되는 경우

```java
// 잘못된 예: Entity가 외부 설정에 의존
@Entity
public class Member {
    @Value("${custom.member.password.changeDays}")  // 엔티티에 @Value 사용 불가!
    private int changeDays;                          // JPA 엔티티는 Spring Bean이 아님
}
```

**엔티티는 Spring Bean이 아니다.** 따라서:
- `@Value`로 설정값 주입 불가
- `@Autowired`로 다른 Bean 주입 불가
- 엔티티는 순수한 도메인 모델이어야 함

### Policy로 분리하면

```java
// 올바른 예: 정책을 별도 서비스로 분리
@Service  // Spring Bean으로 등록
public class MemberPolicy {
    @Value("${custom.member.password.changeDays}")  // 설정값 주입 가능
    private int changeDays;
}
```

---

## 3. 현재 프로젝트에서의 사용

### MemberPolicy.java

```java
@Service
public class MemberPolicy {
    private static int PASSWORD_CHANGE_DAYS;

    @Value("${custom.member.password.changeDays}")
    public void setPasswordChangeDays(int days) { PASSWORD_CHANGE_DAYS = days; }

    // 비밀번호 변경 주기 (Duration)
    public Duration getNeedToChangePasswordPeriod() {
        return Duration.ofDays(PASSWORD_CHANGE_DAYS);
    }

    // 비밀번호 변경 주기 (일수)
    public int getNeedToChangePasswordDays() {
        return PASSWORD_CHANGE_DAYS;
    }

    // 비밀번호 변경이 필요한지 판단
    public boolean isNeedToChangePassword(LocalDateTime lastChangeDate) {
        if (lastChangeDate == null) return true;

        return lastChangeDate.plusDays(PASSWORD_CHANGE_DAYS)
                             .isBefore(LocalDateTime.now());
    }
}
```

### application.yml 설정

```yaml
custom:
  member:
    password:
      changeDays: 90    # 비밀번호 변경 주기: 90일
```

---

## 4. @Value와 static 필드 조합

### 왜 static 필드에 @Value를?

```java
private static int PASSWORD_CHANGE_DAYS;

@Value("${custom.member.password.changeDays}")
public void setPasswordChangeDays(int days) {
    PASSWORD_CHANGE_DAYS = days;   // setter를 통해 static 필드에 주입
}
```

**주의사항:**
- `@Value`는 필드에 직접 사용 시 **static 필드에는 주입되지 않음**
- setter 메서드에 `@Value`를 붙이면 Bean 초기화 시 호출되어 static 필드에도 값 설정 가능
- static으로 선언하면 다른 곳에서 `MemberPolicy.PASSWORD_CHANGE_DAYS`로 접근 가능 (현재는 메서드로만 접근)

---

## 5. Policy 활용 예시

### UseCase에서 Policy 사용

```java
@Service
@RequiredArgsConstructor
public class MemberGetRandomSecureTipUseCase {
    public final MemberPolicy memberPolicy;    // Policy 주입

    public String getRandomSecureTip() {
        // Policy에서 비즈니스 규칙 값을 가져와 활용
        return "비밀번호의 유효기간은 %d일 입니다."
            .formatted(memberPolicy.getNeedToChangePasswordDays());
    }
}
```

### 향후 확장 예시

```java
// 로그인 시 비밀번호 변경 확인
public void login(Member member) {
    if (memberPolicy.isNeedToChangePassword(member.getLastPasswordChangeDate())) {
        // 비밀번호 변경 페이지로 리다이렉트
    }
}
```

---

## 6. Policy vs Entity vs UseCase 비교

| 구분 | 역할 | 예시 |
|------|------|------|
| **Entity** | 자신의 상태 변경 | `member.increaseActivityScore(10)` |
| **Policy** | 외부 설정 기반 비즈니스 규칙 | `memberPolicy.isNeedToChangePassword(date)` |
| **UseCase** | 비즈니스 흐름 조합 | `memberJoinCase.join(...)` |

### 판단 기준

```
Q: 엔티티 자신의 데이터만으로 판단 가능한가?
  → YES → Entity 메서드로 구현
  → NO  → Q: 외부 설정/다른 엔티티가 필요한가?
             → YES → Policy로 분리
             → NO  → UseCase에서 처리
```

---

## 7. Domain Policy 설계 원칙

### 7.1 하나의 도메인 관심사에 집중

```java
// 좋은 예: 회원 관련 정책만
public class MemberPolicy {
    public boolean isNeedToChangePassword(...) { }
    public int getMaxLoginAttempts() { }
}

// 나쁜 예: 여러 도메인 정책 혼합
public class PolicyService {
    public boolean isNeedToChangePassword(...) { }
    public int getMaxOrderCount() { }       // 주문 도메인 정책이 섞임
}
```

### 7.2 설정값은 외부화

```yaml
# application.yml
custom:
  member:
    password:
      changeDays: 90
    maxLoginAttempts: 5
```

- 하드코딩하지 않고 **설정 파일**에서 관리
- 환경별(dev, prod)로 다른 값 적용 가능

### 7.3 순수한 판단 로직만

```java
// Policy는 판단만 한다 (상태 변경 X)
public boolean isNeedToChangePassword(LocalDateTime lastChangeDate) {
    return lastChangeDate.plusDays(PASSWORD_CHANGE_DAYS)
                         .isBefore(LocalDateTime.now());
}

// 실제 변경은 UseCase에서
public void changePasswordIfNeeded(Member member) {
    if (memberPolicy.isNeedToChangePassword(member.getLastChangeDate())) {
        member.changePassword(newPassword);  // 변경은 엔티티에서
    }
}
```

---

## 8. 핵심 정리

1. **Domain Policy**는 엔티티에 넣을 수 없는 비즈니스 규칙을 캡슐화한다
2. `@Service`로 등록하여 `@Value` 등 Spring 기능을 사용한다
3. **판단/계산만** 담당하고, 상태 변경은 엔티티나 UseCase에 위임한다
4. 설정값을 외부화하여 환경별로 유연하게 관리한다
5. DDD의 **Domain Service** 패턴에 해당한다
