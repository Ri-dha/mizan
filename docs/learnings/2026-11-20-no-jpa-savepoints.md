# 2026-11-20 — transactions in the API
Savepoints are not available through JPA here: with Spring 7 and Hibernate, a nested transaction or
`TransactionStatus.createSavepoint()` fails with "JpaDialect does not support savepoints" (and,
before that, "nestedTransactionAllowed" is false on `JpaTransactionManager`). To let one statement
fail without aborting the whole transaction, avoid the error instead: check before writing, or use
`insert … on conflict do nothing` and read the row count (as `VaultRowRepository.insert` does).
Source: T-sync-rejected-ops
