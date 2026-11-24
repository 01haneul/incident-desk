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
