-- 담당자 변경이 확정됐을 때 전송할 알림을 저장한다.
CREATE TABLE incident_desk.incident_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    recipient_key VARCHAR(100) NOT NULL,
    notification_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_notification_due (status, next_attempt_at, id),
    CONSTRAINT fk_notification_incident
        FOREIGN KEY (incident_id)
        REFERENCES incident_desk.incidents (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- LIKE는 컬럼과 인덱스를 복사한다. 외래키는 테스트 DB에 따로 연결한다.
CREATE TABLE incident_desk_test.incident_notifications
    LIKE incident_desk.incident_notifications;

ALTER TABLE incident_desk_test.incident_notifications
    ADD CONSTRAINT fk_notification_incident
    FOREIGN KEY (incident_id)
    REFERENCES incident_desk_test.incidents (id)
    ON DELETE CASCADE;