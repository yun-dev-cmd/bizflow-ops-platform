# BizFlow Operations Platform

BizFlow Operations Platform은 외부 기관(금융 결제망 및 공공 데이터 포털)과의 대용량 파일/API 연계 처리를 자동화하고, 내부 정산 요청 내역과 외부 연계 실적 데이터 간의 정합성을 실시간 대조(Reconciliation) 및 모니터링하기 위한 통합 관리 플랫폼입니다.

---

## 🛠️ Key Features (핵심 기능)

1. **업무 요청 및 정산증빙 프로세스 관리**
   - 사용자별 권한(`ADMIN`, `OPERATOR`, `USER`) 등급에 따른 메뉴 접근 및 API 제어.
   - 신규 정산 요청 등록, 증빙서류 업로드(S3 연계), 담당자 배정 및 최종 승인/반려 상태 전이 파이프라인.
2. **Spring Batch 5.x 기반 대용량 데이터 연계**
   - 청크 지향 처리(Chunk-oriented Processing)를 적용해 대량 정산 거래 내역 로드 시 부하 조절.
   - 배치 가동 상태, 처리 이력, 성공/실패 여부를 메타데이터 테이블 및 이력 테이블에 자동 관리.
3. **정합성 대조 검증 (Reconciliation) 및 실패 재처리 (Retry)**
   - 승인 완료된 정산 데이터와 외부 수신 거래 실적을 일대일로 비교 검증.
   - 불일치 발생 시 즉각 실패 기록을 남기고 배치를 중단하며, 조치 후 운영자가 대시보드에서 즉각 **수동 재처리(Retry)**할 수 있는 복구 환경 제공.
4. **S3 파일 연계 및 장애 극복 (Local Fallback)**
   - AWS S3 스토리지 연동 파일 백업 처리.
   - S3 등 외부 인프라 장애 발생 시 시스템 중단 없이 로컬 백업 저장소(`uploads/`)로 자동 우회 작동하는 장애 방어형 설계.
5. **실시간 관제 웹 대시보드**
   - 실시간 연계 현황 요약 스탯, 정산 검토 테이블, 배치 기동/재기동 제어 버튼, 그리고 텍스트 로그 터미널 모니터 포함.

---

## 📂 Project Structure (디렉토리 구조)

```text
E:\Aws (Root)
├── backend/                       # 스프링 부트 백엔드 모듈
│   ├── build.gradle               # 의존성 및 빌드 설정
│   ├── settings.gradle
│   ├── Dockerfile                 # 컨테이너 빌드 이미지 설정
│   └── src/
│       └── main/
│           ├── java/              # Java 소스 코드
│           └── resources/
│               ├── application.yml # 환경 설정 (DB, AWS, JWT)
│               └── static/        # 관제 대시보드 웹 UI 리소스 (HTML/CSS/JS)
├── docs/                          # 시스템 문서 폴더
│   └── architecture.md            # 시스템 아키텍처 및 정합성 대조 흐름 정의서
├── docker-compose.yml             # 로컬 테스트 및 DB/App 통합 가동 스크립트
└── README.md                      # 본 문서
```

---

## 🚀 Quick Start (로컬 가동 및 테스트)

### 1. 전제 조건
* 로컬 PC에 **Java 17** 및 **Docker**가 설치되어 있어야 합니다.

### 2. 컨테이너 가동
루트 디렉토리에서 아래 명령어를 실행하여 PostgreSQL 및 Spring Boot 앱을 동시에 실행합니다.
```bash
docker-compose up --build -d
```
* **PostgreSQL Port**: `5432` (Database: `batchdb`, User: `postgres`, Password: `password`)
* **Spring Boot App Port**: `8080`

### 3. 웹 서비스 및 API 문서 접속
* **대시보드 UI**: `http://localhost:8080/`
* **Swagger API Docs**: `http://localhost:8080/swagger-ui.html`

### 4. 테스트 로그인 정보 (비밀번호: password)
* **관리자(Admin)**: `admin`
* **운영자(Operator)**: `operator`
* **일반 관찰자(User)**: `user`
