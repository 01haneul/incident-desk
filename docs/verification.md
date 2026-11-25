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