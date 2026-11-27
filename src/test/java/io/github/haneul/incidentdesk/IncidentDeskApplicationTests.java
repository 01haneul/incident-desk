package io.github.haneul.incidentdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// 실제 Spring 애플리케이션 구성과 테스트 DB 연결을 확인한다.
// HTTP 서버는 띄우지 않고, 테이블 구조가 엔티티와 맞는지도 검사한다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:${INCIDENT_TEST_DB_PORT:3306}/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class IncidentDeskApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsApplicationWithTestDatabase() {
        // 실제로 연결된 DB가 테스트 전용 DB인지 확인
        assertEquals(
                "incident_desk_test",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );
    }
}