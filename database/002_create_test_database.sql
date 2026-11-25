SELECT CURRENT_USER();

-- 자동화 테스트 전용 DB 생성
CREATE DATABASE IF NOT EXISTS incident_desk_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 앱 계정에 테스트 데이터 처리 권한 부여
GRANT SELECT, INSERT, UPDATE, DELETE
    ON incident_desk_test.*
    TO 'incident_app'@'localhost';

USE incident_desk_test;

-- 장애 사건 저장
CREATE TABLE IF NOT EXISTS incidents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_key VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- 사건에 연결된 수신 이벤트 저장
CREATE TABLE IF NOT EXISTS events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    service_key VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_events_incident_id (incident_id),
    CONSTRAINT fk_events_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB;

-- 테스트 DB에 두 테이블 생성 확인
SHOW TABLES FROM incident_desk_test;