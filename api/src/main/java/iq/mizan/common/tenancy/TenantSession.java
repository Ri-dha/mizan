package iq.mizan.common.tenancy;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Binds a household to the current transaction for work that has no signed-in caller, such
 * as seeding a new household's defaults during registration. The connection user stays as it
 * is; the policies still apply because every household table is FORCE ROW LEVEL SECURITY.
 */
@Component
@AllArgsConstructor
public class TenantSession {

    private final JdbcClient jdbcClient;

    /** BR-16: household aggregates run as the system so every member's private rows count without being itemised. */
    public void elevateToSystem() {
        jdbcClient.sql("select set_config('app.system', 'on', true)").query().singleRow();
    }

    private static final String ON = "on";
    private static final String OFF = "off";

    /**
     * Everything the policies read, in one statement: the role the session runs as, who and which
     * household, whether it is restricted to its own rows (§4.2, dependents) and whether it may
     * read the advisor reports (ADR e2e-1). One round trip per transaction instead of four.
     */
    public void bind(String role, UUID userId, UUID householdId, boolean restricted, boolean readsReports) {
        jdbcClient.sql("select set_config('role', ?, true), set_config('app.user_id', ?, true), set_config('app.household_id', ?, true), "
                        + "set_config('app.restricted', ?, true), set_config('app.reports', ?, true)")
                .param(role)
                .param(userId.toString())
                .param(householdId.toString())
                .param(restricted ? ON : OFF)
                .param(readsReports ? ON : OFF)
                .query().singleRow();
    }
}
