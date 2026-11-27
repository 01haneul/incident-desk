# Incident Desk 인수인계
작성일: 2026-11-26

최종 갱신일 2026-11-27

## 가장 최근 중단 지점

- 사건 배정·인계·확인·해결과 대응 이력 조회를 구현했다.
- 로컬 서버의 실제 HTTP 응답과 개발 DB를 대조했다. 중복 확인 요청과 거절된 인계는 이력을 추가하지 않았고, `afterId` 조회는 지정한 번호 이후의 이력만 반환했다.
- 알림 전송 실패 후 두 번째 시도에서 수신 성공과 개발 DB의 `SENT` 상태를 확인했다.
- `.\gradlew.bat test --rerun-tasks --console=plain` 실행 결과는 `BUILD SUCCESSFUL`이었다.
- 상세 검증 결과는 [검증 기록](verification.md)에 있다.
- 다음 작업은 빈 MySQL 환경에서 README만 보고 처음부터 실행할 수 있는지 확인하는 것이다.

## 프로젝트와 환경

- 백엔드 포트폴리오 프로젝트: incident-desk
- GitHub: https://github.com/01haneul/incident-desk
- 브랜치: main
- 사용자 PC 경로: C:\Users\smhrd\Desktop\incident-desk
- Windows PowerShell 5.1, VS Code, MySQL Workbench 사용
- Java 21, Spring Boot 4.2.0, Spring Data JPA, MySQL 8.0.46
- 서버 주소: http://127.0.0.1:8081
- 개발 DB: incident_desk
- 테스트 DB: incident_desk_test
- 앱 계정: incident_app
- DB 생성과 테이블 변경은 Workbench의 root 연결에서 실행한다.
- 앱 계정에는 데이터 조회·저장·수정·삭제 권한만 부여했다.

## 실행 설정과 비밀값

- DB_PASSWORD: incident_app의 MySQL 비밀번호
- INGESTION_API_KEY: 이벤트 접수 인증 키
- 실제 값은 환경 변수로 관리한다.
- 실제 비밀번호나 API 키를 채팅·소스·문서에 기록하지 않는다.
- 환경 변수는 실행하는 PowerShell 세션에서 설정해야 한다.
- Start-Process로 연 CMD는 부모 PowerShell의 환경 변수를 전달받는다.
- 새 PowerShell을 열거나 기존 터미널을 닫으면 다시 설정해야 할 수 있다.

최근 서버 실행 실패는 원래 PowerShell에 DB_PASSWORD가 없었던 상태에서
발생했다. API 키는 설정돼 있었다. DB 비밀번호를 입력하고 새 CMD를 열어
서버를 다시 실행한 후 Started IncidentDeskApplication을 확인했다.

## 서비스의 의미

- 외부 감시 시스템에서 장애 알림을 받아 사건으로 관리하는 서버다.
- 현재 테스트 알림은 우리가 직접 만든 가상 데이터다.
- 이 서버가 실제 결제 서비스 장애를 직접 감지하는 기능은 없다.
- 이벤트는 수신한 개별 알림이고, 사건은 담당자가 대응할 관리 단위다.

## 구현된 기능

- POST /api/events: 입력 검증과 이벤트 접수
- GET /api/incidents/{incidentId}: 사건 상세 조회
- GET /api/health: 서버 응답 확인
- 공통 오류 응답과 내부 오류 정보 노출 방지
- 사건과 이벤트 저장의 트랜잭션 및 실패 롤백
- 동일 이벤트 재전송 방지
- 이벤트 접수 API 키 인증
- 반복 이벤트의 OPEN 사건 연결 로직 및 HTTP 연결

인증은 POST /api/events의 X-API-Key 헤더에 적용한다.
사건 조회 API의 운영자 인증은 후속 작업이다.
서버는 로컬 주소에 바인딩하며 외부 배포 검증은 하지 않았다.

## 세 가지 식별자의 차이

- serviceKey: 어느 서비스인지 식별
- externalEventId: 개별 알림의 재전송 식별
- incidentKey: 같은 사건으로 묶을 장애 유형 식별

재전송 식별 기준:
serviceKey + externalEventId

사건 연결 기준:
serviceKey + incidentKey + OPEN 상태

incidentKey가 없거나 null이면 기존처럼 새 사건을 생성한다.
단, 동일 이벤트 재전송이면 기존 결과를 반환한다.

externalEventId가 없으면 매번 새 이벤트를 생성하지만,
incidentKey가 있으면 같은 OPEN 사건에 연결할 수 있다.

같은 외부 이벤트 번호에 제목 또는 incidentKey를 변경하면
409 EVENT_ID_CONFLICT로 거절한다.

## 사건 연결 규칙과 응답

- 서로 다른 이벤트도 연결 기준이 같으면 같은 OPEN 사건에 연결한다.
- 새 이벤트의 제목이 달라도 연결할 수 있다.
- 사건의 대표 제목과 생성 시각은 최초 값을 유지한다.
- 각 이벤트에는 접수 당시 제목과 incidentKey를 보관한다.

성공 응답 필드:
eventId, incidentId, status, receivedAt,
eventCreated, incidentCreated

- 새 사건과 이벤트 생성: 201, true / true
- 기존 사건에 새 이벤트 추가: 200, true / false
- 동일 이벤트 재전송: 200, false / false
- Location: /api/incidents/{incidentId}

## DB와 주요 코드

SQL 변경 파일:
- database/001_create_incidents_and_events.sql
- database/002_create_test_database.sql
- database/003_add_external_event_id.sql
- database/004_add_incident_grouping.sql

004는 개발 DB와 테스트 DB에 적용했다.
두 DB의 새 유니크 인덱스가 생성된 것을 화면으로 확인했다.

추가 컬럼:
- incidents.incident_key: 선택 입력, ASCII 대소문자 구분
- incidents.active_incident_key: DB가 자동 계산하는 STORED 생성 컬럼
- events.incident_key: 접수 당시 연결 키

active_incident_key는 OPEN일 때 incident_key이고 그 외에는 NULL이다.

제약조건:
- uk_events_service_external:
  events(service_key, external_event_id)
- uk_incidents_service_active:
  incidents(service_key, active_incident_key)

IncidentRepository는 native SQL로 service_key와 active_incident_key를 조회한다.
active_incident_key는 Java 엔티티의 쓰기 필드로 매핑하지 않았다.

EventCreationService:
- 기존 create 3인자 메서드는 유지했다.
- 새 create 4인자 메서드는 사건 연결을 지원한다.
- 실제 조회와 저장은 하나의 트랜잭션에서 처리한다.
- package-private findOpenIncident 메서드가 있다.

EventIngestionService:
- Propagation.NEVER로 외부 트랜잭션 진입을 제한한다.
- 기존 receive 2·3인자 메서드를 유지했다.
- 새 receive 4인자는 마지막 인자로 incidentKey를 받는다.
- 저장 서비스의 롤백이 끝난 뒤 재전송 여부를 다시 확인한다.
- OPEN 사건 유니크 충돌로 확인된 경우에만 한 번 재시도한다.
- Hibernate ConstraintViolationException의 제약조건 이름을 확인한다.
- Result.created는 이벤트 생성 여부다.
- Result.incidentCreated는 사건 생성 여부다.
- Receipt는 기존 4개 필드를 유지했다.

EventController:
- incidentKey가 null이면 기존 receive 3인자를 호출한다.
- 값이 있으면 receive 4인자를 호출한다.
- EventCreateResponse로 생성 여부 두 필드를 함께 응답한다.
- HTTP 상태는 incidentCreated를 기준으로 결정한다.

## 검증된 범위

- 기존 접수·조회·입력 검증·인증·재전송 테스트 성공
- 사건 연결 서비스 테스트 4건 성공
- 사건 연결 동시 접수 테스트 2건 성공
- 사건 연결 HTTP 테스트를 추가한 전체 테스트 명령 성공

동시 접수 테스트는 실제 MySQL에서 서비스 호출 두 개를 사용한다.
두 요청 모두 사건 없음 조회를 끝낸 뒤 저장하도록 시점을 조절했다.

- 서로 다른 이벤트 번호: 사건 1건, 이벤트 2건
- 같은 이벤트 번호: 사건 1건, 이벤트 1건

HTTP 테스트는 MockMvc와 서비스 대역을 사용한다.
실제 HTTP 서버에 대한 동시 부하나 성능 측정 결과는 없다.

## 바로 이어서 할 작업

1. README에 JDK 21, MySQL, 개발 DB와 앱 계정 준비, SQL 적용 순서, 환경 변수 설정, 서버 실행 및 테스트 명령을 정리한다.
2. 기존 개발·테스트 DB를 보존하고, 별도의 빈 MySQL 환경에서 새로 내려받은 저장소로 설치 절차를 검증한다.
3. 서버 시작 후 이벤트 한 건을 접수하고, 응답의 사건 번호로 상세 조회와 DB 저장 결과를 확인한다.
4. 전체 테스트 결과와 설치 중 추가로 필요했던 절차를 검증 기록에 남긴다.

## 후속 기능

- 사건 확인과 해결 상태 변경
- 담당자 배정과 미확인 사건의 담당자 전환
- 알림 전송과 실패 재시도
- 대응 이력 조회
- 운영자 인증과 서비스별 권한
- 새 환경 설치 절차 및 외부 배포 검증

## 후속 범위

- 운영자 인증과 서비스별 권한은 후속 기능이다.
- 외부 배포 환경에서의 검증은 아직 수행하지 않았다.

## 진행 방식

- 모든 설명은 한국어로 한다.
- 사용자가 직접 Windows PC에서 파일을 수정하고 실행한다.
- SQL은 Workbench, 명령은 PowerShell, Java는 파일 경로를 명확히 구분한다.
- 기존 파일 교체와 새 파일 생성을 구분한다.
- 코드 수정은 부분 조각보다 전체 파일 내용을 선호한다.
- main과 test 파일 위치를 특히 명확히 안내한다.
- 작업 목적, 실행 방법, 성공 기준을 설명한다.
- 컴파일·테스트·서버 시작·실제 HTTP·DB 검증 결과를 구분한다.
- 확인하지 않은 결과는 완료로 기록하지 않는다.
- 기존 구현을 다시 만들지 말고 최신 코드부터 읽는다.