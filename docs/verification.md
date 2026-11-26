# 검증 기록
 
## 장애 이벤트 접수 - 정상 요청

- 현재 가상의 장애 정보를 보냈고, 우리 서버가 그 정보를 실제 DB에 저장했음 

- 검증일 : 2026-11-24
- 환경 : 로컬 Windows, Java 21, Spring Boot 4.2.0, MySQL 8.0.46
- 요청 : POST/api/events
- 입력 : 
    - serviceKey: payment-api
    - title : Payment approval failed

### 관찰 결과 

- HTTP 201 Created
- eventId : 1
- incidentId : 1
- Location : /api/incidents/1
- status : OPEN
- receiveAt : 2026-11-24T07:39:07.814095Z

### DB 확인

events와 incidents를 incident_id로 조인해 조회했다.

- 응답의 이벤트·사건 식별자와 DB 값 일치
- 서비스 식별자와 제목 일피
- 초기 상태 OPEN 확인
- received_at과 created_at 모두 2026-11-24 07:39:07.814095
- API 응답과 DB에 저장된 UTC 시각 일치

### 판정 

정상 요청 한 건의 응답과 DB 저장 확인 통과

### 미검증·미구현

- 입력 오류 시 저장 방지
- 중간 저장 실패 시 전체 롤백
- 사건 상세 조회 API
- 공통 오류 응답 형식
- 중복 요청 처리 


## 공백 제목 요청 거절 검증

- 검증일: 2026-11-25
- 방법: 실행 중인 로컬 서버에 직접 HTTP 요청 전송
- 요청: POST /api/events
- 입력: serviceKey는 payment-api, title은 공백 3개
- 기대 결과: HTTP 400 반환, 두 테이블에 데이터 추가 없음
- 관찰 결과:
  - HTTP 400 반환
  - incidents: 요청 전 1건 → 요청 후 1건
  - events: 요청 전 1건 → 요청 후 1건
- 판정: 통과

이 결과는 공백 제목 요청의 거절과 데이터 미저장을 확인한다.
저장 도중 실패했을 때의 트랜잭션 롤백은 별도로 검증해야 한다.

## 이벤트 저장 실패 시 사건 롤백 검증

- 검증일: 2026-11-25
- 테스트: EventIngestionServiceRollbackTests
- 환경: 로컬 MySQL의 incident_desk_test DB
- 목적: 사건 저장 후 이벤트 저장 호출이 실패할 때,
  사건만 남는 부분 저장을 방지하는지 확인

### 검증 방법

1. 실제 Spring 서비스와 MySQL을 사용한다.
2. 테스트 자체에는 @Transactional을 적용하지 않는다.
3. 이벤트 저장 직전에 같은 트랜잭션에서 사건 1건을 확인한다.
4. MockitoSpyBean으로 이벤트 저장 호출에서 런타임 예외를 발생시킨다.
5. 서비스 호출 종료 후 트랜잭션 밖에서 DB를 다시 조회한다.

### 실행 명령

현재 터미널에 DB_PASSWORD를 설정하고 실행한다.

```powershell
.\gradlew.bat test --tests "io.github.haneul.incidentdesk.event.EventIngestionServiceRollbackTests"
```

### 관찰 결과

- 이벤트 저장 호출에서 지정한 예외 발생
- 서비스 호출 종료 후 해당 테스트 식별자의 사건 0건, 이벤트 0건
- tests=1, skipped=0, failures=0, errors=0
- 판정: 통과

### 검증 범위와 한계

테스트에서 주입한 런타임 예외에 대한 서비스 트랜잭션의 롤백을 확인했다.
실제 DB 연결 단절, DB 제약조건 위반, HTTP 오류 응답은 이번 검증에 포함하지 않았다.

## 장애 사건 상세 조회 및 공통 오류 응답 검증

- 검증일: 2026-11-25
- 환경: 로컬 Spring Boot 서버와 MySQL incident_desk DB
- 방법: curl로 실제 HTTP 요청을 보내고 Workbench로 DB 개수 확인

### 확인 결과

| 요청 조건 | 기대 결과 | 실제 결과 | 판정 |
| --- | --- | --- | --- |
| GET /api/incidents/1 | 200과 사건 정보 | 저장된 사건 정보 반환 | 통과 |
| GET /api/incidents/999999 | 404 | INCIDENT_NOT_FOUND | 통과 |
| 사건 번호 0 | 400 | INVALID_REQUEST | 통과 |
| 사건 번호 -1 | 400 | INVALID_REQUEST | 통과 |
| 사건 번호 abc | 400 | INVALID_REQUEST | 통과 |
| Long 범위를 초과한 사건 번호 | 400 | INVALID_REQUEST | 통과 |
| POST /api/events의 제목이 공백 | 400과 제목 필드 오류 | INVALID_REQUEST와 title 오류 반환 | 통과 |

오류 응답은 code, message, fieldErrors 형식으로 반환됐다.

### DB 확인

- 잘못된 이벤트 접수 요청 전후 incidents: 1건 → 1건
- 잘못된 이벤트 접수 요청 전후 events: 1건 → 1건
- 잘못된 요청으로 데이터가 추가되지 않았다.

### 검증 범위와 한계

이번 기록은 실제 HTTP 요청을 사용한 수동 검증 결과다.
예상하지 못한 서버 오류의 500 응답과 잘못된 JSON 요청은
아직 별도로 검증하지 않았다.

## 장애 사건 조회 웹 계층 자동화 테스트

- 검증일: 2026-11-25
- 테스트 클래스: IncidentControllerWebTests
- 도구: WebMvcTest, MockMvc, MockitoBean

### 검증 규칙

- 정상 조회 시 200과 계약에 맞는 사건 정보를 반환한다.
- 사건 없음 예외를 404와 INCIDENT_NOT_FOUND로 변환한다.
- 0, 음수, 문자, Long 범위 초과 번호는 400으로 거절한다.
- 잘못된 번호는 조회 서비스에 전달하지 않는다.

### 실행 명령

```powershell
.\gradlew.bat test --tests "io.github.haneul.incidentdesk.incident.IncidentControllerWebTests"
```

### 결과와 범위

- tests=6, skipped=0, failures=0, errors=0
- 판정: 통과
- 실제 Controller, 입력 검증, 공통 예외 처리기를 검증했다.
- 조회 서비스는 대역을 사용하므로 실제 DB 조회는 포함하지 않는다.

## 이벤트 접수 웹 계층 자동화 테스트

- 검증일: 2026-11-26
- 테스트 클래스: EventControllerWebTests

### 검증 규칙

- 정상 요청은 201과 접수 결과를 반환한다.
- Location 헤더에는 이벤트 번호가 아닌 사건 번호를 사용한다.
- 공백 제목은 400과 title 필드 오류로 거절한다.
- 깨진 JSON은 400과 공통 오류 응답으로 거절한다.
- 거절된 요청은 접수 서비스를 호출하지 않는다.

### 실행 명령

```powershell
.\gradlew.bat test --tests "io.github.haneul.incidentdesk.event.EventControllerWebTests"
```

### 결과와 범위

- tests=3, skipped=0, failures=0, errors=0
- 판정: 통과
- 실제 Controller, 입력 검증, 공통 예외 처리기를 실행했다.
- 접수 서비스는 대역을 사용하므로 실제 DB 저장은 포함하지 않는다.

## 전체 자동화 테스트 실행

- 검증일: 2026-11-26
- 실행 명령: `.\gradlew.bat test`
- 사전 조건: 로컬 MySQL 실행, incident_desk_test 준비,
  실행 터미널에 DB_PASSWORD 설정
- 결과: 테스트 12건, 실패 0건, 건너뜀 0건
- 판정: 통과

### 구성

- 사건 조회 웹 계층 테스트: 7건
- 이벤트 접수 웹 계층 테스트: 3건
- 실제 MySQL을 사용하는 롤백 테스트: 1건
- 앱 시작 및 테스트 DB 연결 확인: 1건

웹 계층 테스트는 서비스 대역을 사용한다.
DB를 사용하는 두 테스트는 incident_desk_test에 연결한다.
이번 결과는 작성한 12개 검증 조건이 함께 통과했음을 의미한다.

## 재전송 처리 구현 후 전체 검증

- 검증일: 2026-11-26
- 실행 명령: `.\gradlew.bat test`
- 결과: 테스트 28건, 실패 0건, 건너뜀 0건
- 판정: 통과

| 검사 | 건수 |
| --- | ---: |
| 사건 조회 웹 계층 | 7 |
| 이벤트 접수 웹 계층 | 6 |
| 외부 이벤트 번호 입력 검증 | 6 |
| 실제 MySQL 재전송·동시성 검증 | 7 |
| 저장 실패 시 롤백 | 1 |
| 앱 시작 및 테스트 DB 연결 | 1 |

DB 변경은 database/003_add_external_event_id.sql로 적용했다.
새 DB를 준비할 때는 001, 002, 003 SQL을 순서대로 적용한다.
003의 각 ALTER TABLE은 해당 DB에 한 번만 실행한다.

상세 설계와 검증 범위는 [재전송 처리 문서](idempotency.md)를 참고한다.

## 2026-11-26 — 이벤트 접수 API 인증 검증

### 자동 테스트

실행 명령:

`.\gradlew.bat test`

결과: 전체 42건 통과, 실패 0건, 건너뜀 0건.

인증 웹 테스트는 실제 인증 필터를 포함하며,
접수 서비스는 테스트 대역으로 교체했다.

### 실제 서버와 MySQL 검증

개발 DB incident_desk에서 고유한 auth-demo 서비스 식별자를
사용해 검증했다.

| 조건 | 관찰한 결과 |
| --- | --- |
| API 키 미설정 상태로 서버 실행 | 키 설정 필요 메시지와 함께 시작 실패 |
| 정상 키 설정 후 서버 실행 | 정상 시작 |
| 인증 헤더 없이 접수 | 401 UNAUTHORIZED |
| 잘못된 키로 접수 | 401 UNAUTHORIZED |
| 인증 실패 요청 후 DB 조회 | 사건 0건, 이벤트 0건 |
| 정상 키로 최초 접수 | 201 Created |
| 정상 키로 동일 이벤트 재전송 | 200 OK |
| 최초 접수와 재전송 응답 비교 | eventId, incidentId, receivedAt 동일 |
| 정상 접수와 재전송 후 DB 조회 | 사건 1건, 이벤트 1건 |

### 판정

통과: 인증 실패 요청의 저장 차단과 인증 성공 이후의
접수 및 재전송 처리를 로컬 환경에서 확인했다.

### 검증 범위

- 로컬 서버와 로컬 MySQL에서 검증했다.
- 사건 조회 API의 운영자 인증은 아직 구현하지 않았다.
- 외부 통신 환경의 HTTPS 적용은 후속 작업이다.

## 2026-11-26 — 반복 이벤트의 OPEN 사건 연결 검증

### 로컬 서버와 개발 DB

- 같은 서비스에서 같은 incidentKey로 evt-001과 evt-002를 접수했다.
  두 번째 요청은 HTTP 200을 반환했고, 새 이벤트가 기존 사건에 연결됐다.
- evt-002를 그대로 재전송하자 기존 eventId, incidentId, receivedAt이 반환됐다.
- evt-002의 incidentKey만 바꾸자 HTTP 409 EVENT_ID_CONFLICT가 반환됐다.
  이후 개발 DB 조회에서 evt-001과 evt-002 두 행을 확인했다.
- 다른 장애 키, 다른 서비스, 대소문자가 다른 장애 키로 접수하자
  각각 별도 사건이 생성됐다. 두 데모 서비스를 사건별로 집계한 결과는 4행이었다.

### 테스트 DB에서 확인한 롤백

- 새 사건 생성 후 이벤트 저장에 실패하면 사건과 이벤트가 모두 0건으로 남았다.
  incidentKey를 생략한 경우와 지정한 경우 모두 확인했다.
- 기존 OPEN 사건에 두 번째 이벤트를 추가하다 실패했을 때는
  첫 사건과 첫 이벤트의 모든 컬럼 값이 유지됐고 두 번째 이벤트는 저장되지 않았다.
- 실패 조건은 이벤트 저장 시 테스트용 예외를 발생시켜 재현했다.

### 전체 자동화 테스트

- 실행 명령: `.\gradlew.bat test --rerun-tasks`
- Gradle XML 보고서 합계: 61건 실행, 실패 0건, 오류 0건, 건너뜀 0건
- 판정: 통과