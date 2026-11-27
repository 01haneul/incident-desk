-- 기존 사건은 미배정 상태(NULL)로 유지한다.
ALTER TABLE incident_desk.incidents
    ADD COLUMN assignee_key VARCHAR(100) NULL;

ALTER TABLE incident_desk_test.incidents
    ADD COLUMN assignee_key VARCHAR(100) NULL;