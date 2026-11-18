# ADR api-1 — Response fields are required unless `@Nullable`

- Date: 2026-11-18
- Status: accepted

## Context
springdoc marks a schema property required only when it carries `@NotNull`/`@NotBlank` or
`@Schema(requiredMode = REQUIRED)`. Response records carry neither, so every response field was
optional in `/v3/api-docs` and the clients' openapi-typescript types read `id?: string`. The
strictly typed `packages/core` in MizanApp then needed runtime `required()` guards for values the
server always sends (ADR mobile-3 there). Annotating every field with `@Schema` would be noisy and
easy to forget on the next record.

## Decision
- `common/config/RequiredResponseFields`, a swagger `ModelConverter` bean, marks every component
  of a *response record* required unless the component is annotated
  `org.jspecify.annotations.Nullable`.
- A response record is a Java record whose simple name ends in `Response`, or a record nested in
  one (`KeyMemberResponse.HeldKey`, `ResetPreviewResponse.Household`).
- Request records are untouched: their bean-validation annotations still decide `required`, so an
  unannotated request field (`RefreshRequest.refreshToken`) stays optional.
- `@Nullable` on a response component is the contract that the server may send `null` or omit it.
  Every component the code can leave null carries it (for example `ConflictResponse.clientValue`,
  `MyKeysResponse.account`, `InvitationResponse.token`).
- `OpenApiDocumentTest` pins the rule against the generated document.

## Consequences
- Adding a field to a `*Response` record makes it required in the document; a field that can be
  null must be written `@Nullable` (`java.time.@Nullable Instant` when the type is qualified,
  since the jspecify annotation is a type-use annotation).
- Records returned directly from services without the `Response` suffix (the operator dashboard's
  `AccountSummary`, `OperatorSummary`, `Overview`, `FeedStatus`, `Quota`, `AuditSearch.Entry`) keep
  every field optional. Renaming or moving them to `*Response` DTOs, after checking which fields
  can be null, opts them in.
- The wire format is unchanged; only the document is stricter. Clients regenerate their types
  (`npm run api:types` in MizanApp) and can drop the guards for fields now required.
