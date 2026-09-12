-- Accounts, sessions and the audit trail (FR-ACC-01, FR-ACC-02, NFR-04, NFR-07).

create table app_user (
    id                    uuid         primary key,
    email                 varchar(254),
    phone                 varchar(20),
    display_name          varchar(80)  not null,
    password_hash         varchar(200) not null,
    locale                varchar(8)   not null default 'en',
    verified_at           timestamptz,
    account_status        varchar(16)  not null,
    failed_login_attempts integer      not null default 0,
    locked_until          timestamptz,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint ck_app_user_contact check (email is not null or phone is not null)
);

create unique index ux_app_user_email on app_user (lower(email)) where email is not null;
create unique index ux_app_user_phone on app_user (phone) where phone is not null;

-- Codes are stored hashed; a plaintext column would hand a reader every pending account.
create table verification_code (
    id          uuid        primary key,
    user_id     uuid        not null references app_user (id) on delete cascade,
    purpose     varchar(24) not null,
    code_hash   varchar(64) not null,
    expires_at  timestamptz not null,
    attempts    integer     not null default 0,
    consumed_at timestamptz,
    created_at  timestamptz not null default now()
);

create index ix_verification_code_user on verification_code (user_id, purpose, created_at desc);

create table refresh_token (
    id          uuid         primary key,
    user_id     uuid         not null references app_user (id) on delete cascade,
    token_hash  varchar(64)  not null,
    issued_at   timestamptz  not null default now(),
    expires_at  timestamptz  not null,
    revoked_at  timestamptz,
    replaced_by uuid         references refresh_token (id),
    user_agent  varchar(256),
    ip_address  varchar(45)
);

create unique index ux_refresh_token_hash on refresh_token (token_hash);
create index ix_refresh_token_user_active on refresh_token (user_id) where revoked_at is null;

create table audit_entry (
    id           uuid        primary key,
    household_id uuid,
    actor_id     uuid,
    entity_type  varchar(40) not null,
    entity_id    uuid,
    action       varchar(40) not null,
    before_state jsonb,
    after_state  jsonb,
    ip_address   varchar(45),
    occurred_at  timestamptz not null default now()
);

create index ix_audit_entry_household on audit_entry (household_id, occurred_at desc);
create index ix_audit_entry_actor on audit_entry (actor_id, occurred_at desc);

-- Append-only, enforced where it cannot be bypassed by application code.
create function audit_entry_immutable() returns trigger language plpgsql as $$
begin
    raise exception 'audit_entry is append-only';
end $$;

create trigger trg_audit_entry_immutable
    before update or delete on audit_entry
    for each row execute function audit_entry_immutable();
