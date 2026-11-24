-- 장애 사건과 수신 이벤트를 저장하는 초기 테이블 생성.
-- 테이블 생성 권한이 있는 관리자 계정으로 실행한다.

USE incident_desk;

-- 담당자가 확인하고 해결하는 장애 사건
CREATE TABLE incidents (
    -- BIGINT: Java의 Long으로 다룰 정수 식별자
    -- AUTO_INCREMENT: 새 행을 저장할 때 DB가 번호 생성
    -- NOT NULL: 필수값이 빠진 데이터 저장 방지
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_key VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    -- 마이크로초 정밀도로 시각 저장. 애플리케이션에서 UTC값을 넣을 예정
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id)

-- 트랜잭션과 외래키를 지원하는 저장 엔진
) ENGINE = InnoDB;

-- 접수 당시의 장애 정보를 보존하고 해당 사건에 연결
CREATE TABLE events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    service_key VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    received_at DATETIME(6) NOT NULL,

    -- 각 이벤트를 고유하게 식별하는 기본키
    PRIMARY KEY (id),

    -- 특정 사건에 연결된 이벤트를 조회하기 위한 인덱스
    INDEX idx_events_incident_id (incident_id),

    -- 존재하지 않는 사건에 이벤트가 연결되지 않도록 제한
    CONSTRAINT fk_events_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents (id)

        -- 이벤트가 연결된 사건의 삭제 차단
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB;
FH
SHOW TABLES FROM incident_desk;

-- 장애 사건 테이블의 컬럼과 기본키 확인
SHOW CREATE TABLE incident_desk.incidents;

-- 이벤트 테이블의 컬럼·인덱스·외래키 확인
SHOW CREATE TABLE incident_desk.events;