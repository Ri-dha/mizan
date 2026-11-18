-- ADR e2e-4: the current version of each household's shared keys, and grants that outlive a
-- deleted account so a rotation can tell that someone's access ended.
create table household_key_state (
    household_id    uuid         not null references household (id) on delete cascade,
    key_kind        varchar(10)  not null,
    current_version integer      not null,
    rotated_at      timestamptz,
    primary key (household_id, key_kind),
    constraint ck_household_key_state_kind check (key_kind in ('HOUSEHOLD', 'REPORT')),
    constraint ck_household_key_state_version check (current_version >= 1)
);
call apply_household_rls('household_key_state', false);
grant select, insert, update, delete on household_key_state to mizan_app;

insert into household_key_state (household_id, key_kind, current_version)
select household_id, key_kind, max(key_version) from household_key_grant group by household_id, key_kind;

alter table household_key_grant alter column user_id drop not null;
alter table household_key_grant drop constraint household_key_grant_user_id_fkey;
alter table household_key_grant add constraint household_key_grant_user_id_fkey
    foreign key (user_id) references app_user (id) on delete set null;
