# Spring Boot 핵심 어노테이션 가이드

## 1. 애플리케이션 시작

### @SpringBootApplication

```java
// 현재 프로젝트: MsaDddApplication.java
@SpringBootApplication
public class MsaDddApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsaDddApplication.class, args);
    }
}
```

`@SpringBootApplication`은 3가지 어노테이션의 조합이다:

| 포함 어노테이션 | 역할 |
|---|---|
| `@SpringBootConfiguration` | Spring Boot 설정 클래스 |
| `@EnableAutoConfiguration` | 의존성 기반 자동 설정 |
| `@ComponentScan` | 현재 패키지 하위의 Bean 자동 스캔 |

> `com.back` 패키지에 위치하므로 `com.back.**` 하위의 모든 `@Component`, `@Service` 등이 자동 등록된다.

---

## 2. Bean 등록 어노테이션

### 2.1 스테레오타입 어노테이션

```java
@Component       // 일반 컴포넌트
@Service         // 비즈니스 로직 계층
@Repository      // 데이터 접근 계층 (JPA 예외 변환 포함)
@Controller      // MVC 컨트롤러 (뷰 반환)
@RestController  // REST API 컨트롤러 (@Controller + @ResponseBody)
```

모두 `@Component`의 특수화이며, **역할에 따라 구분**해서 사용한다.

```
@Component
├── @Service          ← 서비스 계층
├── @Repository       ← 데이터 접근 계층
├── @Controller       ← 웹 계층
└── @RestController   ← REST API 계층
```

### 2.2 @Configuration + @Bean

```java
// 현재 프로젝트: GlobalConfig.java
@Configuration    // 설정 클래스 선언
public class GlobalConfig {
    @Getter
    private static EventPublisher eventPublisher;

    @Autowired
    public void setEventPublisher(EventPublisher eventPublisher) {
        GlobalConfig.eventPublisher = eventPublisher;
    }
}
```

직접 Bean을 등록할 때:

```java
@Configuration
public class AppConfig {

    @Bean   // 반환 객체를 Spring Bean으로 등록
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule());
    }
}
```

### 2.3 @Component vs @Bean

| 항목 | @Component | @Bean |
|---|---|---|
| 위치 | 클래스 위에 | 메서드 위에 (@Configuration 내) |
| 대상 | 직접 만든 클래스 | 외부 라이브러리 클래스 |
| 등록 | 자동 스캔 | 수동 등록 |

---

## 3. 의존성 주입 (DI)

### 3.1 생성자 주입 (권장)

```java
// 현재 프로젝트: EventPublisher.java
@Service
@RequiredArgsConstructor   // Lombok으로 생성자 생성
public class EventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;
    // 생성자가 1개 → @Autowired 생략 가능
}
```

### 3.2 주입 방식 비교

```java
// 1. 생성자 주입 (권장)
@Service
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }
}

// 2. 필드 주입 (비권장 - 테스트 어려움)
@Service
public class MemberService {
    @Autowired
    private MemberRepository memberRepository;
}

// 3. Setter 주입 (선택적 의존성일 때)
@Service
public class MemberService {
    private MemberRepository memberRepository;

    @Autowired
    public void setMemberRepository(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }
}
```

> **생성자 주입이 권장되는 이유:**
> - `final` 필드로 불변성 보장
> - 순환 참조를 컴파일 타임에 감지
> - 테스트에서 Mock 주입이 쉬움

---

## 4. Web 계층 어노테이션

### 4.1 Controller

```java
@RestController                      // JSON 응답 반환
@RequestMapping("/api/members")      // 공통 경로
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping                      // GET /api/members
    public List<MemberDto> list() { ... }

    @GetMapping("/{id}")             // GET /api/members/1
    public MemberDto detail(@PathVariable int id) { ... }

    @PostMapping                     // POST /api/members
    public void create(@RequestBody @Valid MemberCreateRequest request) { ... }

    @PutMapping("/{id}")             // PUT /api/members/1
    public void update(@PathVariable int id, @RequestBody MemberUpdateRequest request) { ... }

    @DeleteMapping("/{id}")          // DELETE /api/members/1
    public void delete(@PathVariable int id) { ... }
}
```

### 4.2 파라미터 바인딩

```java
// 경로 변수
@GetMapping("/{id}")
public MemberDto detail(@PathVariable int id) { ... }
// GET /api/members/1 → id = 1

// 쿼리 파라미터
@GetMapping("/search")
public List<MemberDto> search(@RequestParam String keyword,
                               @RequestParam(defaultValue = "0") int page) { ... }
// GET /api/members/search?keyword=peter&page=0

// 요청 본문 (JSON)
@PostMapping
public void create(@RequestBody MemberCreateRequest request) { ... }
// POST body: {"username": "peter", "password": "1234"}
```

---

## 5. 설정 관련 어노테이션

### 5.1 @Value

```java
@Service
public class MailService {
    @Value("${spring.mail.host}")       // application.properties 값 주입
    private String mailHost;

    @Value("${app.default-score:0}")    // 기본값 지정
    private int defaultScore;
}
```

### 5.2 @ConfigurationProperties

```java
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String name;
    private int defaultScore;
    // application.properties의 app.name, app.default-score 매핑
}
```

### 5.3 @Profile

```java
@Configuration
@Profile("dev")        // dev 프로필에서만 활성화
public class DevConfig { ... }

@Configuration
@Profile("prod")       // prod 프로필에서만 활성화
public class ProdConfig { ... }
```

---

## 6. AOP / 횡단 관심사

### 6.1 @Transactional

```java
@Service
public class MemberService {

    @Transactional                      // 트랜잭션 시작/커밋/롤백
    public void create(MemberCreateRequest request) { ... }

    @Transactional(readOnly = true)     // 읽기 전용 최적화
    public MemberDto findById(int id) { ... }
}
```

### 6.2 @Async

```java
@EnableAsync          // 비동기 활성화 (설정 클래스에)

@Async                // 비동기 실행 (별도 스레드)
public void sendEmail(String to) { ... }
```

### 6.3 @Scheduled

```java
@EnableScheduling     // 스케줄링 활성화

@Scheduled(cron = "0 0 3 * * *")     // 매일 새벽 3시
public void cleanup() { ... }

@Scheduled(fixedRate = 60000)         // 60초마다
public void healthCheck() { ... }
```

---

## 7. 현재 프로젝트에서 사용 중인 어노테이션 정리

| 어노테이션 | 사용 위치 | 역할 |
|---|---|---|
| `@SpringBootApplication` | `MsaDddApplication` | 애플리케이션 시작점 |
| `@Configuration` | `GlobalConfig` | 설정 클래스 |
| `@Service` | `EventPublisher` | 서비스 Bean 등록 |
| `@MappedSuperclass` | `BaseEntity`, `BaseMember` | JPA 매핑 정보 상속 |
| `@Entity` | `Member` | JPA 엔티티 선언 |
| `@Autowired` | `GlobalConfig.setEventPublisher()` | 의존성 주입 |

---

## 8. 핵심 정리

| 카테고리 | 어노테이션 | 한 줄 설명 |
|---|---|---|
| 시작 | `@SpringBootApplication` | 자동 설정 + 컴포넌트 스캔 |
| Bean 등록 | `@Component`, `@Service`, `@Repository` | 클래스 → Bean 자동 등록 |
| 설정 | `@Configuration` + `@Bean` | 수동 Bean 등록 |
| DI | `@RequiredArgsConstructor` | 생성자 주입 (Lombok) |
| Web | `@RestController`, `@GetMapping` 등 | REST API 엔드포인트 |
| 검증 | `@Valid` | 요청 데이터 검증 |
| 트랜잭션 | `@Transactional` | 트랜잭션 관리 |
