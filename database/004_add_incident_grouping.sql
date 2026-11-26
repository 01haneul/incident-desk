-- 반복 이벤트의 사건 연결을 위한 컬럼과 제약조건 추가.
-- 관리자 계정으로 실행하며, 각 ALTER TABLE은 한 번만 적용한다.

-- 개발 DB: 사건의 연결 기준과 OPEN 사건 중복 방지
ALTER TABLE incident_desk.incidents
    ADD COLUMN incident_key VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL DEFAULT NULL,

    ADD COLUMN active_incident_key VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'OPEN' THEN incident_key
                ELSE NULL
            END
        ) STORED,

    ADD UNIQUE KEY uk_incidents_service_active (
        service_key,
        active_incident_key
    );

-- 개발 DB: 이벤트 접수 당시의 연결 기준 보관
ALTER TABLE incident_desk.events
    ADD COLUMN incident_key VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL DEFAULT NULL;

-- 테스트 DB: 개발 DB와 동일한 사건 구조 적용
ALTER TABLE incident_desk_test.incidents
    ADD COLUMN incident_key VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL DEFAULT NULL,

    ADD COLUMN active_incident_key VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'OPEN' THEN incident_key
                ELSE NULL
            END
        ) STORED,

    ADD UNIQUE KEY uk_incidents_service_active (
        service_key,
        active_incident_key
    );

-- 테스트 DB: 이벤트 접수 당시의 연결 기준 보관
ALTER TABLE incident_desk_test.events
    ADD COLUMN incident_key VARCHAR(100)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL DEFAULT NULL;

-- 두 DB에 필요한 컬럼이 추가됐는지 확인
SELECT
    TABLE_SCHEMA,
    TABLE_NAME,
    COLUMN_NAME,
    IS_NULLABLE,
    COLLATION_NAME,
    EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA IN ('incident_desk', 'incident_desk_test')
    AND (
        (
            TABLE_NAME = 'incidents'
            AND COLUMN_NAME IN (
                'incident_key',
                'active_incident_key'
            )
        )
        OR (
            TABLE_NAME = 'events'
            AND COLUMN_NAME = 'incident_key'
        )
    )
ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;

-- OPEN 사건 중복 방지 인덱스 확인
SELECT
    TABLE_SCHEMA,
    INDEX_NAME,
    NON_UNIQUE,
    SEQ_IN_INDEX,
    COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('incident_desk', 'incident_desk_test')
    AND TABLE_NAME = 'incidents'
    AND INDEX_NAME = 'uk_incidents_service_active'
ORDER BY TABLE_SCHEMA, SEQ_IN_INDEX;