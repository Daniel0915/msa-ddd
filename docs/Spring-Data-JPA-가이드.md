# Spring Data JPA 가이드

## 1. JPA란?

**Java Persistence API** - Java 객체와 관계형 데이터베이스 테이블을 매핑하는 ORM 표준 스펙이다.
Spring Data JPA는 JPA를 더 편리하게 사용하기 위한 Spring의 추상화 계층이다.

```
Java 객체 (Entity) ←──JPA 매핑──→ DB 테이블 (Row)
```

---

## 2. 핵심 어노테이션

### 2.1 엔티티 정의

```java
@Entity                    // JPA가 관리하는 엔티티 클래스 선언
@Table(name = "member")    // 매핑할 테이블명 지정 (생략 시 클래스명)
public class Member {

    @Id                            // 기본 키(PK) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 자동 증가
    private int id;

    @Column(unique = true)         // 유니크 제약조건
    private String username;

    @Column(nullable = false, length = 100)  // NOT NULL, 길이 제한
    private String password;

    private String nickname;       // @Column 생략 시 필드명 = 컬럼명
}
```

### 2.2 주요 어노테이션 정리

| 어노테이션 | 역할 | 예시 |
|---|---|---|
| `@Entity` | JPA 엔티티 선언 | `@Entity public class Member` |
| `@Table` | 테이블명 지정 | `@Table(name = "members")` |
| `@Id` | 기본 키 지정 | `@Id private int id;` |
| `@GeneratedValue` | PK 자동 생성 전략 | `IDENTITY`, `SEQUENCE`, `AUTO` |
| `@Column` | 컬럼 매핑 세부 설정 | `unique`, `nullable`, `length` |
| `@MappedSuperclass` | 매핑 정보 상속 전용 | 테이블 생성 안 됨 |
| `@Transient` | DB 매핑 제외 | `@Transient private List<Object> events;` |

---

## 3. Repository

### 3.1 기본 사용법

```java
public interface MemberRepository extends JpaRepository<Member, Integer> {
    // JpaRepository가 기본 CRUD 메서드 자동 제공
}
```

### 3.2 자동 제공 메서드

```java
// 저장/수정
memberRepository.save(member);           // INSERT or UPDATE
memberRepository.saveAll(members);       // 다건 저장

// 조회
memberRepository.findById(1);            // Optional<Member>
memberRepository.findAll();              // List<Member>
memberRepository.existsById(1);          // boolean
memberRepository.count();               // long

// 삭제
memberRepository.delete(member);
memberRepository.deleteById(1);
```

### 3.3 쿼리 메서드 (메서드 이름으로 쿼리 생성)

```java
public interface MemberRepository extends JpaRepository<Member, Integer> {

    // SELECT * FROM member WHERE username = ?
    Optional<Member> findByUsername(String username);

    // SELECT * FROM member WHERE nickname LIKE ?
    List<Member> findByNicknameContaining(String keyword);

    // SELECT * FROM member WHERE activity_score > ? ORDER BY activity_score DESC
    List<Member> findByActivityScoreGreaterThanOrderByActivityScoreDesc(int score);

    // SELECT COUNT(*) FROM member WHERE username = ?
    boolean existsByUsername(String username);
}
```

### 3.4 @Query 직접 작성

```java
public interface MemberRepository extends JpaRepository<Member, Integer> {

    // JPQL
    @Query("SELECT m FROM Member m WHERE m.username = :username AND m.activityScore > :score")
    List<Member> findActiveMembers(@Param("username") String username, @Param("score") int score);

    // Native SQL
    @Query(value = "SELECT * FROM member WHERE username = ?1", nativeQuery = true)
    Member findByUsernameNative(String username);
}
```

---

## 4. 영속성 컨텍스트 (Persistence Context)

JPA의 핵심 개념으로, 엔티티를 관리하는 **1차 캐시** 역할을 한다.

### 4.1 엔티티 생명주기

```
[비영속 (new)]  ──save()──→  [영속 (managed)]  ──detach()──→  [준영속 (detached)]
                                    │
                                 remove()
                                    │
                                    ▼
                             [삭제 (removed)]
```

### 4.2 Dirty Checking (변경 감지)

```java
@Transactional
public void updateNickname(int memberId, String newNickname) {
    Member member = memberRepository.findById(memberId).orElseThrow();
    member.setNickname(newNickname);
    // save() 호출 안 해도 트랜잭션 커밋 시 자동으로 UPDATE 실행!
    // 영속성 컨텍스트가 스냅샷과 비교하여 변경 감지
}
```

### 4.3 1차 캐시

```java
@Transactional
public void example() {
    Member m1 = memberRepository.findById(1).orElseThrow();  // DB 조회 (SQL 실행)
    Member m2 = memberRepository.findById(1).orElseThrow();  // 1차 캐시에서 반환 (SQL 없음)
    System.out.println(m1 == m2);  // true (같은 인스턴스)
}
```

---

## 5. 연관관계 매핑

### 5.1 기본 관계

```java
// 1:N 관계
@Entity
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "team")  // 양방향 시 주인 지정
    private List<Member> members = new ArrayList<>();
}

@Entity
public class Member {
    @ManyToOne(fetch = FetchType.LAZY)  // N:1 (외래키 소유)
    @JoinColumn(name = "team_id")
    private Team team;
}
```

### 5.2 Fetch 전략

| 전략 | 동작 | 기본값 |
|---|---|---|
| `FetchType.LAZY` | 실제 사용 시점에 쿼리 | `@OneToMany`, `@ManyToMany` |
| `FetchType.EAGER` | 즉시 로딩 (JOIN) | `@ManyToOne`, `@OneToOne` |

> **실무 원칙: 모든 연관관계에 `FetchType.LAZY` 사용.** 필요 시 fetch join으로 해결.

### 5.3 N+1 문제와 해결

```java
// 문제: Member 10명 조회 → 각 Member의 Team 조회 쿼리 10번 추가 발생
List<Member> members = memberRepository.findAll();
members.forEach(m -> m.getTeam().getName());  // N+1!

// 해결: Fetch Join
@Query("SELECT m FROM Member m JOIN FETCH m.team")
List<Member> findAllWithTeam();
```

---

## 6. @Transactional

### 6.1 기본 동작

```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional           // 메서드 시작 시 트랜잭션 시작, 종료 시 커밋
    public void update() {
        // 성공 → 커밋
        // 예외 → 롤백 (RuntimeException만. CheckedException은 롤백 안 됨)
    }

    @Transactional(readOnly = true)  // 읽기 전용 (성능 최적화)
    public Member findById(int id) {
        return memberRepository.findById(id).orElseThrow();
    }
}
```

### 6.2 전파 옵션 (Propagation)

| 옵션 | 동작 |
|---|---|
| `REQUIRED` (기본값) | 기존 트랜잭션 있으면 참여, 없으면 새로 생성 |
| `REQUIRES_NEW` | 항상 새 트랜잭션 생성 (기존 트랜잭션 일시 중단) |
| `MANDATORY` | 기존 트랜잭션 없으면 예외 |
| `NOT_SUPPORTED` | 트랜잭션 없이 실행 |

---

## 7. 핵심 정리

| 개념 | 핵심 |
|---|---|
| Entity | `@Entity` + `@Id`로 테이블과 매핑 |
| Repository | `JpaRepository` 상속으로 CRUD 자동 제공 |
| 영속성 컨텍스트 | 1차 캐시 + Dirty Checking |
| 연관관계 | LAZY 기본, fetch join으로 N+1 해결 |
| 트랜잭션 | `@Transactional`로 원자성 보장 |
