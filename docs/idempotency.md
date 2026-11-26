# 외부 이벤트 재전송의 중복 저장 방지

## 해결할 문제

서버가 이벤트를 저장했지만 응답이 전달되지 않으면,
감시 시스템은 성공 여부를 모르고 같은 이벤트를 재전송할 수 있다.

재전송으로 사건과 이벤트가 중복 생성되는 것을 방지한다.

## 처리 규칙

- 중복 식별 기준은 serviceKey와 externalEventId의 조합이다.
- 외부 번호는 대소문자를 구분한다.
- 같은 식별자와 같은 제목이면 기존 접수 결과를 반환한다.
- 같은 식별자에 다른 제목을 사용하면 409로 거절한다.
- 제목은 변환 없이 원래 문자열로 비교한다.
- 외부 번호가 없거나 null이면 기존처럼 매번 새로 접수한다.

| 조건 | HTTP 상태 |
| --- | --- |
| 최초 접수 | 201 |
| 동일 이벤트 재전송 | 200 |
| 같은 식별자에 다른 제목 | 409 |

재전송 응답은 기존 eventId, incidentId, receivedAt을 유지한다.
status는 연결된 사건의 상태를 사용한다.

## 검토한 방식과 선택

| 방식 | 검토 결과 |
| --- | --- |
| 사전 조회로만 중복 판단 | 동시 요청이 모두 미접수로 판단할 수 있음 |
| DB UNIQUE만 적용 | 중복 저장은 막지만 충돌한 요청의 응답 처리도 필요 |
| 사전 조회와 UNIQUE, 롤백 후 재조회 | 선택한 방식 |

events에 UNIQUE(service_key, external_event_id)를 적용했다.
external_event_id는 NULL을 허용해 기존 요청과 데이터를 유지한다.

## 트랜잭션 경계

- EventIngestionService는 중복 여부를 판단한다.
- EventCreationService는 사건과 이벤트를 한 트랜잭션으로 저장한다.
- 저장 중 무결성 오류가 발생하면 저장 트랜잭션을 롤백한다.
- 롤백 완료 후 같은 식별자의 접수가 존재하는지 다시 확인한다.
- 기존 접수가 있으면 내용을 비교해 결과 재사용 또는 충돌로 처리한다.
- 기존 접수가 없다면 원래 저장 오류를 전달한다.

접수 판단 서비스는 기존 트랜잭션 안에서 호출할 수 없도록
Propagation.NEVER로 실행 경계를 명시했다.

## 검증 결과

로컬 환경에서 재현한 설계 검증 사례다.

- 실제 HTTP 순차 요청에서 201 → 200 → 409 확인
- 위 요청 후 사건 1건, 이벤트 1건 유지
- 실제 MySQL 테스트에서 재전송 결과의 번호와 수신 시각 일치 확인
- 서비스·외부 번호·대소문자에 따른 식별 범위 확인
- 외부 번호 없는 요청의 기존 동작 유지 확인
- 두 요청이 모두 최초 저장을 시도하도록 조절한 동시성 검사 통과
- 동시 호출 결과: 신규 생성 1건, 기존 결과 재사용 1건
- 동시 호출 후 사건 1건, 이벤트 1건 확인

## 검증 범위와 한계

동시성 검증은 실제 MySQL을 사용하는 서비스 호출 2건을 대상으로 했다.
실제 HTTP 동시 부하, 대규모 트래픽의 처리량과 지연 시간은 측정하지 않았다.
인증은 후속 구현이며 현재는 로컬 환경에서 사용한다.

## 관련 코드

- [접수 판단 서비스](../src/main/java/io/github/haneul/incidentdesk/event/EventIngestionService.java)
- [트랜잭션 저장 서비스](../src/main/java/io/github/haneul/incidentdesk/event/EventCreationService.java)
- [MySQL 중복·동시성 테스트](../src/test/java/io/github/haneul/incidentdesk/event/EventIngestionServiceIdempotencyTests.java)
- [DB 변경 SQL](../database/003_add_external_event_id.sql)