# 첫 API 계약

## 적용 범위

- 단일 조직의 로컬 개발 환경에서 사용한다.
- 인증과 중복 처리는 후속 단계에서 구현한다.
- 현재 단계에서는 유효한 접수 요청마다 새 장애 사건
  을 생성한다.

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