-- 기존 DB와 테스트 DB에 외부 이벤트 번호 및 중복 방지 제약조건 추가.
-- 테이블 변경 권한이 있는 관리자 계정으로 각 ALTER TABLE을 한 번만 실행한다.
-- 두 컬럼의 UNIQUE : 같은 서비스·외부 번호 조합의 중복 저장 차단
ALTER TABLE incident_desk.events
    ADD COLUMN external_event_id VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL DEFAULT NULL,
    ADD UNIQUE KEY uk_events_service_external (
        service_key,
        -- 보내는 쪽이 부여한 이벤트 번호 저장
        external_event_id
    );

ALTER TABLE incident_desk_test.events
    ADD COLUMN external_event_id VARCHAR(100)
        CHARACTER SET ascii
        -- 외부 번호의 영문 대소문자 구분
        -- NULL 허용
        COLLATE ascii_bin
        NULL DEFAULT NULL,
    ADD UNIQUE KEY uk_events_service_external (
        service_key,
        external_event_id
    );

-- 두 DB에 복합 UNIQUE 인덱스가 생성됐는지 확인
SELECT
    TABLE_SCHEMA,
    INDEX_NAME,
    NON_UNIQUE,
    SEQ_IN_INDEX,
    COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('incident_desk', 'incident_desk_test')
    AND TABLE_NAME = 'events'
    AND INDEX_NAME = 'uk_events_service_external'
ORDER BY TABLE_SCHEMA, SEQ_IN_INDEX;