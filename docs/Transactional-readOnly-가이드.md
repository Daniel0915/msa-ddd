# @Transactional(readOnly = true) 동작 원리 가이드

> 이 문서는 `readOnly = true`가 내부적으로 **어떤 최적화를 수행하는지**에 집중한다.
> 기본적인 `@Transactional` 사용법은 `Spring-Data-JPA-가이드.md` 섹션 6을 참고한다.

---

## 1. @Transactional 없음 vs readOnly = true (읽기 기준)

### @Transactional이 없을 때

```
메서드 시작 → 트랜잭션 없음
  → Repository 메서드 호출 → 자체 트랜잭션 생성 → 조회 → 즉시 트랜잭션 종료
  → 엔티티가 준영속(detached) 상태
  → Lazy Loading 불가 ❌ (LazyInitializationException)
  → 1차 캐시 없음 ❌
메서드 종료
```

### @Transactional(readOnly = true)가 있을 때

```
메서드 시작 → 트랜잭션 시작 → 영속성 컨텍스트 열림
  → 엔티티 조회 (영속 상태)
  → Lazy Loading 가능 ✅
  → 1차 캐시 동작 (같은 엔티티 재조회 시 DB 안 감) ✅
메서드 종료 → 트랜잭션 커밋 → 영속성 컨텍스트 닫힘
```

### 실제 예시

```java
// ✅ @Transactional(readOnly = true) 있음
@Transactional(readOnly = true)
public PostDto getPost(int id) {
    Post post = postRepository.findById(id).orElseThrow();
    post.getComments().size();  // Lazy Loading 동작 ✅
    return post.toDto();
}

// ❌ @Transactional 없음
public PostDto getPost(int id) {
    Post post = postRepository.findById(id).orElseThrow();
    post.getComments().size();  // LazyInitializationException 💥
    return post.toDto();
}
```

### 비교표

| 항목 | `@Transactional` 없음 | `@Transactional(readOnly = true)` |
|---|---|---|
| 트랜잭션 | Repository 호출마다 개별 트랜잭션 | 메서드 전체가 하나의 트랜잭션 |
| 영속성 컨텍스트 | Repository 호출 후 즉시 닫힘 | 메서드 종료까지 유지 |
| Lazy Loading | 불가 (`LazyInitializationException`) | 가능 |
| 1차 캐시 | 없음 (매번 DB 조회) | 동작 (같은 엔티티 재조회 시 캐시) |
| 스냅샷 | 없음 | 없음 (readOnly라서 생략) |
| Dirty Checking | 없음 | 없음 (readOnly라서 비활성화) |
| DB 커넥션 점유 | 짧음 (쿼리 단위) | 메서드 동안 점유 |

> **결론:** Lazy Loading과 1차 캐시가 필요한 읽기 메서드에는 반드시 `readOnly = true`를 붙인다.
> 트랜잭션 없이도 동작하는 단건 조회가 있지만, 일관성과 안전성을 위해 붙이는 것이 관례다.

---

## 2. readOnly = true의 내부 최적화 (3단계)

```
@Transactional(readOnly = true)
public Optional<Member> findById(int id) { ... }
```

이 한 줄이 **3개의 레이어**에서 각각 최적화를 수행한다.

```
[1] Spring/Hibernate 레벨  → Flush Mode를 MANUAL로 변경
[2] 영속성 컨텍스트 레벨    → Dirty Checking 비활성화, 스냅샷 생략
[3] JDBC/DB 레벨           → Connection에 readOnly 힌트 전달
```

---

## 3. [1단계] Flush Mode → MANUAL

### 일반 트랜잭션 (`readOnly = false`)

```
엔티티 조회 → 필드 변경 → 트랜잭션 종료 시 자동 flush → UPDATE SQL 실행
```

Hibernate의 기본 FlushMode는 `AUTO`다.
쿼리 실행 전이나 트랜잭션 커밋 시점에 **변경된 엔티티를 자동으로 DB에 반영**한다.

### readOnly 트랜잭션

```
엔티티 조회 → 필드 변경 → flush 안 함 → UPDATE SQL 실행 안 됨
```

FlushMode가 `MANUAL`로 바뀌어서, **명시적으로 `flush()`를 호출하지 않는 한 DB에 반영하지 않는다.**

```java
@Transactional(readOnly = true)
public Member findById(int id) {
    Member member = memberRepository.findById(id).orElseThrow();
    member.setNickname("변경");  // 영속성 컨텍스트에서는 변경되지만
    return member;               // flush가 안 되므로 DB에는 반영 안 됨
}
```

---

## 4. [2단계] Dirty Checking 비활성화 & 스냅샷 생략

### 일반 트랜잭션의 동작

```
엔티티 조회 시:
  1. DB에서 데이터 로드
  2. 엔티티 객체 생성 (영속성 컨텍스트에 저장)
  3. 원본 스냅샷 복사본 생성 ← 메모리 사용

flush 시:
  4. 현재 엔티티 vs 스냅샷 비교 (Dirty Checking)
  5. 변경된 필드가 있으면 UPDATE SQL 생성
```

스냅샷은 **엔티티마다 하나씩** 생성된다.
1,000개의 엔티티를 조회하면 1,000개의 스냅샷이 메모리에 올라간다.

### readOnly 트랜잭션의 동작

```
엔티티 조회 시:
  1. DB에서 데이터 로드
  2. 엔티티 객체 생성 (영속성 컨텍스트에 저장)
  3. 스냅샷 생성 안 함 ← 메모리 절약

flush 시:
  4. flush 자체를 안 함 ← CPU 절약
```

**대량 조회에서 특히 효과적이다.**

```java
// PostFacade.java
@Transactional(readOnly = true)
public List<Post> findByOrderByIdDesc() {
    return postSupport.findByOrderByIdDesc();
    // 100개의 Post를 조회해도 스냅샷 100개가 생성되지 않음
}
```

---

## 5. [3단계] JDBC 드라이버 / DB 레벨 최적화

Spring은 `Connection.setReadOnly(true)`를 호출한다.
이 힌트를 **DB 드라이버가 어떻게 활용하는지**는 드라이버마다 다르다.

### PostgreSQL (현재 프로젝트)

```
Connection.setReadOnly(true)
  → PostgreSQL JDBC 드라이버가 "SET TRANSACTION READ ONLY" 실행
  → DB가 해당 트랜잭션에서 INSERT/UPDATE/DELETE를 거부
```

실제로 readOnly 트랜잭션에서 쓰기를 시도하면:

```
ERROR: cannot execute INSERT in a read-only transaction
```

### MySQL

```
Connection.setReadOnly(true)
  → MySQL Connector/J가 Replica(읽기 전용 서버)로 라우팅
  → Source-Replica 구조에서 읽기 부하 분산
```

### Replica 라우팅 아키텍처

```
@Transactional                    → Source DB (쓰기)
@Transactional(readOnly = true)   → Replica DB (읽기)

┌──────────┐     ┌────────────┐
│  Source   │────→│  Replica   │  (비동기 복제)
│  (Write)  │     │  (Read)    │
└──────────┘     └────────────┘
      ↑                ↑
  @Transactional   @Transactional
                   (readOnly=true)
```

이 구조에서 readOnly는 **단순 최적화가 아니라 라우팅 키**로 동작한다.

---

## 6. 프로젝트 적용 패턴

### Facade에서 트랜잭션 경계 설정

```java
// MemberFacade.java

@Transactional                              // 쓰기 → 일반 트랜잭션
public RsData<Member> join(...) { ... }

@Transactional(readOnly = true)             // 읽기 → readOnly
public long count() { ... }

@Transactional(readOnly = true)             // 읽기 → readOnly
public Optional<Member> findByUsername(...) { ... }

@Transactional(readOnly = true)             // 읽기 → readOnly
public Optional<Member> findById(...) { ... }
```

```java
// PostFacade.java

@Transactional                              // 쓰기 → 일반 트랜잭션
public PostMember syncMember(...) { ... }

@Transactional                              // 쓰기 → 일반 트랜잭션
public RsData<Post> write(...) { ... }

@Transactional(readOnly = true)             // 읽기 → readOnly
public long count() { ... }

@Transactional(readOnly = true)             // 읽기 → readOnly
public Optional<Post> findById(...) { ... }

@Transactional(readOnly = true)             // 읽기 → readOnly
public List<Post> findByOrderByIdDesc() { ... }
```

**규칙: 데이터를 변경하지 않는 메서드는 반드시 `readOnly = true`를 붙인다.**

---

## 7. readOnly에서 쓰기하면 어떻게 되는가?

동작이 **DB 드라이버에 따라 다르다.**

| DB | 동작 |
|---|---|
| PostgreSQL | `READ ONLY transaction` 에러 발생 (DB가 거부) |
| MySQL | 에러 없이 무시될 수 있음 (드라이버 버전에 따라 다름) |
| H2 (테스트용) | 에러 없이 flush만 안 됨 |

### Hibernate 레벨에서의 보호

DB가 거부하지 않더라도, Hibernate의 FlushMode가 `MANUAL`이므로:

```
1. 엔티티 필드를 변경해도 flush가 일어나지 않음
2. 따라서 UPDATE SQL이 생성되지 않음
3. 결과적으로 DB에 반영되지 않음
```

**하지만** `@Modifying` + `@Query`로 직접 SQL을 실행하면 flush 없이 DB에 직접 나가므로, readOnly여도 실행될 수 있다. (DB가 거부하지 않는 경우)

---

## 8. 성능 차이 요약

| 항목 | `readOnly = false` | `readOnly = true` |
|---|---|---|
| Flush Mode | AUTO (자동 flush) | MANUAL (flush 안 함) |
| 스냅샷 생성 | O (엔티티마다 복사본) | X (메모리 절약) |
| Dirty Checking | O (flush 시 비교 연산) | X (CPU 절약) |
| DB 힌트 | 없음 | `SET TRANSACTION READ ONLY` |
| Replica 라우팅 | Source로 라우팅 | Replica로 라우팅 가능 |

**대량 조회(수백~수천 건)에서 메모리와 CPU 차이가 체감된다.**

---

## 9. 언제 써야 하는가 / 쓰면 안 되는 경우

### 반드시 써야 하는 경우

```java
// 단순 조회
@Transactional(readOnly = true)
public List<Post> findAll() { ... }

// count 쿼리
@Transactional(readOnly = true)
public long count() { ... }

// 존재 여부 확인
@Transactional(readOnly = true)
public boolean existsByUsername(String username) { ... }
```

### 쓰면 안 되는 경우

```java
// 엔티티를 수정하는 메서드
@Transactional  // readOnly = false (기본값)
public void changeNickname(int id, String nickname) {
    Member member = memberRepository.findById(id).orElseThrow();
    member.setNickname(nickname);  // Dirty Checking으로 UPDATE 필요
}

// 이벤트 리스너에서 DB 작업을 하는 경우
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = REQUIRES_NEW)  // readOnly X → 쓰기 필요
public void handle(MemberJoinedEvent event) { ... }
```

### 주의: 조회 후 수정하는 패턴

```java
// 이런 메서드에 readOnly를 붙이면 수정이 반영되지 않는다
@Transactional(readOnly = true)  // 잘못된 사용!
public void updateAndReturn(int id) {
    Member member = memberRepository.findById(id).orElseThrow();
    member.setNickname("new");  // DB에 반영 안 됨 (flush 안 됨)
}
```

---

## 10. 핵심 정리

| 포인트 | 설명 |
|---|---|
| readOnly는 3단계로 최적화한다 | Hibernate flush, 스냅샷, DB 힌트 |
| 스냅샷을 생성하지 않아 메모리를 절약한다 | 대량 조회에서 효과적 |
| flush를 하지 않아 CPU를 절약한다 | Dirty Checking 비교 연산 생략 |
| DB가 쓰기를 거부할 수 있다 | PostgreSQL은 에러 발생, MySQL은 드라이버 의존 |
| Replica 라우팅의 키로 사용된다 | Source-Replica 구조에서 읽기 부하 분산 |
| 데이터를 변경하지 않는 메서드에는 반드시 붙인다 | 프로젝트 컨벤션 |
