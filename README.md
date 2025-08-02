# BizFlow Operations Platform

BizFlow Operations Platform은 기업 내부 정산 프로세스와 외부 연계 기관(금융망 등)의 실적 데이터를 안전하게 연계하고, 데이터 간 정합성을 실시간 대조(Reconciliation)하여 검증 및 재처리할 수 있는 실무형 운영 관리 시스템입니다.

단순한 CRUD 수준을 넘어 공공/금융 분야에서 흔히 사용되는 **정합성 대조 프로세스**, **Spring Batch 기반 배치 연계**, **장애 복구 Fallback 아키텍처**를 포트폴리오용으로 통합 설계하였습니다.

---

## 🛠️ 기술 스택

* **Language**: Java 17
* **Framework**: Spring Boot 3.2.5
* **Build Tool**: Gradle
* **Security**: Spring Security & JWT 인증 (JSON Web Token)
* **Data Access**: Spring Data JPA
* **Database**: PostgreSQL 15 (Alpine)
* **Batch Engine**: Spring Batch 5.x / Spring Scheduler
* **API Documentation**: Springdoc OpenAPI (Swagger UI)
* **Storage Interface**: AWS S3 SDK (Mock 클라이언트 내장) & 로컬 파일시스템 Fallback
* **Containerization**: Docker & Docker Compose
* **Test Tool**: JUnit 5 & AssertJ

---

## 🔐 권한별 기능 (Role Matrix)

시스템 보안을 위해 3단계 역할(`ADMIN`, `OPERATOR`, `USER`) 권한을 제공하며, 각 역할에 맞는 API 접근이 통제됩니다.

* **ADMIN (관리자)**
  - 모든 정산 및 업무 요청 내역 전체 조회 가능
  - 정산 요청 심사 (승인 및 반려) 가능
  - 정합성 검증 배치 실행 가능
  - **실패 배치 건 수동 재처리(Retry) 가능 (ADMIN 전용 기능)**
* **OPERATOR (운영자)**
  - 모든 정산 및 업무 요청 내역 전체 조회 가능
  - 정산 요청 담당자 배정 가능
  - 정산 요청 심사 (승인 및 반려) 가능
  - 정합성 검증 배치 실행 가능 (재처리 실행은 불가)
* **USER (일반 사용자)**
  - 본인이 등록한 정산 요청서 작성 및 업로드 가능
  - 본인이 등록한 정산 건 및 증빙에 대해서만 조회 가능
  - 본인 요청 건에 증빙 파일 업로드 가능
  - 승인/반려/배정 및 배치 기동 제어 일체 불가

---

## 📊 정합성 검증 규칙 (Reconciliation Logic)

배치 엔진은 다음 5가지 시나리오 규칙을 기준으로 대조(Reconciliation)를 실시하여 정합성 위배 사항을 완전하게 필터링합니다.

| 규칙 | 구분 | 조건 및 판정 기준 | 정합성 코드 (reconciliationStatus) |
| :--- | :--- | :--- | :---: |
| **규칙 1** | **정상 일치** | `APPROVED` 내부 정산 요청과 외부 실적이 존재하고 금액이 같음 | **MATCHED** |
| **규칙 2** | **금액 불일치** | `APPROVED` 내부 정산 요청과 외부 실적이 존재하나 금액이 다름 | **MISMATCHED** |
| **규칙 3** | **외부 실적 누락** | `APPROVED` 내부 정산 요청이 존재하지만 외부 실적 데이터가 없음 | **MISSING_EXTERNAL** |
| **규칙 4** | **내부 요청 누락** | 내부 정산 요청 기록이 존재하지 않으나 외부 연계 실적만 존재함 | **UNKNOWN_EXTERNAL** |
| **규칙 5** | **미승인 거래 존재** | 내부 정산 요청 상태가 `APPROVED`가 아님(REQUESTED 등)에도 외부 실적이 존재함 | **INVALID_STATUS** |

---

## 🚀 핵심 동작 시나리오

1. **사용자 로그인**: 일반 사용자(`user`), 운영자(`operator`), 관리자(`admin`) 중 하나로 로그인하여 JWT 토큰을 획득합니다.
2. **정산 요청 및 파일 첨부**: 일반 사용자가 신규 정산 요청을 등록하고 증빙 서류를 업로드합니다.
3. **담당자 배정 및 최종 승인**: 운영자가 해당 정산 요청 건에 담당 운영자를 배정하고 서류 검증 후 최종 '승인(APPROVED)' 처리합니다.
4. **외부 실적 데이터 Mock 유입**: 배치 연계 대조를 위해 외부 결제 기관에서 수신된 거래 실적 Mock 데이터를 생성합니다. (5대 규칙 검증용 시나리오 데이터)
5. **정합성 대조 배치 실행**: 정합성 검증 배치를 작동시켜 실적 대조를 실시하고 `ReconciliationResult` 테이블에 상세 결과를 적재합니다. (정합성 에러 발생 시 배치는 실패 처리됨)
6. **대시보드 모니터링**: 대시보드 스탯 요약 카드, 배치 로그 이력, 정합성 위배 상세 내역을 한눈에 파악합니다.
7. **실패 건 재처리 (ADMIN 전용)**: 정합성 미달로 FAILED 처리된 배치 건을 관리자가 예외 조치 후 즉시 수동 **재처리(Retry)**하여 대조 완료 상태로 갱신합니다.

---

## 🖥️ 주요 API 명세

### 1. 인증 및 계정 (Auth)
* `POST /api/auth/login` : 사용자 로그인 및 JWT 발급
* `POST /api/auth/signup` : 신규 사용자 가입 등록

### 2. 정산 업무 관리 (Settlement)
* `POST /api/settlements` : 신규 정산 요청 등록
* `GET /api/settlements` : 정산 요청 목록 조회 (USER는 본인 건만 조회)
* `GET /api/settlements/{id}` : 정산 요청 상세 조회
* `PATCH /api/settlements/{id}/assign` : 담당 운영자 배정
* `PATCH /api/settlements/{id}/approve` : 정산 최종 승인 처리
* `PATCH /api/settlements/{id}/reject` : 정산 최종 반려 처리

### 3. 증빙 관리 (Attachment)
* `POST /api/settlements/{id}/attachments` : 정산 건 증빙 파일 첨부 업로드
* `GET /api/settlements/{id}/attachments` : 정산 건 첨부파일 메타데이터 조회
* `GET /api/settlements/{id}/attachments/download` : 증빙 파일 다운로드

### 4. 외부 실적 연계 Mock (External Result Mock)
* `POST /api/external-results/mock` : 테스트용 외부 실적 데이터 수동 생성
* `GET /api/external-results` : 수신된 외부 실적 전체 조회

### 5. 정합성 및 배치 제어 (Reconciliation & Batch)
* `POST /api/batches/reconciliation/run` : 정합성 대조 검증 배치 실행 (mockFailure 옵션 지원)
* `POST /api/batches/reconciliation/retry` : 실패한 배치 강제 수동 재기동 (ADMIN 전용)
* `GET /api/batches/logs` : 배치 작업 이력 전체 조회
* `GET /api/reconciliation-results` : 정합성 검증 상세 결과 목록 조회

### 6. 대시보드 통계 (Dashboard)
* `GET /api/dashboard/summary` : 대시보드 요약 통계 정보 조회

---

## 💾 Local Fallback & S3 인프라 아키텍처

파일 업로드 및 다운로드 서비스는 클라우드 분리 구조로 설계되었습니다.
1. 기본적으로 AWS S3 스토리지에 파일을 업로드하도록 작동합니다.
2. 만약 S3 접속 오류, 자격 증명 누락 등의 예외 상황(Exception)이 감지되면 즉각 **로컬 파일시스템(./uploads)** 디렉토리로 백업 저장하는 Fallback 로직이 구동됩니다.
3. 메타데이터 테이블(`attachments`)의 `storageType` 컬럼에 `S3_MOCK` 또는 `LOCAL` 이 영속화되어, 다운로드 요청 시 유연하게 복구 동작이 이어집니다.

---

## 🏃 실행 방법 (Quick Start)

### 1. 전제 조건
* 시스템에 **Docker** 및 **Docker Compose**가 기동 중이어야 합니다.

### 2. 가동 및 컴파일
프로젝트 루트 디렉토리(docker-compose.yml이 위치한 경로)에서 아래의 명령을 수행합니다.
```bash
docker-compose up --build
```
PostgreSQL 데이터베이스 컨테이너와 Spring Boot 애플리케이션 컨테이너가 빌드된 후 순차적으로 실행됩니다.

* **관제 대시보드 UI**: `http://localhost:8080/`
* **Swagger API 명세**: `http://localhost:8080/swagger-ui.html`

---

## 🔑 테스트용 기본 Seed 계정
시스템 가동 시 로컬 환경에서의 즉각적인 테스트를 돕기 위해 3개의 역할별 테스트 계정이 자동 생성(Data Seeding)됩니다. (비밀번호는 모두 `password`입니다.)

* **관리자(ADMIN)** 사원 계정: `admin` / `password`
* **운영자(OPERATOR)** 사원 계정: `operator` / `password`
* **일반사용자(USER)** 사원 계정: `user` / `password`

---

## 🌟 포트폴리오 핵심 강조점

1. **금융/공공 비즈니스 모델 설계**: 실무 정합성 검증의 5대 핵심 시나리오(MATCHED, MISMATCHED, MISSING, UNKNOWN, INVALID)를 충실하게 모델링하고 배치 스텝으로 자동화하였습니다.
2. **장애 방어형 파일 서비스 (Fallback)**: 클라우드 인프라 장애 발생을 대비한 Local Fallback 구조 설계로 시스템 연속성을 보장합니다.
3. **보안 인프라**: JWT 무상태 세션 인증을 이용하며, Spring Security 및 `@PreAuthorize`로 역할별 API 보안을 철저하게 분기 통제합니다.
4. **Spring Batch 5.x 정합**: Spring Boot 3.x 환경에 대응하는 Spring Batch 5.x JobRepository 아키텍처를 온전히 수용하여 빌드하였습니다.

---

## API 연결 및 플로우 검증 결과 (2026-06-30)

Static dashboard에서 호출하는 핵심 API와 Spring Boot 구현 연결 상태를 점검했습니다. 아래 API는 Controller, DTO, Service/Repository, Entity 계층이 실제로 연결되어 있으며 `SettlementVerificationTest.readmeDashboardFlowWorksThroughBackendApis()`에서 USER 요청 등록 → OPERATOR 승인 → 외부 Mock 생성 → 정합성 검증 → 대시보드 갱신 → 실패 배치 재처리 흐름으로 검증했습니다.

| API | 구현 상태 |
| :--- | :--- |
| `POST /api/auth/login` | `AuthController` → `AuthServiceImpl` → `UserRepository` / `User` |
| `GET /api/dashboard/summary` | `DashboardController` → `SettlementRequestRepository`, `ReconciliationResultRepository`, `BatchJobLogRepository` |
| `POST /api/settlements` | `SettlementController` → `SettlementServiceImpl` → `SettlementRequestRepository` / `SettlementRequest` |
| `GET /api/settlements` | `SettlementController` → `SettlementServiceImpl` → `SettlementRequestRepository` |
| `PATCH /api/settlements/{id}/assign` | `SettlementController` → `SettlementServiceImpl` → `UserRepository`, `SettlementRequestRepository` |
| `PATCH /api/settlements/{id}/approve` | `SettlementController` → `SettlementServiceImpl` → `SettlementRequestRepository` |
| `PATCH /api/settlements/{id}/reject` | `SettlementController` → `SettlementServiceImpl` → `SettlementRequestRepository` |
| `POST /api/settlements/{id}/attachments` | `SettlementController` → `SettlementServiceImpl` → `FileServiceImpl` → `AttachmentRepository` / `Attachment` |
| `POST /api/external-results/mock` | `ExternalMockController` → `ExternalResultRepository` / `ExternalResult` |
| `POST /api/batches/reconciliation/run` | `BatchReconciliationController` → `BatchMonitoringServiceImpl` → `settlementVerificationJob` |
| `POST /api/batches/reconciliation/retry` | `BatchReconciliationController` → `BatchMonitoringServiceImpl` → `BatchJobLogRepository` |
| `GET /api/batches/logs` | `BatchReconciliationController` → `BatchMonitoringServiceImpl` → `BatchJobLogRepository` |
| `GET /api/reconciliation-results` | `BatchReconciliationController` → `BatchMonitoringServiceImpl` → `ReconciliationResultRepository` |

검증 중 Docker fresh DB 기동 기준으로 막힐 수 있는 항목도 보강했습니다.

* `docker-compose.yml`에 PostgreSQL healthcheck와 app `depends_on: condition: service_healthy`를 추가했습니다.
* prod 프로파일에서 fresh Postgres 테이블 생성을 위해 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 주입하도록 했습니다.
* Docker 검증 환경에서는 AWS S3 대신 `AWS_S3_LOCAL_ONLY=true`로 첨부 파일을 로컬 저장소에 저장하도록 했습니다.
* demo mismatch seed 데이터가 신규 정상 플로우를 실패처럼 보이게 하지 않도록 `APP_SEED_DEMO_SCENARIOS=false`를 Docker Compose 기본값으로 설정했습니다. 기본 계정 `admin` / `operator` / `user`는 계속 생성됩니다.
* `mockFailure=true` 배치 실행 옵션이 실제 `settlementVerificationJob`에서 예외를 발생시키도록 연결해 대시보드의 실패/재처리 시나리오가 의미 있게 동작하도록 했습니다.

실행한 검증 명령:

```bash
node --check backend/src/main/resources/static/js/dashboard.js
gradle test --no-daemon --stacktrace
```

검증 결과:

* `node --check` 통과
* `gradle test` 통과: `tests=2`, `failures=0`, `errors=0`
* 통과한 테스트 플로우: `POST /api/auth/login` → `POST /api/settlements` → `GET /api/settlements` → `POST /api/settlements/{id}/attachments` → `PATCH /api/settlements/{id}/assign` → `PATCH /api/settlements/{id}/approve` → `POST /api/external-results/mock` → `POST /api/batches/reconciliation/run` → `GET /api/reconciliation-results` → `GET /api/batches/logs` → `GET /api/dashboard/summary` → 별도 요청 `PATCH /api/settlements/{id}/reject` → 실패 배치 생성 → `POST /api/batches/reconciliation/retry` → 대시보드 재확인

현재 Codex 실행 환경에는 Docker CLI가 설치되어 있지 않아 `docker compose up --build` 자체는 이 머신에서 실행하지 못했습니다 (`docker` 명령 없음). Docker Desktop이 있는 환경에서는 아래 명령으로 동일 설정을 실행하면 됩니다.

```bash
docker compose up --build
```
