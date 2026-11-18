# 2026-09-13 — Reading jsonb columns through JdbcClient
`org.postgresql.util.PGobject` is not on the compile classpath. Select JSON columns as
`column::text` (`SyncColumnType.JSON.selectExpression`) and parse them; write them with
`?::jsonb`. Passing an already-serialised string to a JSON column double-encodes it.
Source: T-m0-m1, T-m5-m7
