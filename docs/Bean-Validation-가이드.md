# Bean Validation 가이드

## 1. Bean Validation이란?

Java 객체의 필드 값을 **어노테이션으로 검증**하는 표준 스펙이다.
`spring-boot-starter-validation`이 Hibernate Validator 구현체를 포함한다.

```
사용자 입력 → Controller → @Valid로 검증 → 통과 시 Service로 전달
                                         → 실패 시 에러 응답
```

---

## 2. 핵심 어노테이션

### 2.1 문자열 검증

```java
public class MemberCreateRequest {

    @NotNull                    // null 불가
    @NotEmpty                   // null, "" 불가
    @NotBlank                   // null, "", "   " 불가 (공백만 있는 것도 불가)
    private String username;

    @Size(min = 2, max = 20)    // 길이 제한
    private String nickname;

    @Pattern(regexp = "^[a-zA-Z0-9]+$")  // 정규식 매칭
    private String password;

    @Email                      // 이메일 형식 검증
    private String email;
}
```

### 2.2 숫자 검증

```java
public class ScoreUpdateRequest {

    @Min(0)                     // 최소값
    @Max(10000)                 // 최대값
    private int activityScore;

    @Positive                   // 양수만
    @PositiveOrZero             // 0 또는 양수
    private int point;

    @DecimalMin("0.0")          // 소수점 최소값
    @DecimalMax("100.0")        // 소수점 최대값
    private BigDecimal rate;
}
```

### 2.3 기타

```java
public class EventRequest {

    @Past                       // 과거 날짜만
    @PastOrPresent              // 과거 또는 현재
    private LocalDateTime startDate;

    @Future                     // 미래 날짜만
    private LocalDateTime endDate;

    @AssertTrue                 // true여야 함
    private boolean agreeTerms;
}
```

---

## 3. 사용 방법

### 3.1 Controller에서 검증

```java
@RestController
@RequiredArgsConstructor
public class MemberController {

    @PostMapping("/members")
    public ResponseEntity<?> create(@RequestBody @Valid MemberCreateRequest request) {
        // @Valid가 request 객체의 어노테이션을 검증
        // 검증 실패 시 MethodArgumentNotValidException 발생
        memberService.create(request);
        return ResponseEntity.ok().build();
    }
}
```

### 3.2 요청 DTO에 검증 규칙 정의

```java
public class MemberCreateRequest {

    @NotBlank(message = "사용자명은 필수입니다")
    @Size(min = 3, max = 20, message = "사용자명은 3~20자여야 합니다")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다")
    private String nickname;
}
```

### 3.3 에러 처리

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errors);
    }
}
```

응답 예시:
```json
{
    "username": "사용자명은 필수입니다",
    "password": "비밀번호는 8자 이상이어야 합니다"
}
```

---

## 4. 그룹 검증 (Validation Groups)

같은 DTO에서 상황에 따라 다른 검증 규칙을 적용한다.

```java
// 그룹 인터페이스 정의
public interface OnCreate {}
public interface OnUpdate {}

public class MemberRequest {

    @Null(groups = OnCreate.class)         // 생성 시 id 없어야 함
    @NotNull(groups = OnUpdate.class)      // 수정 시 id 필수
    private Integer id;

    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    private String username;
}

// Controller에서 그룹 지정
@PostMapping("/members")
public void create(@RequestBody @Validated(OnCreate.class) MemberRequest request) {}

@PutMapping("/members")
public void update(@RequestBody @Validated(OnUpdate.class) MemberRequest request) {}
```

---

## 5. 커스텀 Validator

기본 어노테이션으로 부족할 때 직접 만든다.

```java
// 1. 어노테이션 정의
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoSpecialCharValidator.class)
public @interface NoSpecialChar {
    String message() default "특수문자를 포함할 수 없습니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 2. 검증 로직 구현
public class NoSpecialCharValidator implements ConstraintValidator<NoSpecialChar, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return value.matches("^[a-zA-Z0-9가-힣]+$");
    }
}

// 3. 사용
public class MemberCreateRequest {
    @NoSpecialChar
    private String nickname;
}
```

---

## 6. @Valid vs @Validated

| 항목 | @Valid | @Validated |
|---|---|---|
| 패키지 | `jakarta.validation` | `org.springframework.validation` |
| 그룹 검증 | X | O |
| 중첩 객체 검증 | O | O |
| 사용 위치 | 파라미터, 필드 | 파라미터, 클래스 |

```java
// 중첩 객체 검증
public class OrderRequest {
    @Valid                      // 중첩 객체도 검증하려면 @Valid 필수
    @NotNull
    private AddressRequest address;
}

public class AddressRequest {
    @NotBlank
    private String city;
}
```

---

## 7. 검증 위치 가이드

| 계층 | 검증 내용 | 방법 |
|---|---|---|
| Controller | 요청 형식 (null, 길이, 형식) | `@Valid` + Bean Validation |
| Service | 비즈니스 규칙 | 직접 조건 검사 후 예외 |
| Entity (DDD) | 도메인 불변식 | 생성자/메서드에서 검증 |

```java
// Service 계층 - 비즈니스 규칙 검증
@Service
public class MemberService {
    public void create(MemberCreateRequest request) {
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다");
        }
    }
}

// Entity - 도메인 불변식
public class Member {
    public void addScore(int score) {
        if (score < 0) throw new IllegalArgumentException("점수는 음수일 수 없습니다");
        this.activityScore += score;
    }
}
```

---

## 8. 핵심 정리

| 개념 | 설명 |
|---|---|
| `@NotBlank` | null, 빈 문자열, 공백만 있는 문자열 불가 |
| `@Valid` | Controller 파라미터에 붙여 검증 활성화 |
| `@Validated` | 그룹 검증이 필요할 때 사용 |
| `MethodArgumentNotValidException` | 검증 실패 시 발생하는 예외 |
| 커스텀 Validator | `@Constraint` + `ConstraintValidator` 구현 |
| 검증 계층 분리 | 형식은 Controller, 비즈니스 규칙은 Service |
