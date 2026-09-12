package iq.mizan.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs against real Postgres: the row-level security policies and the audit trigger are the
 * point of these tests and nothing else reproduces them. One container for the whole suite,
 * started once and reaped when the JVM exits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** TRUNCATE skips row triggers, which is what lets the append-only audit table be cleared. */
    protected void resetTransactionalData() {
        jdbcTemplate.execute("truncate table bucket, plan, income_receipt, income_source, cash_account, sync_conflict, sync_op_receipt, sync_log, "
                + "sync_device, audit_entry, refresh_token, verification_code, membership, household, "
                + "app_user cascade");
    }
}
