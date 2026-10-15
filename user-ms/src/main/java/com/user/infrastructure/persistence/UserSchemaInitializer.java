package com.user.infrastructure.persistence;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * JPA(ddl-auto)가 만들지 못하는 p_users 제약을 기동 시 추가한다 (docs/design/06 테이블 명세서 p_users).
 * <ul>
 *     <li>부분 UNIQUE: 활성 사용자 사이에서만 slack_id 중복 금지</li>
 *     <li>CHECK: 본사(HQ) 소속은 organization_id를 가질 수 없음</li>
 * </ul>
 * role·organization_type 값 CHECK는 Hibernate가 enum 컬럼에 자동으로 만든다.
 *
 * <p>docker/postgres/init SQL은 테이블이 생기기 전에 실행되므로 여기서 만든다.
 * EntityManagerFactory에 의존해 테이블 생성(ddl-auto) 뒤에 실행된다. PostgreSQL이 아니면(H2 테스트) 건너뛴다.
 * 모든 SQL은 여러 번 실행해도 결과가 같다.
 */
@Slf4j
@Component
public class UserSchemaInitializer {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final String schema;

    public UserSchemaInitializer(JdbcTemplate jdbc, DataSource dataSource, EntityManagerFactory ddlCompleted,
                                 @Value("${db.schema}") String schema) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @PostConstruct
    void createConstraints() throws SQLException {
        if (!isPostgres()) {
            log.debug("[UserSchemaInitializer] PostgreSQL이 아니므로 부분 UNIQUE·CHECK 생성을 건너뜀");
            return;
        }
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_p_users_slack_id_active "
                + "ON " + schema + ".p_users (slack_id) WHERE deleted_at IS NULL");
        jdbc.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_p_users_hq_no_organization') THEN
                        ALTER TABLE %s.p_users
                            ADD CONSTRAINT ck_p_users_hq_no_organization
                            CHECK (organization_type <> 'HQ' OR organization_id IS NULL);
                    END IF;
                END $$
                """.formatted(schema));
    }

    private boolean isPostgres() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        }
    }
}
