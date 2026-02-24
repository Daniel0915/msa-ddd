# Jackson 역직렬화 가이드

> 외부 API 응답(JSON)을 Java 객체로 변환하는 과정을 다룬다.
> Spring WebClient + Jackson + record 조합에서 역직렬화가 어떻게 동작하는지에 집중한다.

---

## 1. 역직렬화란?

```
직렬화 (Serialization)     : Java 객체 → JSON 문자열
역직렬화 (Deserialization) : JSON 문자열 → Java 객체
```

```
[외부 API] --JSON 응답--> [WebClient] --Jackson--> [Java 객체]
```

Spring WebClient가 HTTP 응답을 받으면, 내부적으로 Jackson `ObjectMapper`가 JSON을 Java 객체로 변환한다.

---

## 2. class vs record 역직렬화 방식

### 일반 class — 기본 생성자 + setter

```java
public class AList {
    private String a_test;
    public AList() {}                           // 1. 기본 생성자 필수
    public void setA_test(String a_test) {      // 2. setter로 값 주입
        this.a_test = a_test;
    }
}
```

```
Jackson 동작:
1. new AList()              ← 기본 생성자로 빈 객체 생성
2. obj.setA_test("hello")   ← setter로 필드 값 주입
```

### record — canonical 생성자

```java
public record AList(String a_test) {}
```

```
Jackson 동작:
1. new AList("hello")       ← canonical 생성자 한 번에 완성 (setter 불필요)
```

record는 기본 생성자도 setter도 없다.
Jackson 2.12+부터 record의 **canonical 생성자**(모든 필드를 받는 생성자)를 자동 인식하여,
`@JsonCreator` 같은 어노테이션 없이 역직렬화가 동작한다.

---

## 3. JSON 키 → record 필드 매칭 과정

외부 API가 다음 JSON을 반환한다고 가정한다.

```json
{
  "resultCode": "200",
  "resultMsg": "성공",
  "list": [
    { "a_test": "hello" },
    { "a_test": "world" }
  ]
}
```

대응하는 record:

```java
public record Response<T extends ListItem>(
    String resultCode,
    String resultMsg,
    List<T> list
) {}

public record AList(String a_test) implements ListItem {}
```

Jackson의 매칭 과정:

```
JSON 키          →  record 컴포넌트
─────────────────────────────────
"resultCode"     →  Response.resultCode
"resultMsg"      →  Response.resultMsg
"list"           →  Response.list (List<AList>)
  "a_test"       →  AList.a_test
```

**JSON 키 이름과 record 컴포넌트 이름이 일치하면 자동 매칭된다.**

---

## 4. 이름이 다를 때 — @JsonProperty

JSON 키와 record 필드 이름이 다르면 `@JsonProperty`로 매핑한다.

```json
{ "result_code": "200", "result_msg": "성공" }
```

```java
public record Response<T extends ListItem>(
    @JsonProperty("result_code") String resultCode,
    @JsonProperty("result_msg") String resultMsg,
    List<T> list
) {}
```

또는 전역적으로 snake_case → camelCase 변환을 설정할 수 있다.

```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
```

---

## 5. Type Erasure와 ParameterizedTypeReference

### 문제: 제네릭 타입 소거

Java는 컴파일 후 제네릭 타입 정보를 지운다 (Type Erasure).

```java
// 컴파일 전
List<AList> list;

// 컴파일 후 (런타임)
List list;  // AList 정보가 사라짐
```

이 때문에 제네릭 타입을 Jackson에 직접 전달할 수 없다.

```java
// 잘못된 방법
.bodyToMono(Dto.Response.class)
// → Jackson이 T가 뭔지 모름
// → list 안의 원소를 LinkedHashMap으로 만들어버림

response.list().get(0);  // AList가 아닌 LinkedHashMap!
```

### 해결: ParameterizedTypeReference

```java
// 올바른 방법
.bodyToMono(new ParameterizedTypeReference<Dto.Response<Dto.AList>>() {})
// → Jackson이 T = AList 라는 것을 알고 정확히 역직렬화
```

### 왜 이게 동작하는가?

```java
// 이 코드는 익명 클래스를 생성한다
new ParameterizedTypeReference<Dto.Response<Dto.AList>>() {}
```

Java의 Type Erasure에는 예외가 있다.
**클래스 선언부의 제네릭 타입 정보는 런타임에도 유지된다** (리플렉션으로 조회 가능).

```java
// 익명 클래스의 상속 구조
// ParameterizedTypeReference<Dto.Response<Dto.AList>> ← 이 타입 정보가 보존됨
//         ↑
//    익명 클래스 (extends ParameterizedTypeReference)
```

`ParameterizedTypeReference`는 이 특성을 이용하여:
1. 익명 클래스의 부모 타입에서 제네릭 정보를 리플렉션으로 추출
2. `Dto.Response<Dto.AList>` 라는 완전한 타입 정보를 Jackson에 전달
3. Jackson이 `list` 필드를 `List<AList>`로 정확히 역직렬화

### Type Erasure의 규칙 정리

```java
// 지워지는 경우 — 변수, 파라미터의 제네릭
List<AList> list;                    // 런타임: List (AList 정보 없음)
Dto.Response<Dto.AList> response;    // 런타임: Dto.Response (AList 정보 없음)

// 보존되는 경우 — 클래스 선언부의 제네릭
class MyRef extends ParameterizedTypeReference<Dto.Response<Dto.AList>> {}
// → 부모 타입의 제네릭 정보가 클래스 메타데이터에 기록됨
// → 리플렉션으로 Dto.Response<Dto.AList> 조회 가능
```

---

## 6. 전체 흐름 정리

```
[외부 API]
    │
    │  HTTP 응답: { "resultCode": "200", "list": [{ "a_test": "hello" }] }
    ▼
[WebClient]
    │
    │  .bodyToMono(new ParameterizedTypeReference<Response<AList>>() {})
    ▼
[Jackson ObjectMapper]
    │
    │  1. ParameterizedTypeReference에서 타입 정보 추출
    │     → Response<AList> 라는 것을 인지
    │
    │  2. JSON 키 → record 컴포넌트 이름 매칭
    │     "resultCode" → Response.resultCode
    │     "list"       → List<AList>
    │     "a_test"     → AList.a_test
    │
    │  3. canonical 생성자 호출
    │     new AList("hello")
    │     new Response("200", "성공", List.of(aList))
    ▼
[Java 객체]
    response.resultCode()     // "200"
    response.list().get(0)    // AList[a_test=hello]
```

---

## 7. 알 수 없는 JSON 필드가 있을 때

외부 API가 예상치 못한 필드를 포함할 수 있다.

```json
{
  "resultCode": "200",
  "resultMsg": "성공",
  "list": [],
  "timestamp": "2024-01-01"   ← record에 없는 필드
}
```

기본 설정에서는 `UnrecognizedPropertyException`이 발생한다.
무시하려면 다음 설정을 추가한다.

```java
// record에 어노테이션 추가
@JsonIgnoreProperties(ignoreUnknown = true)
public record Response<T extends ListItem>(
    String resultCode,
    String resultMsg,
    List<T> list
) {}
```

또는 application.yml에서 전역 설정:

```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: false
```

---

## 8. 핵심 정리

1. Jackson은 **JSON 키 이름과 record 컴포넌트 이름을 매칭**하여 역직렬화한다
2. record는 기본 생성자/setter 대신 **canonical 생성자**를 통해 한 번에 객체를 생성한다
3. 제네릭 타입은 런타임에 소거되므로, **`ParameterizedTypeReference`로 타입 정보를 보존**해야 한다
4. `ParameterizedTypeReference`는 **익명 클래스의 부모 타입 정보가 보존되는 Java 특성**을 이용한 트릭이다
5. 외부 API의 예상치 못한 필드는 `@JsonIgnoreProperties(ignoreUnknown = true)`로 무시할 수 있다
