# 2026-11-18 — Regenerating API types when port 5432 is taken
`npm run api:types` needs a running API. When the dev database cannot start, export the
document from the test context instead: a throwaway test extending `PostgresIntegrationTest`
that writes `GET /v3/api-docs` to a file, then
`npx openapi-typescript <file> -o src/api/generated.d.ts`. Delete the test and its
`target/surefire-reports` entries afterwards so the test count stays honest.
Source: T-e2e-encryption
