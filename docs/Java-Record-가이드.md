# Java Record 가이드

> Java 16에서 정식 도입된 기능으로, **불변 데이터 캐리어**를 간결하게 정의한다.
> DTO, 이벤트 객체, API 응답 등 "데이터를 담아서 전달하는 객체"에 적합하다.

---

## 1. record vs class 비교

### 일반 class로 DTO를 만들면

```java
public class AList {
    private final String a_test;

    public AList(String a_test) {
        this.a_test = a_test;
    }

    public String getA_test() {
        return a_test;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AList aList)) return false;
        return Objects.equals(a_test, aList.a_test);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a_test);
    }

    @Override
    public String toString() {
        return "AList[a_test=" + a_test + "]";
    }
}
```

### record로 만들면

```java
public record AList(String a_test) {}
```

**한 줄**로 위의 모든 코드가 자동 생성된다.

---

## 2. record가 자동으로 제공하는 것

| 항목 | 자동 생성 여부 | 설명 |
|---|---|---|
| 생성자 | O | 모든 필드를 받는 canonical 생성자 |
| getter | O | 필드명과 동일한 접근자 (`a_test()`, `getX()`가 아님) |
| `equals()` | O | 모든 필드 기반 값 비교 |
| `hashCode()` | O | 모든 필드 기반 해시 |
| `toString()` | O | `AList[a_test=value]` 형식 |
| `final` 클래스 | O | 상속 불가 |
| `private final` 필드 | O | 불변 보장 |

```java
public record AList(String a_test) {}

// 사용
AList a = new AList("hello");
a.a_test();          // "hello" — getter (getA_test()가 아님!)
a.toString();        // "AList[a_test=hello]"

AList b = new AList("hello");
a.equals(b);         // true — 값 기반 비교
```

---

## 3. 왜 record를 쓰는가

### 3.1 불변성 보장

```java
// record — 필드 변경 불가
public record AList(String a_test) {}

AList a = new AList("hello");
// a.a_test = "world";  // 컴파일 에러 — final 필드

// class — 실수로 변경 가능
public class AList {
    private String a_test;  // final 빼먹으면 가변 객체가 됨
    public void setA_test(String a_test) { this.a_test = a_test; }
}
```

record는 **구조적으로 불변**이다. 개발자가 실수로 setter를 만들거나 final을 빼먹을 여지가 없다.

### 3.2 보일러플레이트 제거

```java
// Lombok으로 줄여도
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class AList {
    private final String a_test;
}

// record는 어노테이션조차 불필요
public record AList(String a_test) {}
```

Lombok 없이도 동일한 결과를 얻을 수 있다.

### 3.3 의도가 명확하다

```java
public class AList { ... }   // 이게 DTO인지? 엔티티인지? 서비스인지? 알 수 없음
public record AList( ... )    // "이것은 불변 데이터 객체다"라는 의도가 즉시 전달됨
```

`record` 키워드 자체가 **"이 클래스는 데이터를 담는 용도"** 라는 설계 의도를 표현한다.

### 3.4 값 기반 동등성

```java
// class — 기본적으로 참조 비교
AList a1 = new AList("test");
AList a2 = new AList("test");
a1.equals(a2);  // false (equals를 오버라이드하지 않으면)

// record — 자동으로 값 비교
AList a1 = new AList("test");
AList a2 = new AList("test");
a1.equals(a2);  // true
```

---

## 4. 프로젝트 적용 예시

### WebClient 응답 역직렬화

```java
public class Dto {

    public sealed interface ListItem permits AList, BList {}

    @Builder
    public record Response<T extends ListItem>(
            String resultCode,
            String resultMsg,
            List<T> list
    ) {}

    @Builder
    public record AList(String a_test) implements ListItem {}

    @Builder
    public record BList(String b_test) implements ListItem {}
}
```

외부 API 응답을 받아서 역직렬화하는 DTO는 record에 가장 적합한 케이스다.

- 응답 데이터는 받아서 읽기만 하므로 **불변이 자연스럽다**
- 필드만 선언하면 되므로 **간결하다**
- Jackson이 record의 canonical 생성자를 인식하여 **역직렬화가 자동으로 동작**한다

---

## 5. record에서 커스텀 로직 추가

### 컴팩트 생성자 (유효성 검증)

```java
public record AList(String a_test) {
    public AList {
        // compact constructor — this.a_test = a_test 자동 수행
        if (a_test == null || a_test.isBlank()) {
            throw new IllegalArgumentException("a_test는 필수입니다");
        }
    }
}
```

### 커스텀 메서드

```java
public record Response<T extends ListItem>(
        String resultCode,
        String resultMsg,
        List<T> list
) {
    public boolean isSuccess() {
        return resultCode != null && resultCode.startsWith("200");
    }

    public int listSize() {
        return list != null ? list.size() : 0;
    }
}
```

record도 메서드를 자유롭게 추가할 수 있다. 다만 **필드를 변경하는 메서드는 만들 수 없다**.

---

## 6. record를 쓰면 안 되는 경우

| 상황 | 이유 | 대안 |
|---|---|---|
| JPA Entity | JPA는 기본 생성자 + setter 기반, 프록시 상속 필요 | `@Entity class` |
| 상태 변경이 필요한 객체 | record는 불변 | 일반 class |
| 상속이 필요한 경우 | record는 암묵적 final | 일반 class |
| Spring Bean (Service, Repository 등) | 데이터 캐리어가 아님 | `@Component class` |

---

## 7. record + Lombok @Builder

```java
@Builder
public record AList(String a_test) implements ListItem {}

// Builder 패턴 사용 가능
AList a = AList.builder()
        .a_test("hello")
        .build();
```

필드가 많을 때 Builder와 조합하면 가독성이 좋아진다.
다만 필드가 1~2개인 record는 생성자를 직접 쓰는 것이 더 간결하다.

---

## 8. 핵심 정리

1. record는 **불변 데이터 객체**를 한 줄로 정의한다
2. 생성자, getter, equals, hashCode, toString이 **자동 생성**된다
3. `record` 키워드 자체가 **"데이터 캐리어"라는 설계 의도**를 표현한다
4. DTO, 이벤트 객체, API 응답 등 **받아서 읽기만 하는 데이터**에 적합하다
5. JPA Entity처럼 가변성이나 상속이 필요한 곳에서는 **사용하지 않는다**
