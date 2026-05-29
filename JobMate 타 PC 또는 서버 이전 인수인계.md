# JobMate 타 PC 또는 서버 이전 인수인계

## 1. 문서 목적
이 문서는 **JobMate 프로젝트**를 다른 PC 또는 서버로 이전하여 다시 실행할 때 필요한 절차를 정리한 인수인계 문서입니다.

이번 정리본은 현재 JobMate 프로젝트 기준으로 다시 작성했습니다. 특히 아래 3가지를 반드시 포함합니다.

- 현재 **SecurityConfig 보안 이슈**
- 현재 **Kakao OAuth 연동 이슈**
- 현재 로컬 DB 백업 파일인 **`jobmate_dump.sql`** 사용 방법

---

## 2. 프로젝트 개요
JobMate는 **Spring Boot + Thymeleaf + JPA + Spring Security + OAuth2** 기반 취업 지원 프로젝트입니다.

주요 기능:
- 회원가입
- 이메일 인증
- 소셜 로그인(OAuth2)
- 프로필 기반 채용공고 추천
- 공고 상세조회
- 지원 내역 관리
- 자기소개서 / 면접 보조
- CSV 기반 채용공고 적재
- 기업 재무 / NPS / DART 연동

---

## 3. 현재 구조 요약

```text
브라우저 -> Spring Boot(JobMate) -> MySQL
                         └-> OAuth2 (Kakao / Naver / Google)
                         └-> Mail (Gmail SMTP)
                         └-> 외부 API (DART / NPS / Groq 등)
```

현재 기본 실행 포트는 아래 설정 기준입니다.

```yml
server:
  port: ${SERVER_PORT:5000}
```

즉, 로컬 기본 접속 주소는 아래입니다.

```text
http://localhost:5000
```

---

## 4. 이전 전에 반드시 확보해야 할 것

### 4-1. 프로젝트 소스 전체
기존 PC에서 현재 JobMate 프로젝트 전체 폴더를 확보합니다.

예시:

```text
G:\LSK\jobmate_pjt
```

### 4-2. 로컬 DB 백업 파일
현재 프로젝트 루트에 **로컬 DB dump 파일**이 포함되어 있습니다.

```text
jobmate_dump.sql
```

이 파일은 현재 로컬 MySQL `jobmate` DB 백업본입니다.

중요:
- 타 PC 또는 서버 이전 시 이 파일을 반드시 같이 옮깁니다.
- 새 환경에서 DB를 다시 만들 때 이 파일로 복원합니다.

### 4-3. 환경변수 파일
아래 정보가 들어 있는 `.env` 또는 환경변수 목록을 확보해야 합니다.

예시 항목:
- DB_HOST
- DB_PORT
- DB_NAME
- DB_USERNAME
- DB_PASSWORD
- MAIL_USERNAME
- MAIL_PASSWORD
- KAKAO_CLIENT_ID
- KAKAO_CLIENT_SECRET
- NAVER_CLIENT_ID
- NAVER_CLIENT_SECRET
- GOOGLE_CLIENT_ID
- GOOGLE_CLIENT_SECRET
- GROQ_API_KEY
- NPS_API_KEY
- DART_API_KEY
- KAKAO_REST_API_KEY

주의:
- `.env` 자체는 Git에 올리지 않습니다.
- 비밀번호 / 시크릿은 별도 보안 방식으로 전달해야 합니다.

---

## 5. 새 PC에 준비할 프로그램

- Java 17
- Git
- PowerShell
- MySQL 클라이언트
- IntelliJ IDEA 또는 VS Code
- 필요 시 HeidiSQL / DBeaver

---

## 6. 새 PC에 소스 준비

### 방법 . 기존 폴더 복사
프로젝트 폴더를 통째로 복사해도 됩니다.

예시:

```text
G:\LSK\jobmate_pjt
```

---

## 7. 새 PC에서 빌드

PowerShell:

```powershell
cd G:\LSK\jobmate_pjt
.\gradlew.bat clean bootJar
```

또는 실행만 먼저 할 경우:

```powershell
.\gradlew.bat bootRun
```

빌드 결과물 예시:

```text
build\libs\jobmate-0.0.1-SNAPSHOT.jar
```

---

## 8. 로컬 DB 복원 방법

### 8-1. DB 생성
MySQL에서 먼저 DB를 생성합니다.

```sql
CREATE DATABASE jobmate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 8-2. dump 복원
프로젝트 루트에 있는 `jobmate_dump.sql` 을 사용합니다.

PowerShell 예시:

```powershell
mysql -u root -p jobmate < jobmate_dump.sql
```

또는 호스트 지정:

```powershell
mysql -h localhost -P 3306 -u root -p jobmate < jobmate_dump.sql
```

### 8-3. 확인
```sql
USE jobmate;
SHOW TABLES;
```
### 8-4. 현재 로컬 DB를 다시 dump 떠서 `jobmate_dump.sql` 만들기
인수인계 직전 또는 다른 PC로 넘기기 전에, 현재 로컬 MySQL의 `jobmate` DB를 다시 백업해 최신 `jobmate_dump.sql` 을 만드는 PowerShell 예시는 아래와 같습니다.

#### 기본 예시 --> cd G:\LSK\jobmate_pjt
```powershell
mysqldump -h localhost -P 3306 -u root -p --default-character-set=utf8mb4 --single-transaction --skip-lock-tables --set-gtid-purged=OFF --routines --triggers --events jobmate > jobmate_dump.sql
---
```
### 8-5. 트리에 있는 jobmate_dump.sql 참고

## 9. 현재 설정 파일 구조

핵심 설정 파일:

```text
src/main/resources/application.yml
```

현재 구조상 대부분은 환경변수 기반입니다.

예:
- datasource
- mail
- OAuth2 client
- 외부 API key

하지만 **모든 항목이 완전히 공용 구조는 아닙니다.**  
특히 **Kakao redirect-uri** 는 아직 하드코딩 이슈가 있습니다.

---

## 10. 현재 SecurityConfig 보안 문제
현재 소스 기준 `SecurityConfig` 에는 아래 이슈가 있습니다.

### 10-1. 전체 공개 문제
현재 원본 소스에는 아래 설정이 있었습니다.

```java
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
```

이 의미는 사실상 **모든 URL이 열려 있는 상태**입니다.  개발의 물리적 시간을
최소화 하기 위해서 선택한 상황입니다.  (추후 개선 과제임)

영향:
- `/dashboard`
- `/jobs`
- `/api/admin/**`

같은 보호 대상까지 비로그인 사용자가 접근할 수 있는 구조가 됩니다.

### 10-2. 관리자 API 보호 미흡
현재 관리자 전용으로 봐야 하는 CSV import 계열은 `/api/admin/**` 아래에 있습니다.

예:
- `/api/admin/import-csv`

그런데 전체 permitAll 상태면 이 API도 보호되지 않습니다.

### 10-3. 로그아웃 URL 하드코딩
기존 `SecurityConfig` 에는 Kakao logout redirect URL 이 코드에 직접 박혀 있었습니다.
AWS와 연동하기 위해서 불가피한 선택이였음 ( 추후 개선 과제)

문제점:
- 운영 주소 변경 시 유지보수 어려움
- 환경별 전환 어려움
- 코드에 외부 주소와 client 정보가 섞임

---

## 11. 추후 개선 과제인  SecurityConfig.java  수정 방향
    아래 구조가 현재 검토안입니다.

### 공개
- `/`
- `/login`
- `/signup`
- `/find-id`
- `/find-password`
- `/css/**`
- `/js/**`
- `/images/**`
- `/oauth2/**`
- `/login/oauth2/**`
- `/error`

### 로그인 필요
- `/dashboard`
- `/jobs`
- `/job-detail/**`
- `/profile-setup`
- `/interview`
- `/interview-notes`
- `/applications`
- `/community`
- `/account`
- `/api/**`

### SecurityConfig.java 를 수정시 카카오 연동 등 별도 수정 작업이 필요함


---

## 12. 추후 개선 과제인  Kakao 연동 이슈
현재 JobMate 프로젝트에서 Kakao는 **완전한 로컬/AWS 공용 구조가 아닙니다.**

### 12-1. redirect-uri 하드코딩 문제
현재 `application.yml` 의 Kakao 설정은 아래 방향입니다.

- `client-id`, `client-secret` 는 환경변수
- 그러나 `redirect-uri` 는 AWS Elastic Beanstalk 주소로 직접 하드코딩

즉, 로컬에서 그대로 쓰면:
- 일반 서버 기동은 가능
- Kakao OAuth 콜백은 로컬 기준으로 맞지 않을 수 있음

### 12-2. 로그아웃 redirect 하드코딩 문제
기존 `SecurityConfig` 에도 Kakao logout redirect 주소가 직접 들어 있었습니다.

이 구조는:
- 로컬 테스트 부적합
- AWS 주소 변경 시 유지보수 어려움
- 최종 도메인 전환 시 추가 수정 필요

### 12-3. 로컬에서의 현실적인 판단
현재 로컬에서는 아래만 먼저 확인하는 것이 맞습니다.

가능:
- 서버 실행
- 비로그인 보호 확인
- 로그인 페이지 진입
- OAuth 버튼 진입 시작 여부

보류:
- Kakao 최종 로그인 완료
- AWS 기준 콜백 정상 완료

### 12-4. 추후 개선 과제 : application.yml 수정 
Kakao 설정은 아래처럼 외부화하는 것이 좋습니다.

```yml
redirect-uri: ${KAKAO_REDIRECT_URI}
```

환경별 예시:
- 로컬:
  ```text
  http://localhost:5000/login/oauth2/code/kakao
  ```
- AWS:
  ```text
  http://<EB주소>/login/oauth2/code/kakao
  ```
- 최종 도메인:
  ```text
  https://<서비스도메인>/login/oauth2/code/kakao
  ```

---

## 13. 현재 로그인 구조 주의사항

### 13-1. 일반 ID/PW 웹 로그인은 아직 미완성
현재 구조상 `/login` 화면은 사실상 **OAuth 로그인 진입 화면**에 가깝습니다.

즉:
- Kakao / Naver / Google 버튼 중심
- 일반 ID/PW formLogin 은 아직 정식 연결되지 않음

따라서 현재 보안 테스트 시에는:
- OAuth 진입
- 비로그인 차단
- 보호 URL 접근 차단

위주로 확인합니다.

### 13-2. 이메일 인증 메일 발송
회원가입 시 이메일 인증 발송은 Gmail SMTP 설정에 의존합니다.

로컬에서 실패할 가능성이 큰 항목:
- `MAIL_USERNAME` 누락
- `MAIL_PASSWORD` 누락
- Gmail 앱 비밀번호 미사용
- SMTP 타임아웃 미설정

---

## 14. 로컬 실행 절차

### 14-1. 환경변수 준비
예시:

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=jobmate
DB_USERNAME=root
DB_PASSWORD=****
MAIL_USERNAME=****
MAIL_PASSWORD=****
KAKAO_CLIENT_ID=****
KAKAO_CLIENT_SECRET=****
NAVER_CLIENT_ID=****
NAVER_CLIENT_SECRET=****
GOOGLE_CLIENT_ID=****
GOOGLE_CLIENT_SECRET=****
GOOGLE_REDIRECT_URI=http://localhost:5000/login/oauth2/code/google
NAVER_REDIRECT_URI=http://localhost:5000/login/oauth2/code/naver
KAKAO_REST_API_KEY=****
GROQ_API_KEY=****
NPS_API_KEY=****
DART_API_KEY=****
SERVER_PORT=5000
```

주의:
- Kakao는 현재 redirect-uri 외부화가 안 되어 있으면 로컬에서 완전 테스트가 어렵습니다.

### 14-2. 서버 실행
```powershell
cd G:\LSK\jobmate_pjt
.\gradlew.bat bootRun
```

### 14-3. 접속 주소
```text
http://localhost:5000
```

---

## 15. 로컬에서 우선 확인할 항목

### 15-1. 비로그인 상태
확인 주소:

```text
http://localhost:5000/
http://localhost:5000/dashboard
http://localhost:5000/jobs
http://localhost:5000/api/admin/import-csv
```

의도:
- `/` 는 열려야 함
- `/dashboard`, `/jobs`, `/api/admin/import-csv` 는 로그인 페이지로 이동해야 함

### 15-2. 로그인 페이지
```text
http://localhost:5000/login
```

확인:
- 로그인 화면 노출
- OAuth 버튼 노출
- CSS/JS 정상 로딩

### 15-3. OAuth 버튼
- Kakao
- Naver
- Google

확인:
- 외부 인증 페이지로 이동 시작되는지

주의:
- Kakao는 redirect-uri 문제 때문에 로컬 완료 테스트는 보류 가능

---

## 16. AWS 또는 다른 서버로 이전 시 핵심 체크

1. 소스 전체 복사
2. Java 17 설치
3. `jobmate_dump.sql` 확보
4. 새 MySQL 또는 RDS에 `jobmate` DB 생성
5. `jobmate_dump.sql` 복원
6. `.env` 또는 환경변수 설정
7. `application.yml` 의 Kakao redirect 정책 점검
8. `SecurityConfig` 보안 수정본 적용 여부 확인
9. JAR 빌드 및 실행
10. `/dashboard`, `/jobs`, `/api/admin/**` 보호 확인
11. OAuth 연동 주소 재확인
12. Kakao 개발자 콘솔 redirect URI 동기화

---

## 17. 실제 인수인계 체크리스트

- [ ] 프로젝트 전체 소스 복사 완료
- [ ] Java 17 설치 완료
- [ ] Gradle 실행 확인
- [ ] `jobmate_dump.sql` 확보 완료
- [ ] `jobmate` DB 생성 완료
- [ ] dump 복원 완료
- [ ] 환경변수 준비 완료
- [ ] `SecurityConfig` 1차 보안 수정 적용 여부 확인
- [ ] `/dashboard` 비로그인 차단 확인
- [ ] `/jobs` 비로그인 차단 확인
- [ ] `/api/admin/**` 비로그인 차단 확인
- [ ] `/login` 접근 확인
- [ ] OAuth 버튼 노출 확인
- [ ] Kakao redirect URI 운영 주소와 일치 확인

---

## 18. 인수인계 시 꼭 전달할 것

### 필수 파일
- 프로젝트 전체 소스
- `jobmate_dump.sql`
- `.env.example` 또는 환경변수 목록
- 보안 수정된 `SecurityConfig.java` 기준본

### 필수 설명
- 현재 원본 `SecurityConfig` 는 전체 공개 이슈가 있었음
- Kakao redirect-uri 는 아직 완전 외부화가 안 되어 있음
- 로컬에서는 Kakao 완전 테스트보다 일반 보안 동작 확인이 우선
- `/api/admin/**` 는 현재 1차 기준 로그인 필요 수준까지만 보호함
- 관리자 role 분리는 후속 과제

---

## 19. 최종 한 줄 요약
JobMate를 다른 PC 또는 서버로 옮길 때 핵심은 **프로젝트 소스 + `jobmate_dump.sql` + 환경변수**를 함께 넘기고, **SecurityConfig의 전체 공개 문제**와 **Kakao redirect 하드코딩 문제**를 반드시 같이 인계하는 것입니다.

