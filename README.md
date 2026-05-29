# JobMate

## 개요

JobMate는 **Spring Boot + Thymeleaf + JPA** 기반의 취업 지원 프로젝트입니다.  
회원 프로필을 기반으로 채용공고를 추천하고, 공고 상세/기업 정보 조회, 지원 내역 관리, 자기소개서 및 면접 보조 기능 등을 제공합니다.

이 문서는 실제 작업자가 프로젝트를 실행하고, 설정을 확인하고, 테스트를 수행할 때 필요한 내용을 중심으로 정리했습니다.

---

## 주요 기능

- 회원가입 / 로그인 / 비밀번호 재설정
- 회원 프로필 설정
- 맞춤 채용공고 추천
- 공고 상세 및 기업 정보 조회
- 지원 내역 관리
- 자기소개서 관리 및 AI 피드백
- 면접 질문/피드백
- 커뮤니티 게시글 / 댓글 / 좋아요
- CSV 기반 채용공고 적재

---

## 기술 스택

### Backend
- Java
- Spring Boot
- Spring Web / Spring MVC
- Spring Data JPA
- Spring Security
- Validation

### Database
- MySQL

### Build
- Gradle Wrapper

### External Integration
- OAuth2
- Mail
- 외부 API 연동(DART, NPS, AI 등)

---

## 프로젝트 구조

```text
src/
 ├─ main/
 │   ├─ java/com/ama/jobmate/
 │   │   ├─ controller/
 │   │   ├─ service/
 │   │   ├─ repository/
 │   │   ├─ entity/
 │   │   ├─ dto/
 │   │   └─ common/
 │   └─ resources/
 │       ├─ application.yml
 │       └─ templates/
 └─ test/
     └─ java/com/ama/jobmate/
```

---

## 실행 방법

### Windows PowerShell

```powershell
.\gradlew.bat bootRun
```

### Linux / macOS

```bash
./gradlew bootRun
```

---

## 빌드 방법

### Windows PowerShell

```powershell
.\gradlew.bat clean bootJar
```

### Linux / macOS

```bash
./gradlew clean bootJar
```

빌드 후 결과물은 일반적으로 아래 경로에 생성됩니다.

```text
build/libs/
```

---

## 테스트

이 프로젝트의 테스트는 현재 **핵심 서비스 3개를 중심으로 구성**합니다.

### 포함된 핵심 서비스 테스트
- `MemberService`
- `JobMatchService`
- `CsvImportService`

### 테스트 목적

#### 1. MemberService
회원 기능의 핵심 흐름을 검증합니다.

예:
- 회원가입 성공/실패
- 중복 이메일 처리
- 로그인 성공/실패
- 비밀번호 변경/재설정
- 프로필 저장

#### 2. JobMatchService
프로젝트의 핵심 추천 로직을 검증합니다.

예:
- 회원 프로필 기반 매칭 점수 계산
- 직무/기술스택/지역/연봉 조건 반영
- 추천 결과 정렬
- fallback 데이터 처리

#### 3. CsvImportService
운영 데이터 유입 경로를 검증합니다.

예:
- CSV 적재 성공
- 중복 공고 skip
- 잘못된 row skip
- 일부 row 오류 시 나머지 row 계속 처리

### 테스트 실행

전체 테스트:

```bash
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

특정 서비스 테스트만 실행할 수도 있습니다.

예:

```bash
./gradlew test --tests "com.ama.jobmate.service.MemberServiceTest"
./gradlew test --tests "com.ama.jobmate.service.JobMatchServiceTest"
./gradlew test --tests "com.ama.jobmate.service.CsvImportServiceTest"
```

Windows PowerShell:

```powershell
.\gradlew.bat test --tests "com.ama.jobmate.service.MemberServiceTest"
.\gradlew.bat test --tests "com.ama.jobmate.service.JobMatchServiceTest"
.\gradlew.bat test --tests "com.ama.jobmate.service.CsvImportServiceTest"
```

---

## 설정 파일 설명

이 프로젝트는 설정을 주로 `application.yml` 과 `.env` 기준으로 관리합니다.

---

## 1. application.yml

`src/main/resources/application.yml` 은 **Spring Boot 애플리케이션의 메인 설정 파일**입니다.

주요 역할:
- 서버 포트 설정
- 데이터베이스 연결 설정
- JPA 설정
- 메일 설정
- OAuth2 설정
- 외부 API Key 참조
- 로그 레벨 및 기타 애플리케이션 설정

### 보통 포함되는 항목 예시
- `server.port`
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.jpa.hibernate.ddl-auto`
- `spring.mail.*`
- `spring.security.oauth2.*`
- 사용자 정의 설정값(API Key 등)

### 주의사항
- 운영 비밀번호나 민감정보를 `application.yml` 에 직접 하드코딩하지 않는 것을 권장합니다.
- 가능하면 `.env` 또는 환경변수로 분리해서 참조합니다.
- 로컬/운영 설정이 다르면 profile 분리(`application-local.yml`, `application-prod.yml`)를 고려합니다.

---

## 2. .env

`.env` 파일은 **민감한 값이나 환경별 설정값을 분리하기 위한 파일**입니다.

주요 용도:
- DB 비밀번호 분리
- API Key 분리
- OAuth Client Secret 분리
- Mail 계정 정보 분리
- 배포 환경별 값 분리

### .env 에 넣기 좋은 값 예시
- DB URL
- DB USERNAME
- DB PASSWORD
- MAIL USERNAME
- MAIL PASSWORD
- OAUTH CLIENT ID / SECRET
- DART API KEY
- NPS API KEY
- AI API KEY

### 사용 이유
- 소스 코드에 민감정보를 남기지 않기 위해
- 로컬/운영 환경을 쉽게 분리하기 위해
- Git에 비밀번호가 올라가는 실수를 줄이기 위해

### 권장사항
- `.env` 는 `.gitignore` 에 포함합니다.
- `.env.example` 파일을 별도로 두고, 필요한 키 이름만 공유하는 방식이 좋습니다.

예:

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=
MAIL_USERNAME=
MAIL_PASSWORD=
OAUTH_GOOGLE_CLIENT_ID=
OAUTH_GOOGLE_CLIENT_SECRET=
DART_API_KEY=
NPS_API_KEY=
AI_API_KEY=
```

---

## application.yml 과 .env 역할 차이

| 구분 | application.yml | .env |
|---|---|---|
| 역할 | 애플리케이션 설정 파일 | 환경 변수/민감정보 분리 |
| 위치 | `src/main/resources/` | 프로젝트 루트(일반적) |
| 포함 내용 | 포트, JPA, mail, oauth 구조 | 비밀번호, secret, API key |
| Git 관리 | 가능 | 보통 제외 |
| 용도 | 구조/동작 정의 | 값 주입 |

정리하면,
- `application.yml` 은 **무엇을 설정할지 정의하는 파일**
- `.env` 는 **실제 비밀값을 담는 파일**

으로 보면 됩니다.

---

## 작업 시 먼저 확인할 것

1. `application.yml` 의 datasource / jpa / mail / oauth 설정
2. `.env` 또는 환경변수 값 누락 여부
3. DB 접속 가능 여부
4. 외부 API Key 유효 여부
5. 테스트 실행 가능 여부

---

## 주의사항

- 민감정보는 Git에 올리지 않습니다.
- 운영/로컬 설정을 혼합하지 않습니다.
- 기능 수정 후에는 최소한 핵심 서비스 테스트 3개를 먼저 확인합니다.
    - `MemberService`
    - `JobMatchService`
    - `CsvImportService`

---

## 유지보수 메모

새 작업자가 투입되면 아래 순서로 확인하는 것을 권장합니다.

1. 프로젝트 실행
2. `application.yml` 확인
3. `.env` 확인
4. DB 연결 확인
5. 테스트 실행
6. 핵심 서비스 로직 확인

---

## 한 줄 요약

이 프로젝트의 README는 **실행, 테스트, 설정(application.yml / .env), 핵심 서비스 확인 포인트**를 빠르게 파악하기 위한 작업자용 문서입니다.
