# 첫 API 계약

## 적용 범위

- 단일 조직의 로컬 개발 환경에서 사용한다.
- POST /api/events는 X-API-Key 헤더로 외부 시스템을 인증한다.
- 인증 정보가 없거나 올바르지 않으면 401 UNAUTHORIZED로 응답한다.
- 인증 실패 시 사건과 이벤트를 저장하지 않는다.
- 사건 조회 API의 운영자 인증은 후속 구현이다.
- 외부 이벤트 번호가 없거나 null이면 요청마다 새 사건과 이벤트를 생성한다.
- 외부 번호가 있으면 serviceKey와 externalEventId의 조합으로 재전송을 식별한다.
- 같은 식별자와 같은 제목이면 기존 접수 결과를 반환한다.
- 같은 식별자에 다른 제목을 사용하면 409로 거절한다.
- 서로 다른 이벤트를 진행 중인 사건에 연결하는 기능은 후속 구현이다.

## 1. 장애 이벤트 접수

POST /api/events
Content-Type : application/json

### 요청 예시

{
    "serviceKey" : "payment-api",
    "title" : "결제 승인 실패"
}

### 입력 규칙

- serviceKey : 필수, 1~ 100 자의 영문 소문자 · 숫자 ·
  하이픈
- title : 필수, 1~200자, 공백만으로 구성할 수 없음
- 입력값을 자동으로 잘라내거나 변경하지 않음
- receivedAt은 요청으로 받지 않고 서버에서 기록함

### 성공 응답

상태 코드 : 201 Created
Location : /api/incidents/1

{
    "eventId" : 1,
    "incidentId" : 1,
    "status" : "OPEN",
    "receivedAt" : "2026-11-23T08:30:00Z"
}


- 식별자와 시각은 예시이며 실제 값은 서버에서 생성한다.
- 이벤트와 장애 사건은 모두 저장된 이후 성공을 응답한다.
- 저장 중 실패하면 두 데이터 모두 롤백한다.

### 입력 오류 응답

상태 코드: 400 Bad Request

{
  "code": "INVALID_REQUEST",
  "message": "입력값을 확인해주세요.",
  "fieldErrors": [
    {
      "field": "title",
      "message": "제목은 공백일 수 없습니다."
    }
  ]
}

잘못된 JSON도 400으로 응답한다.
요청이 거절되면 이벤트와 장애 사건을 저장하지 않는다.

## 2. 장애 사건 상세 조회

GET /api/incidents/{incidentId}

### 성공 응답

상태 코드: 200 OK

{
  "incidentId": 1,
  "serviceKey": "payment-api",
  "title": "결제 승인 실패",
  "status": "OPEN",
  "createdAt": "2026-11-23T08:30:00Z"
}

### 존재하지 않는 사건

상태 코드: 404 Not Found

{
  "code": "INCIDENT_NOT_FOUND",
  "message": "장애 사건을 찾을 수 없습니다.",
  "fieldErrors": []
}

### 잘못된 식별자

incidentId가 양의 정수가 아니거나 서버의 정수 표현 범위를
벗어나면 400 Bad Request로 응답한다.

## 3. 예상하지 못한 서버 오류

상태 코드: 500 Internal Server Error

{
  "code": "INTERNAL_ERROR",
  "message": "요청 처리 중 오류가 발생했습니다.",
  "fieldErrors": []
}

응답에 SQL, DB 접속 정보, 스택 트레이스를 포함하지 않는다.

## 구현 예정 : 외부 이벤트 재전송 처리

### 요청 예시 

```json
{
  "serviceKey" : "payment-api",
  "externalEventId" : "evt-001",
  "title" : "Payment approval failed"
}
```

### 외부 이벤트 번호 규칙

- externalEventId는 선택 입력이다.
- 생략 하거나 null이면 기존처럼 매번 새로 접수한다.
- 제공하면 1~100자의 영문 대소문자, 숫자, 점, 밑줄, 콜론, 하이픈만 허용한다.
- 대소문자를 구분하고 입력값을 자동 변경하지 않는다.
- 중복 식별 기준은 serviceKey와 externalEventId의 조합 이다.

### 처리 결과

| 조건 | 상태 코드 | 동작 |
| --- | --- | --- |
| 최초 접수 | 201 | 사건과 이벤트 생성 |
| 같은 식별자와 같은 제목으로 재전송 | 200 | 기존 접수 결과 반환, 추가 생성 없음 |
| 같은 식별자에 다른 제목 사용 | 409 | EVENT_ID_CONFLICT, 추가 생성 없음 |

재전송 응답의 eventId, incidentId, receivedAt은
기존 접수에 기록된 값을 사용한다.

제목은 공백 제거 등의 변환 없이 원래 문자열로 비교한다.