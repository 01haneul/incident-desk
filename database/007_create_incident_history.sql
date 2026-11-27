-- 사건의 배정·인계·확인·해결 이력을 발생 순서대로 보관한다.
CREATE TABLE incident_desk.incident_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    previous_assignee_key VARCHAR(100) NULL,
    new_assignee_key VARCHAR(100) NULL,
    occurred_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_incident_history_order (incident_id, id),

    CONSTRAINT fk_history_incident
        FOREIGN KEY (incident_id)
        REFERENCES incident_desk.incidents (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 테스트 DB에도 동일한 컬럼과 인덱스를 생성한다.
CREATE TABLE incident_desk_test.incident_history
    LIKE incident_desk.incident_history;

-- CREATE TABLE ... LIKE는 외래키를 복사하지 않으므로 별도로 연결한다.
ALTER TABLE incident_desk_test.incident_history
    ADD CONSTRAINT fk_history_incident
    FOREIGN KEY (incident_id)
    REFERENCES incident_desk_test.incidents (id)
    ON DELETE CASCADE;