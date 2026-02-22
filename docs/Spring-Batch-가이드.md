# Spring Batch 가이드

## 1. Spring Batch란?

대용량 데이터를 **일괄 처리(Batch Processing)**하기 위한 프레임워크이다.
정해진 시간에 대량의 데이터를 읽고, 가공하고, 저장하는 작업을 처리한다.

```
예시: 매일 새벽 3시에 비활성 회원 정리, 월말 정산, 대량 알림 발송
```

---

## 2. 핵심 구조: Job → Step → (Reader → Processor → Writer)

```
Job (하나의 배치 작업)
├── Step 1 (데이터 처리 단위)
│   ├── ItemReader      ← 데이터 읽기 (DB, 파일, API)
│   ├── ItemProcessor   ← 데이터 가공/변환
│   └── ItemWriter      ← 데이터 저장/출력
│
├── Step 2
│   └── Tasklet         ← 단순 작업 (읽기-가공-저장이 아닌 경우)
│
└── Step 3 ...
```

---

## 3. 핵심 개념

### 3.1 Job

배치 처리의 최상위 단위. 하나 이상의 Step으로 구성된다.

```java
@Configuration
public class MemberCleanupJobConfig {

    @Bean
    public Job memberCleanupJob(JobRepository jobRepository, Step cleanupStep) {
        return new JobBuilder("memberCleanupJob", jobRepository)
            .start(cleanupStep)
            .build();
    }
}
```

### 3.2 Step

Job 내의 실제 처리 단위. **Chunk 방식** 또는 **Tasklet 방식**으로 구현한다.

#### Chunk 방식 (대량 데이터 처리)

데이터를 일정 크기(chunk)로 나누어 처리한다. 트랜잭션은 chunk 단위로 관리된다.

```java
@Bean
public Step cleanupStep(JobRepository jobRepository,
                         PlatformTransactionManager transactionManager,
                         ItemReader<Member> reader,
                         ItemProcessor<Member, Member> processor,
                         ItemWriter<Member> writer) {
    return new StepBuilder("cleanupStep", jobRepository)
        .<Member, Member>chunk(100, transactionManager)  // 100건씩 처리
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
}
```

```
chunk(100) 동작 흐름:

[Reader] 1건씩 읽기 × 100번
    ↓
[Processor] 1건씩 가공 × 100번
    ↓
[Writer] 100건 한번에 저장
    ↓
[트랜잭션 커밋]
    ↓
다음 100건 반복...
```

#### Tasklet 방식 (단순 작업)

```java
@Bean
public Step simpleStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager) {
    return new StepBuilder("simpleStep", jobRepository)
        .tasklet((contribution, chunkContext) -> {
            log.info("단순 작업 실행");
            // 파일 삭제, 테이블 정리 등
            return RepeatStatus.FINISHED;
        }, transactionManager)
        .build();
}
```

### 3.3 ItemReader

데이터를 읽어오는 역할.

```java
// DB에서 읽기
@Bean
public JpaPagingItemReader<Member> memberReader(EntityManagerFactory emf) {
    return new JpaPagingItemReaderBuilder<Member>()
        .name("memberReader")
        .entityManagerFactory(emf)
        .queryString("SELECT m FROM Member m WHERE m.activityScore = 0")
        .pageSize(100)     // 100건씩 페이징 조회
        .build();
}
```

### 3.4 ItemProcessor

데이터를 가공/변환/필터링하는 역할.

```java
@Bean
public ItemProcessor<Member, Member> memberProcessor() {
    return member -> {
        if (member.isSystem()) {
            return null;   // null 반환 → 해당 건 건너뜀 (필터링)
        }
        // 가공 로직
        return member;
    };
}
```

### 3.5 ItemWriter

가공된 데이터를 저장하는 역할.

```java
@Bean
public JpaItemWriter<Member> memberWriter(EntityManagerFactory emf) {
    return new JpaItemWriterBuilder<Member>()
        .entityManagerFactory(emf)
        .build();
}
```

---

## 4. Job 실행과 메타 테이블

### 4.1 메타 테이블

Spring Batch는 실행 이력을 자동으로 관리한다.

| 테이블 | 역할 |
|---|---|
| `BATCH_JOB_INSTANCE` | Job의 논리적 실행 단위 |
| `BATCH_JOB_EXECUTION` | Job의 실제 실행 이력 (성공/실패) |
| `BATCH_STEP_EXECUTION` | Step별 실행 이력 |
| `BATCH_JOB_EXECUTION_PARAMS` | Job 실행 시 전달된 파라미터 |

### 4.2 재시작과 멱등성

```
1차 실행: Step1 성공 → Step2 실패 → Job 실패
2차 실행: Step1 건너뜀 → Step2 재시도 → Job 성공
```

- 같은 Job + 같은 파라미터로 재실행 시, 실패한 Step부터 이어서 실행
- 이미 성공한 Step은 건너뜀

---

## 5. 스케줄링

배치 Job을 주기적으로 실행하려면 스케줄러와 연동한다.

```java
@Configuration
@EnableScheduling
public class BatchScheduler {
    private final JobLauncher jobLauncher;
    private final Job memberCleanupJob;

    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
    public void runCleanupJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("datetime", LocalDateTime.now().toString())
            .toJobParameters();
        jobLauncher.run(memberCleanupJob, params);
    }
}
```

---

## 6. Chunk vs Tasklet 선택 기준

| 기준 | Chunk | Tasklet |
|---|---|---|
| 데이터 양 | 대량 (수천~수백만) | 소량 또는 단순 작업 |
| 처리 방식 | 읽기→가공→저장 반복 | 한번에 처리 |
| 트랜잭션 | chunk 단위로 커밋 | 전체가 하나의 트랜잭션 |
| 재시작 | chunk 단위로 이어서 실행 | 전체 재실행 |
| 예시 | 회원 데이터 마이그레이션 | 임시 파일 삭제, 통계 집계 |

---

## 7. 핵심 정리

| 개념 | 설명 |
|---|---|
| Job | 배치 작업의 최상위 단위 |
| Step | 실제 처리 단위 (Chunk 또는 Tasklet) |
| Chunk | Reader → Processor → Writer, N건씩 트랜잭션 |
| 메타 테이블 | 실행 이력 관리, 실패 시 재시작 지원 |
| 스케줄링 | `@Scheduled` + `cron`으로 주기 실행 |
