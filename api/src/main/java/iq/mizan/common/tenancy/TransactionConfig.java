package iq.mizan.common.tenancy;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(order = TransactionConfig.TRANSACTION_ADVISOR_ORDER)
public class TransactionConfig {

    static final int TRANSACTION_ADVISOR_ORDER = 100;
}
