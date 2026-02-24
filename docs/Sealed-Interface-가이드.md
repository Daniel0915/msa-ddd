# Sealed Interface 가이드

> Java 17에서 정식 도입된 기능으로, **구현체를 컴파일 타임에 제한**할 수 있다.
> "이 인터페이스를 구현할 수 있는 클래스는 이것들뿐이다"를 선언하는 것이다.

---

## 1. 기본 문법

```java
public sealed interface ListItem permits AList, BList {}

public record AList(String a_test) implements ListItem {}  // 허용
public record BList(String b_test) implements ListItem {}  // 허용
public record CList(String c_test) implements ListItem {}  // 컴파일 에러!
```

- `sealed` — 이 인터페이스의 구현을 제한하겠다는 선언
- `permits` — 허용할 구현체를 명시적으로 나열

---

## 2. 왜 필요한가?

### 일반 interface의 문제

```java
public interface ListItem {}

// 누구나 구현 가능 → 제한 불가
public record AList(String a_test) implements ListItem {}
public record CList(String c_test) implements ListItem {}  // 막을 수 없음
```

일반 인터페이스는 **열린 구조**라서 아무 클래스나 구현할 수 있다.
제네릭 바운드 `<T extends ListItem>`을 걸어도, 의도하지 않은 타입이 들어오는 것을 막을 수 없다.

### sealed interface의 해결

```java
public sealed interface ListItem permits AList, BList {}

// 컴파일러가 AList, BList만 허용한다는 것을 알고 있음
// → 제네릭, switch 등에서 타입 안전성 보장
```

---

## 3. 프로젝트 적용 예시

### WebClient 응답 역직렬화에서 타입 제한

```java
public class Dto {

    public sealed interface ListItem permits AList, BList {}

    public record Response<T extends ListItem>(
            String resultCode,
            String resultMsg,
            List<T> list
    ) {}

    public record AList(String a_test) implements ListItem {}
    public record BList(String b_test) implements ListItem {}
}
```

```java
// 허용
Dto.Response<Dto.AList> responseA = ...;
Dto.Response<Dto.BList> responseB = ...;

// 컴파일 에러 — CList는 ListItem을 구현할 수 없음
Dto.Response<CList> responseC = ...;
```

`sealed`이므로 `permits`에 없는 클래스는 `ListItem`을 구현하는 것 자체가 불가능하다.

---

## 4. 구현체가 가져야 하는 제어자

`sealed interface`를 구현하는 클래스는 반드시 다음 중 하나를 선언해야 한다.

| 제어자 | 의미 |
|---|---|
| `final` | 더 이상 상속 불가 (계층 종료) |
| `sealed` | 자신도 구현체를 제한하는 sealed 타입 |
| `non-sealed` | 제한을 풀어서 누구나 상속 가능 |

```java
public sealed interface Shape permits Circle, Polygon {}

public final class Circle implements Shape {}           // 상속 불가

public sealed class Polygon implements Shape             // Polygon도 제한
        permits Triangle, Rectangle {}

public final class Triangle extends Polygon {}
public final class Rectangle extends Polygon {}

public non-sealed class OpenShape implements Shape {}   // 제한 해제, 누구나 상속 가능
```

> `record`는 암묵적으로 `final`이므로 별도 선언이 필요 없다.

---

## 5. switch 패턴 매칭과의 조합

`sealed`의 가장 큰 장점은 컴파일러가 **모든 구현체를 알고 있다**는 점이다.

### Java 21+ 패턴 매칭

```java
public sealed interface PaymentMethod permits Card, Cash, Transfer {}
public record Card(String cardNumber) implements PaymentMethod {}
public record Cash(int amount) implements PaymentMethod {}
public record Transfer(String bankCode, String account) implements PaymentMethod {}
```

```java
// 컴파일러가 모든 케이스를 알고 있으므로 default 불필요
public String describe(PaymentMethod method) {
    return switch (method) {
        case Card c    -> "카드 결제: " + c.cardNumber();
        case Cash c    -> "현금 결제: " + c.amount() + "원";
        case Transfer t -> "계좌이체: " + t.bankCode();
        // default 없어도 컴파일 통과 — 모든 케이스를 커버했으므로
    };
}
```

- 새로운 구현체가 추가되면 switch에서 **컴파일 에러**가 발생하여 누락을 방지한다
- 일반 interface는 default가 필수이므로 이 보장이 불가능하다

---

## 6. sealed interface vs enum

둘 다 "허용되는 타입을 제한"한다는 점에서 비슷하지만, 차이가 있다.

| | enum | sealed interface |
|---|---|---|
| 각 항목이 가질 수 있는 필드 | 모두 동일한 필드 | **각각 다른 필드** 가능 |
| 인스턴스 수 | 고정 (싱글톤) | 제한 없음 |
| 상속 | 불가 | 가능 (sealed/non-sealed 체인) |
| 적합한 경우 | 상수 나열 (Status, Role) | **각기 다른 구조를 가진 타입 그룹** |

```java
// enum이 적합: 모든 항목이 같은 구조
public enum OrderStatus { PENDING, CONFIRMED, CANCELLED }

// sealed가 적합: 항목마다 구조가 다름
public sealed interface ListItem permits AList, BList {}
public record AList(String a_test) implements ListItem {}
public record BList(String b_test) implements ListItem {}
```

---

## 7. 핵심 정리

1. `sealed interface`는 **구현 가능한 타입을 컴파일 타임에 제한**한다
2. `permits`에 명시된 클래스만 구현 가능하며, 그 외는 **컴파일 에러**
3. 구현체는 `final`, `sealed`, `non-sealed` 중 하나를 선택해야 한다 (record는 암묵적 final)
4. switch 패턴 매칭과 조합하면 **케이스 누락을 컴파일 타임에 감지**할 수 있다
5. 제네릭 바운드(`<T extends SealedType>`)와 함께 쓰면 **타입 안전한 제네릭 제한**이 가능하다
