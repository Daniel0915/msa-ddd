# @MappedSuperclass 가이드 (프로젝트 코드 기반)

## 1. @MappedSuperclass란?

JPA에서 **공통 매핑 정보를 상속으로 제공**하기 위한 어노테이션이다.
`@MappedSuperclass`가 붙은 클래스는 **테이블이 생성되지 않고**, 자식 엔티티가 해당 필드를 자신의 테이블 컬럼으로 포함한다.

---

## 2. 현재 프로젝트 상속 구조

```
BaseEntity (@MappedSuperclass)          ← 전역 공통 필드 (id, createDate, modifyDate)
    │
    └── BaseMember (@MappedSuperclass)  ← Member 도메인 공통 필드 (username, password, ...)
            │
            └── SourceMember (abstract) ← 확장 포인트
                    │
                    └── Member (@Entity) ← 실제 테이블 생성
```

**테이블은 `Member` 하나만 생성**되며, 상위 클래스들의 모든 필드가 `member` 테이블의 컬럼이 된다.

### 생성되는 테이블 구조

```sql
CREATE TABLE member (
    -- BaseEntity에서 상속
    id              SERIAL PRIMARY KEY,
    create_date     TIMESTAMP,
    modify_date     TIMESTAMP,
    -- BaseMember에서 상속
    username        VARCHAR(255) UNIQUE,
    password        VARCHAR(255),
    nickname        VARCHAR(255),
    activity_score  INTEGER
);
```

---

## 3. 프로젝트 코드 분석

### 3.1 BaseEntity (전역 공통)

```java
// 경로: global/jpa/entity/BaseEntity.java

@MappedSuperclass   // 테이블 생성 X, 매핑 정보만 자식에게 제공
@Getter
public abstract class BaseEntity implements HasModelTypeCode {
    public abstract int getId();
    public abstract LocalDateTime getCreateDate();
    public abstract LocalDateTime getModifyDate();

    @Override
    public String getModelTypeCode() {
        return this.getClass().getSimpleName();  // "Member" 등 클래스명 반환
    }

    // 도메인 이벤트 발행 - 모든 엔티티에서 사용 가능
    protected void publishEvent(Object event) {
        GlobalConfig.getEventPublisher().publish(event);
    }
}
```

**역할:**
- 모든 엔티티의 **최상위 부모**
- `id`, `createDate`, `modifyDate`를 추상 메서드로 강제
- `publishEvent()`로 어떤 엔티티든 도메인 이벤트를 발행할 수 있게 함
- `getModelTypeCode()`로 엔티티 타입 식별

### 3.2 BaseMember (도메인 공통)

```java
// 경로: boundedContext/member/domain/BaseMember.java

@MappedSuperclass   // 이것도 테이블 생성 X
@Getter
@Setter(value = AccessLevel.PROTECTED)  // 외부에서 직접 수정 불가
@NoArgsConstructor
public abstract class BaseMember extends BaseEntity {
    @Column(unique = true)
    private String username;
    private String password;
    private String nickname;
    private int activityScore;

    public boolean isSystem() { return "system".equals(username); }
}
```

**역할:**
- Member 바운디드 컨텍스트의 **공통 필드 정의**
- `@Setter(AccessLevel.PROTECTED)` → DDD에서 외부 직접 수정 방지 (캡슐화)

### 3.3 Member (실제 엔티티)

```java
// 경로: boundedContext/member/domain/Member.java

@Entity   // 이 클래스만 테이블 생성!
public class Member extends SourceMember {
    // 상위 클래스의 모든 필드가 member 테이블에 매핑됨
}
```

---

## 4. @MappedSuperclass vs @Entity 상속 vs @Embeddable

### 4.1 비교표

| 항목 | @MappedSuperclass | @Entity 상속 (SINGLE_TABLE) | @Embeddable |
|---|---|---|---|
| 테이블 생성 | X (자식 테이블에 포함) | O (상속 전략에 따라) | X (포함하는 테이블에 포함) |
| 다형성 쿼리 | X | O | X |
| 관계 | 부모로 직접 조회/참조 불가 | 부모 타입으로 조회 가능 | 값 객체로 사용 |
| 용도 | 공통 필드 재사용 | 상속 계층 매핑 | 값 타입 그룹화 |

### 4.2 @MappedSuperclass를 선택해야 하는 경우

```java
// 이런 쿼리가 필요하면 → @Entity 상속
// "모든 BaseEntity를 조회" 같은 다형성 쿼리
em.createQuery("SELECT e FROM BaseEntity e");  // @MappedSuperclass로는 불가능!

// 이런 용도면 → @MappedSuperclass (현재 프로젝트)
// 공통 필드만 재사용하고, 부모 타입으로 직접 조회할 일이 없음
```

### 4.3 언제 무엇을 쓰는가

```
"공통 필드(id, 날짜 등)를 재사용하고 싶다"
    → @MappedSuperclass (현재 프로젝트의 BaseEntity)

"상속 관계 자체를 DB에 매핑하고 다형성 쿼리가 필요하다"
    → @Entity + @Inheritance

"주소(city, street, zipcode) 같은 값 객체를 묶고 싶다"
    → @Embeddable + @Embedded
```

---

## 5. 현재 프로젝트 이벤트 발행 구조

`BaseEntity`에 `publishEvent()`가 있으므로 **모든 엔티티에서 도메인 이벤트 발행이 가능**하다.

### 호출 흐름

```
Member (엔티티)
    │
    └── publishEvent(event)                    ← BaseEntity에서 상속
            │
            └── GlobalConfig.getEventPublisher()   ← static으로 접근
                    │
                    └── EventPublisher.publish(event)
                            │
                            └── ApplicationEventPublisher.publishEvent(event)
                                    │
                                    └── @EventListener / @TransactionalEventListener 수신
```

### 사용 예시

```java
@Entity
public class Member extends SourceMember {

    public void changeNickname(String newNickname) {
        setNickname(newNickname);
        // BaseEntity의 publishEvent() 사용
        publishEvent(new MemberNicknameChangedEvent(this.getId(), newNickname));
    }
}
```

---

## 6. 핵심 정리

| 포인트 | 설명 |
|---|---|
| `@MappedSuperclass`는 테이블을 만들지 않는다 | 매핑 정보만 자식에게 상속 |
| `@Entity`가 붙은 클래스만 테이블이 생성된다 | `Member`만 테이블 생성 |
| 다형성 쿼리가 불가하다 | `BaseEntity` 타입으로 JPQL 조회 불가 |
| DDD에서 공통 인프라 계층에 적합하다 | `id`, `createDate`, `modifyDate`, `publishEvent()` |
| 여러 단계로 중첩 가능하다 | `BaseEntity` → `BaseMember` → `SourceMember` → `Member` |
