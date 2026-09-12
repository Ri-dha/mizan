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

    public void bind(UUID userId, UUID householdId) {
        jdbcClient.sql("select set_config('app.user_id', ?, true), set_config('app.household_id', ?, true)")
                .param(userId.toString())
                .param(householdId.toString())
                .query().singleRow();
    }
}
