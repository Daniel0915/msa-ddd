# Gradle Kotlin DSL 가이드

## 1. Gradle이란?

Java/Kotlin 프로젝트의 **빌드 자동화 도구**이다.
의존성 관리, 컴파일, 테스트, 패키징 등을 자동으로 처리한다.

---

## 2. Groovy DSL vs Kotlin DSL

| 특성 | Groovy DSL (`build.gradle`) | Kotlin DSL (`build.gradle.kts`) |
|------|---------------------------|-------------------------------|
| 문법 | 동적 타이핑 | 정적 타이핑 |
| IDE 지원 | 제한적 | 자동완성, 타입 체크 완벽 |
| 파일 확장자 | `.gradle` | `.gradle.kts` |
| 문자열 | `'작은따옴표'` 가능 | `"큰따옴표"` 만 가능 |

현재 프로젝트는 **Kotlin DSL** (`build.gradle.kts`)을 사용한다.

---

## 3. 현재 프로젝트 빌드 파일

### build.gradle.kts

```kotlin
plugins {
    java                                                    // Java 플러그인
    id("org.springframework.boot") version "4.0.2"          // Spring Boot 플러그인
    id("io.spring.dependency-management") version "1.1.7"   // 의존성 버전 관리
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "msa-ddd"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)         // Java 25 사용
    }
}

repositories {
    mavenCentral()                                           // Maven Central에서 의존성 다운로드
}

dependencies {
    // 런타임 의존성
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-batch")

    // 컴파일 시에만 필요 (런타임에는 불필요)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // 개발 시에만 사용 (핫 리로드)
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // 런타임에만 필요 (H2 드라이버)
    runtimeOnly("com.h2database:h2")

    // 테스트 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.batch:spring-batch-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()                                       // JUnit 5 플랫폼 사용
}
```

### settings.gradle.kts

```kotlin
rootProject.name = "msa-ddd"     // 프로젝트 이름
```

---

## 4. Plugins 블록

```kotlin
plugins {
    java                                                    // ①
    id("org.springframework.boot") version "4.0.2"          // ②
    id("io.spring.dependency-management") version "1.1.7"   // ③
}
```

| 번호 | 플러그인 | 역할 |
|------|---------|------|
| ① | `java` | Java 컴파일, 테스트, JAR 생성 기본 태스크 제공 |
| ② | `spring-boot` | 실행 가능한 Fat JAR 생성, `bootRun` 태스크 제공 |
| ③ | `dependency-management` | Spring BOM으로 의존성 버전 자동 관리 |

---

## 5. 의존성 스코프 (Configuration)

### 5.1 implementation

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
```

- **컴파일 + 런타임** 모두 필요한 의존성
- 가장 일반적으로 사용

### 5.2 compileOnly

```kotlin
compileOnly("org.projectlombok:lombok")
```

- **컴파일 시에만** 필요, 런타임에는 불필요
- Lombok: 컴파일 시 코드 생성 후 런타임에는 필요 없음

### 5.3 annotationProcessor

```kotlin
annotationProcessor("org.projectlombok:lombok")
```

- **어노테이션 프로세서** 등록
- 컴파일 시 `@Getter`, `@Setter` 등을 처리하여 코드 생성

### 5.4 runtimeOnly

```kotlin
runtimeOnly("com.h2database:h2")
```

- **런타임에만** 필요, 컴파일 시에는 불필요
- JDBC 드라이버: JPA가 내부적으로 사용하므로 직접 코드에서 참조하지 않음

### 5.5 developmentOnly

```kotlin
developmentOnly("org.springframework.boot:spring-boot-devtools")
```

- **개발 환경에서만** 포함, 프로덕션 JAR에는 제외
- DevTools: 코드 변경 시 자동 재시작 (Hot Reload)

### 5.6 testImplementation / testRuntimeOnly

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

- `testImplementation`: 테스트 컴파일 + 실행 시 사용
- `testRuntimeOnly`: 테스트 실행 시에만 사용

### 스코프 정리

```
               컴파일    런타임    테스트
implementation    O        O        O
compileOnly       O        X        X
runtimeOnly       X        O        O
developmentOnly   O        O(dev)   X
testImplementation X       X        O
```

---

## 6. Java Toolchain

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

- 프로젝트가 사용할 **Java 버전을 명시적으로 지정**
- 시스템에 해당 JDK가 없으면 자동 다운로드 가능
- 팀원 간 Java 버전 불일치 방지

---

## 7. Spring Dependency Management

### 버전을 명시하지 않는 이유

```kotlin
// 버전 없이 선언 가능!
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
// Spring Boot 4.0.2가 알아서 호환되는 버전을 결정
```

`io.spring.dependency-management` 플러그인이 **Spring Boot BOM (Bill of Materials)** 을 적용하여, Spring Boot 버전과 호환되는 라이브러리 버전을 자동으로 관리한다.

```
Spring Boot 4.0.2 BOM:
├── spring-data-jpa: 자동 결정
├── hibernate: 자동 결정
├── jackson: 자동 결정
├── lombok: 자동 결정 (단, 별도 선언 필요)
└── ...
```

---

## 8. 주요 Gradle 명령어

```bash
# 빌드
./gradlew build              # 컴파일 + 테스트 + JAR 생성

# 실행
./gradlew bootRun            # Spring Boot 애플리케이션 실행

# 테스트
./gradlew test               # 테스트 실행

# 클린
./gradlew clean              # build 디렉토리 삭제

# 의존성 확인
./gradlew dependencies       # 전체 의존성 트리 출력

# 클린 빌드
./gradlew clean build        # 이전 빌드 삭제 후 새로 빌드
```

---

## 9. Groovy DSL 과의 문법 차이

### 의존성 선언

```groovy
// Groovy DSL
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

```kotlin
// Kotlin DSL
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
```

### 플러그인 선언

```groovy
// Groovy DSL
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.2'
}
```

```kotlin
// Kotlin DSL
plugins {
    java
    id("org.springframework.boot") version "4.0.2"
}
```

### 태스크 설정

```groovy
// Groovy DSL
test {
    useJUnitPlatform()
}
```

```kotlin
// Kotlin DSL
tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## 10. 핵심 정리

1. **Gradle Kotlin DSL**은 `build.gradle.kts` 파일로, 정적 타이핑과 IDE 자동완성을 지원한다
2. `implementation`, `compileOnly`, `runtimeOnly` 등 **스코프**로 의존성 사용 범위를 제어한다
3. `spring-dependency-management` 플러그인으로 **버전 자동 관리**를 받는다
4. Java Toolchain으로 **프로젝트 Java 버전**을 명시적으로 지정한다
5. `./gradlew bootRun`, `./gradlew build` 등의 명령어로 빌드/실행한다
