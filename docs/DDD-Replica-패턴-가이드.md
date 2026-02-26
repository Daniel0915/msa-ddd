# DDD Replica 패턴 가이드 (바운디드 컨텍스트 간 데이터 복제)

## 1. Replica 패턴이란?

DDD에서 바운디드 컨텍스트 간 **강결합을 피하면서도 필요한 데이터를 로컬에 복제**하는 패턴이다.

### 문제 상황

```
[Member BC]                      [Post BC]
    Member                          Post
     ↑                               │
     │                               │
     └───────────────────────────────┘
         직접 FK 참조? → 강결합 발생!
```

**직접 참조의 문제점:**
- Member BC가 변경되면 Post BC도 영향 받음
- Member BC의 DB에 의존성 생김 (분산 환경에서 불가능)
- 트랜잭션 경계 문제 (다른 DB면 분산 트랜잭션 필요)

### 해결책: Replica 패턴

```
[Member BC]                      [Post BC]
    Member ─────이벤트────→    PostMember (복제본)
                                     ↑
                                     │
                                    Post
```

**장점:**
- 각 BC가 독립적인 DB 사용 가능
- Post BC는 Member BC의 내부 변경에 영향 받지 않음
- 로컬 조회로 성능 향상 (외부 API 호출 불필요)

---

## 2. 프로젝트 코드 분석

### 2.1 구조도

```
BaseMember (@MappedSuperclass)     ← 공통 필드 (username, password, nickname, ...)
    │
    ├── SourceMember               ← Member BC의 원본 데이터
    │       │                         - @GeneratedValue로 ID 자동 생성
    │       │                         - @CreatedDate로 날짜 자동 생성
    │       └── Member (@Entity)
    │
    └── ReplicaMember              ← Post BC의 복제 데이터
            │                         - ID를 생성자에서 받음 (복제이므로)
            └── PostMember (@Entity)  - createdDate를 생성자에서 받음
```

### 2.2 SourceMember (원본)

```java
// 경로: boundedContext/member/domain/SourceMember.java

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)  // JPA Auditing 활성화
@Getter
@NoArgsConstructor
public abstract class SourceMember extends BaseMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 자동 생성
    private int           id;

    @CreatedDate      // Spring Data JPA가 자동으로 현재 시간 설정
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime modifyDate;

    public SourceMember(String username, String password, String nickname) {
        super(username, password, nickname, 0);
    }
}
```

**특징:**
- `@GeneratedValue`: DB가 ID를 자동 생성 (원본이므로)
- `@CreatedDate`: JPA Auditing이 생성 시간 자동 설정
- 생성자에서 ID, 날짜를 받지 않음

### 2.3 ReplicaMember (복제본)

```java
// 경로: shared/member/domain/ReplicaMember.java

@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class ReplicaMember extends BaseMember {
    @Id
    private int           id;              // @GeneratedValue 없음!
    private LocalDateTime createdDate;     // @CreatedDate 없음! (필드명 주의)
    private LocalDateTime modifyDate;

    public ReplicaMember(int id, LocalDateTime createDate, LocalDateTime modifyDate,
                        String username, String password, String nickname, int activityScore) {
        super(username, password, nickname, activityScore);
        this.id = id;
        this.createdDate = createDate;     // 생성자에서 받아서 설정
        this.modifyDate = modifyDate;
    }
}
```

**특징:**
- `@GeneratedValue` 없음: ID를 외부에서 받아옴 (원본 Member의 ID)
- `@CreatedDate` 없음: 날짜를 외부에서 받아옴 (원본 Member의 날짜)
- **필드명 차이**: `createdDate` (과거분사형) - 복제된 데이터임을 강조

### 2.4 PostMember (Post BC의 Member 복제본)

```java
// 경로: boundedContext/post/domain/PostMember.java

@Entity
@Table(name = "POST_MEMBER")
@Getter
@NoArgsConstructor
public class PostMember extends ReplicaMember {
    public PostMember(int id, LocalDateTime createDate, LocalDateTime modifyDate,
                     String username, String password, String nickname, int activityScore) {
        super(id, createDate, modifyDate, username, password, nickname, activityScore);
    }
}
```

**테이블 생성:**
```sql
CREATE TABLE POST_MEMBER (
    id              INTEGER PRIMARY KEY,  -- Member BC의 ID를 그대로 사용
    created_date    TIMESTAMP,
    modify_date     TIMESTAMP,
    username        VARCHAR(255),
    password        VARCHAR(255),
    nickname        VARCHAR(255),
    activity_score  INTEGER
);
```

---

## 3. 필드명 차이와 오버라이딩 문제

### 3.1 문제 발생

```java
// BaseEntity.java
@MappedSuperclass
public abstract class BaseEntity {
    public abstract LocalDateTime getCreateDate();  // 메서드명: getCreateDate()
}

// SourceMember.java
private LocalDateTime createDate;  // 필드명: createDate
// → Lombok @Getter가 자동 생성: getCreateDate() ✅

// ReplicaMember.java
private LocalDateTime createdDate;  // 필드명: createdDate (d 추가!)
// → Lombok @Getter가 자동 생성: getCreatedDate() ❌
```

**문제:** ReplicaMember는 `getCreateDate()`가 아닌 `getCreatedDate()`를 생성하므로 BaseEntity의 추상 메서드를 구현하지 못함!

### 3.2 해결 방법 1: 오버라이딩

PostMember에서 직접 오버라이딩:

```java
@Entity
public class PostMember extends ReplicaMember {
    @Override
    public LocalDateTime getCreateDate() {
        return getCreatedDate();  // createdDate 필드를 반환
    }
}
```

### 3.3 해결 방법 2: ReplicaMember에서 오버라이딩 (권장)

모든 Replica 엔티티에서 중복을 피하려면 ReplicaMember에 추가:

```java
@MappedSuperclass
public abstract class ReplicaMember extends BaseMember {
    @Id
    private int           id;
    private LocalDateTime createdDate;  // 필드명은 그대로 유지
    private LocalDateTime modifyDate;

    @Override
    public LocalDateTime getCreateDate() {
        return createdDate;  // BaseEntity 요구사항 충족
    }

    // 복제된 데이터임을 명시하는 별도 getter 제공
    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
```

**필드명을 다르게 한 이유:**
- `createDate`: 원본 데이터 (현재 생성된 시간)
- `createdDate`: 복제 데이터 (과거에 생성된 시간을 복사한 것)
- 코드만 봐도 복제본임을 알 수 있음

---

## 4. 동기화 전략

### 4.1 이벤트 기반 동기화 (추천)

```java
// Member BC에서 이벤트 발행
@Entity
public class Member extends SourceMember {
    public int increaseActivityScore(int amount) {
        setActivityScore(getActivityScore() + amount);
        publishEvent(new MemberModifiedEvent(toDto()));  // 이벤트 발행
        return getActivityScore();
    }
}
```

```java
// Post BC에서 이벤트 수신
@Component
@RequiredArgsConstructor
public class MemberEventListener {
    private final PostMemberRepository postMemberRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberModified(MemberModifiedEvent event) {
        MemberDto member = event.getMember();

        PostMember postMember = postMemberRepository.findById(member.getId())
            .orElseGet(() -> new PostMember(
                member.getId(),
                member.getCreateDate(),
                member.getModifyDate(),
                member.getUsername(),
                member.getPassword(),
                member.getNickname(),
                member.getActivityScore()
            ));

        // 변경 사항 반영
        postMember.updateFrom(member);
        postMemberRepository.save(postMember);
    }
}
```

### 4.2 스케줄러 기반 동기화 (보조)

이벤트를 놓쳤을 경우를 대비한 백업:

```java
@Component
@RequiredArgsConstructor
public class PostMemberSyncScheduler {
    @Scheduled(fixedDelay = 60000)  // 1분마다
    public void syncMembers() {
        // Member BC의 API 호출 또는 Message Queue 확인
        // 누락된 동기화 처리
    }
}
```

---

## 5. Replica 패턴 사용 시 주의사항

### 5.1 데이터 일관성

```
Replica는 결과적 일관성(Eventual Consistency)을 따른다!
→ Member BC에서 변경 후 Post BC에 즉시 반영되지 않을 수 있음
```

**대응 방법:**
- 중요한 데이터는 실시간으로 Member BC API를 호출
- 덜 중요한 데이터만 Replica로 캐싱

### 5.2 복제할 데이터 선택

```java
// ❌ 나쁜 예: 모든 필드 복제
private String password;  // 민감 정보까지 복제

// ✅ 좋은 예: 필요한 필드만 복제
private String nickname;   // Post 작성자 표시용
private int activityScore; // 활동 점수 표시용
// password는 복제하지 않음!
```

### 5.3 ID 충돌 방지

```java
// ❌ PostMember ID를 자동 생성하면?
// Member BC의 Member(id=1)과 Post BC의 PostMember(id=1)이 다른 사람일 수 있음!

// ✅ 원본 Member의 ID를 그대로 사용
public PostMember(int id, ...) {
    this.id = id;  // Member BC의 ID를 그대로 받아옴
}
```

---

## 6. 대안 비교

| 방식 | 장점 | 단점 | 사용 시기 |
|---|---|---|---|
| **Replica 패턴** | 독립성, 성능, 확장성 | 결과적 일관성, 동기화 복잡도 | 읽기 많고 강한 일관성 불필요 |
| **API 호출** | 강한 일관성, 단순함 | 성능 저하, 장애 전파 | 중요한 데이터, 실시간 필요 |
| **공유 DB** | 강한 일관성 | 강결합, 확장 어려움 | 모놀리식 초기 단계 |
| **Event Sourcing** | 완전한 이력, 복구 가능 | 구현 복잡도 높음 | 금융, 이력 추적 필수 |

---

## 7. 핵심 정리

| 포인트 | 설명 |
|---|---|
| Replica는 독립성을 위한 패턴 | BC 간 강결합 없이 필요한 데이터만 복제 |
| SourceMember vs ReplicaMember | 원본은 자동 생성, 복제본은 생성자로 받음 |
| 필드명 차이로 의도 표현 | `createDate` (원본) vs `createdDate` (복제) |
| 오버라이딩 필요 | BaseEntity 요구사항 충족 위해 getCreateDate() 구현 |
| 이벤트 기반 동기화 | MemberModifiedEvent → PostMember 업데이트 |
| 결과적 일관성 | 즉시 동기화 보장 안 됨, 중요 데이터는 API 호출 |