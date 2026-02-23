# @MappedSuperclass 퀴즈

> 답변을 작성한 후 저에게 채점을 요청해주세요!

---

## Q1. 개념 문제

현재 프로젝트의 상속 구조는 `BaseEntity → BaseMember → SourceMember → Member` 입니다.
이 4개 클래스 중 **실제 DB 테이블이 생성되는 클래스**는 무엇이고, 그 이유는?

**답변:**

```
Member 클래스
나머지 클래스는 @MappedSuperclass 사용하여, 공통적인 필드 사용해서 사용할 뿐 테이블이 생성되지는 않아
```

---

## Q2. 코드 빈칸 채우기

아래 코드의 빈칸 **(A)**, **(B)** 에 들어갈 어노테이션을 작성하세요.

```java
( A )          // 테이블 생성 X, 매핑 정보만 상속
@Getter
public abstract class BaseEntity {
    public abstract int getId();
}

( B )          // 실제 테이블 생성
public class Member extends SourceMember {
    // ...
}
```

**답변:**

```
(A): @MappedSuperclass
(B): @Entity
```

---

## Q3. 코드 구현 문제

`Post` 바운디드 컨텍스트에 `PostMember` 엔티티를 만들려고 합니다.

- 테이블명: `POST_MEMBER`
- `BaseMember`를 상속받아 `username`, `nickname` 필드를 재사용
- 추가 필드: `postCount` (int)

**직접 클래스를 작성해보세요.**

**답변:**

```java
public class PostMember extends BaseMember {
    private final int postCount;
    
    public PostMember(String username, String nickname, int postCount) {
        super(username, nickname);
        this.postCount = postCount;
    }
}


```

---

## Q4. O/X 문제

다음 중 맞으면 O, 틀리면 X를 답하세요.

| 번호 | 문제 | 답변 |
|------|------|----|
| 1 | `@MappedSuperclass`가 붙은 클래스로 JPQL 조회가 가능하다. (`SELECT e FROM BaseEntity e`) | X  |
| 2 | `@MappedSuperclass`는 여러 단계로 중첩할 수 있다. (A → B → C 모두 @MappedSuperclass) | O  |
| 3 | `BaseMember`의 `@Setter(AccessLevel.PROTECTED)`는 어디서든 setter를 호출할 수 있게 한다. | X  |

---

## Q5. 설계 판단 문제

만약 "모든 회원 타입(Member, PostMember, CashMember)을 **하나의 쿼리로 조회**"해야 하는 요구사항이 생긴다면, `@MappedSuperclass` 대신 어떤 방식을 사용해야 하며 그 이유는?

**답변:**

```
모르겠음 
```

---

## 채점 결과

| 문제 | 결과 | 비고 |
|------|------|------|
| Q1 | ✅ 정답 | Member만 @Entity이므로 테이블 생성. 정확합니다! |
| Q2 | ✅ 정답 | (A) @MappedSuperclass, (B) @Entity 완벽합니다. |
| Q3 | ⚠️ 부분 정답 | 아래 해설 참고 |
| Q4 | ✅ 정답 | 3문제 모두 정확합니다! |
| Q5 | ❌ 모름 | 아래 해설 참고 |

**총점: 3.5 / 5**

---

### Q3 해설 (부분 정답)

상속 아이디어는 맞지만, 빠진 부분이 있습니다:

```java
// 작성한 답변
public class PostMember extends BaseMember {
    private final int postCount;
}
```

**빠진 것들:**
1. `@Entity` — 이게 없으면 JPA가 테이블을 생성하지 않습니다
2. `@Table(name = "POST_MEMBER")` — 문제에서 테이블명을 지정했습니다
3. `id`, `createDate`, `modifyDate` 필드 — BaseEntity에서 abstract로 선언되어 있으므로 구현 필요
4. `@NoArgsConstructor` — JPA 엔티티는 기본 생성자가 필수입니다

**모범 답안:**

```java
@Entity
@Table(name = "POST_MEMBER")
@Getter
@NoArgsConstructor
public class PostMember extends BaseMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @CreatedDate
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime modifyDate;

    private int postCount;
}
```

> 프로젝트의 `SourceMember`처럼 중간 클래스를 두는 방법도 있습니다.
> 핵심: **@Entity + @Table + JPA 기본 생성자**를 잊지 마세요!

---

### Q5 해설

**답: `@Entity` 상속 + `@Inheritance` 전략을 사용해야 합니다.**

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)  // 하나의 테이블에 모든 타입 저장
@DiscriminatorColumn(name = "member_type")             // 타입 구분 컬럼
public abstract class BaseMember {
    @Id
    private int id;
    private String username;
}

@Entity
@DiscriminatorValue("MEMBER")
public class Member extends BaseMember { }

@Entity
@DiscriminatorValue("POST_MEMBER")
public class PostMember extends BaseMember { }
```

이렇게 하면 다형성 쿼리가 가능합니다:

```java
// 모든 회원 타입을 한 번에 조회 가능!
em.createQuery("SELECT m FROM BaseMember m", BaseMember.class);
```

**@MappedSuperclass로는 불가능한 이유:**
- `@MappedSuperclass`는 JPA 엔티티가 아니므로 JPQL의 FROM 절에 사용할 수 없습니다
- 각 자식이 별도 테이블이라 하나의 쿼리로 묶을 수 없습니다

| 전략 | 테이블 구조 | 다형성 쿼리 |
|------|-----------|------------|
| `@MappedSuperclass` | 자식마다 별도 테이블 | **불가** |
| `SINGLE_TABLE` | 하나의 테이블 + 구분 컬럼 | **가능** |
| `JOINED` | 부모/자식 각각 테이블 + JOIN | **가능** |
| `TABLE_PER_CLASS` | 자식마다 별도 테이블 + UNION | **가능** |
