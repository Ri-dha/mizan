-- Household membership beyond the owner (FR-ACC-05..07): invitations by link, a current
-- membership per user, household deletion with a grace period, and each member's privacy
-- defaults (BR-16).

create table household_invitation (
    id            uuid         primary key,
    household_id  uuid         not null references household (id) on delete cascade,
    invited_by    uuid         not null references app_user (id) on delete cascade,
    role          varchar(16)  not null,
    contact       varchar(160),
    token_hash    varchar(128) not null,
    expires_at    timestamptz  not null,
    accepted_by   uuid         references app_user (id) on delete set null,
    accepted_at   timestamptz,
    revoked_at    timestamptz,
    created_at    timestamptz  not null default now(),
    constraint ck_household_invitation_role check (role in ('MEMBER', 'VIEWER', 'DEPENDENT'))
);

create unique index ux_household_invitation_token on household_invitation (token_hash);
create index ix_household_invitation_household on household_invitation (household_id) where accepted_at is null and revoked_at is null;

-- A user may belong to several households; tokens are issued for the current one.
alter table membership add column is_current boolean not null default false;
update membership set is_current = true where role = 'OWNER';

alter table household add column deletion_requested_at timestamptz;
alter table household add column deletion_requested_by uuid references app_user (id) on delete set null;

-- Each member's default visibility per record type; the row itself is private to its owner.
create table privacy_setting (
    id            uuid         primary key,
    household_id  uuid         not null references household (id) on delete cascade,
    owner_id      uuid         not null references app_user (id),
    visibility    varchar(8)   not null default 'PRIVATE',
    transactions  varchar(8)   not null default 'SHARED',
    accounts      varchar(8)   not null default 'SHARED',
    metals        varchar(8)   not null default 'SHARED',
    debts         varchar(8)   not null default 'SHARED',
    goals         varchar(8)   not null default 'SHARED',
    assets        varchar(8)   not null default 'SHARED',
    field_clocks  jsonb        not null default '{}'::jsonb,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    deleted_at    timestamptz,
    constraint ck_privacy_setting_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_privacy_setting_values check (
        transactions in ('SHARED', 'PRIVATE') and accounts in ('SHARED', 'PRIVATE') and metals in ('SHARED', 'PRIVATE')
        and debts in ('SHARED', 'PRIVATE') and goals in ('SHARED', 'PRIVATE') and assets in ('SHARED', 'PRIVATE'))
);

call make_syncable('privacy_setting');
