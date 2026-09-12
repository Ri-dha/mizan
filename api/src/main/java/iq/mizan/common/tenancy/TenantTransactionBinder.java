package iq.mizan.common.tenancy;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.common.security.CurrentUserAccessor;

import lombok.AllArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds the caller to the database session at the start of every transaction, so the
 * row-level security policies (V002) decide what each query can see.
 *
 * <p>Ordered after the transaction advisor so the SET LOCAL lands inside the transaction it
 * scopes. Requests with no authenticated user (registration, sign-in) stay as the connection
 * user, whose tables carry no policies.
 */
@Aspect
@Component
@Order(TransactionConfig.TRANSACTION_ADVISOR_ORDER + 1)
@AllArgsConstructor
public class TenantTransactionBinder {

    static final String APP_ROLE = "mizan_app";
    private static final Object BOUND_MARKER = new Object();

    private final CurrentUserAccessor currentUserAccessor;
    private final JdbcClient jdbcClient;

    @Before("within(iq.mizan..*) && (@within(org.springframework.transaction.annotation.Transactional)"
            + " || @annotation(org.springframework.transaction.annotation.Transactional))")
    public void bindCurrentUser() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.hasResource(BOUND_MARKER)) {
            return;
        }
        currentUserAccessor.current().ifPresent(this::bind);
    }

    private void bind(CurrentUser user) {
        jdbcClient.sql("set local role " + APP_ROLE).update();
        jdbcClient.sql("select set_config('app.user_id', ?, true), set_config('app.household_id', ?, true)")
                .param(user.userId().toString())
                .param(user.householdId().toString())
                .query().singleRow();

        TransactionSynchronizationManager.bindResource(BOUND_MARKER, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(BOUND_MARKER);
            }
        });
    }
}
