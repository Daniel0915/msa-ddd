# Docker로 PostgreSQL 로컬 DB 띄우기

---

## 1. 개념 정리

| 용어 | 설명 |
|------|------|
| **Docker** | 프로그램을 격리된 가상 환경(컨테이너)에서 실행하는 도구 |
| **컨테이너** | Docker가 실행하는 격리된 가상 프로세스 (여기선 PostgreSQL 서버) |
| **이미지** | 컨테이너의 설계도. `postgres:16`은 Docker Hub에서 받아오는 공식 PostgreSQL 이미지 |
| **볼륨** | 컨테이너 삭제 후에도 데이터를 유지하기 위한 저장소 |
| **포트 포워딩** | `-p 5432:5432` → 내 PC의 5432포트를 컨테이너의 5432포트에 연결 |
| **docker-compose.yml** | 어떤 컨테이너를 어떤 설정으로 실행할지 정의하는 설정 파일 |

---

## 2. docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16                  # Docker Hub에서 PostgreSQL 16 이미지 사용
    container_name: msa-ddd-postgres    # 컨테이너 이름
    environment:
      POSTGRES_USER: postgres           # DB 접속 유저
      POSTGRES_PASSWORD: postgres       # DB 접속 비밀번호
      POSTGRES_DB: msa_ddd             # 최초 생성할 DB 이름
    ports:
      - "5432:5432"                     # 내PC포트:컨테이너포트
    volumes:
      - postgres_data:/var/lib/postgresql/data              # DB 데이터 영구 보존
      - ./docs/DDL.sql:/docker-entrypoint-initdb.d/DDL.sql  # 최초 실행 시 DDL 자동 실행
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres -d msa_ddd" ]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:   # 볼륨 선언 (데이터 영구 저장)
```

> **핵심 포인트**
> - `docker-entrypoint-initdb.d/` 경로에 `.sql` 파일을 마운트하면 DB 최초 생성 시 자동 실행됨
> - `volumes`의 `postgres_data`는 컨테이너를 삭제해도 DB 데이터가 보존됨

---

## 3. 접속 정보

| 항목 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `5432` |
| Database | `msa_ddd` |
| User | `postgres` |
| Password | `postgres` |

---

## 4. 자주 쓰는 명령어

```bash
# 컨테이너 백그라운드 실행 (-d: detached mode, 터미널을 점유하지 않음)
docker compose up -d

# 컨테이너 상태 확인 (STATUS가 healthy면 정상)
docker compose ps

# 로그 확인
docker compose logs postgres

# 실시간 로그 확인 (Ctrl+C로 종료)
docker compose logs -f postgres

# 컨테이너 중지 (데이터 볼륨은 유지)
docker compose down

# 컨테이너 + 데이터까지 완전 삭제 (DB 초기화)
docker compose down -v

# DB에 직접 접속해서 SQL 실행
docker exec msa-ddd-postgres psql -U postgres -d msa_ddd -c "\dt"
```

---

## 5. 흐름 정리

```
docker compose up -d
       │
       ▼
Docker Hub에서 postgres:16 이미지 다운로드 (최초 1회)
       │
       ▼
컨테이너 생성 및 시작
       │
       ▼
볼륨 데이터가 없으면? → DDL.sql 자동 실행 → 테이블 생성
볼륨 데이터가 있으면? → 기존 데이터 그대로 사용 (DDL 재실행 안 함)
       │
       ▼
localhost:5432 으로 접속 가능
```

---

## 6. 주의사항

- `docker compose down` → 컨테이너만 삭제, **데이터 보존**
- `docker compose down -v` → 컨테이너 + 볼륨 삭제, **데이터 초기화** (DDL 재실행됨)
- DDL.sql을 수정한 경우 반영하려면 `docker compose down -v` 후 다시 `up -d` 해야 함
- `docker-compose.yml`은 프로젝트 루트에 두는 것이 관례