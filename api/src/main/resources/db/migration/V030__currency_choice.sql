-- T-any-base-currency: a person's country and the rates they follow, and a household's history of
-- base-currency changes. Records are sealed on devices, so the server cannot convert them; it
-- keeps each change's factor and lets one device per member claim the conversion of that
-- member's private records (ADR currency-1).

alter table app_user
    add column country            varchar(2)   not null default 'IQ',
    add column tracked_currencies varchar(400);

alter table household
    add column base_generation integer not null default 0;

alter table membership
    add column rebased_generation integer not null default 0;

create table household_rebase (
    household_id   uuid           not null references household (id) on delete cascade,
    generation     integer        not null,
    from_currency  char(3)        not null,
    to_currency    char(3)        not null,
    -- New base units per old base unit, in whole units (a dinar, a dollar), not minor units.
    factor         numeric(38,18) not null check (factor > 0),
    changed_by     uuid           references app_user (id) on delete set null,
    created_at     timestamptz    not null,
    primary key (household_id, generation)
);

grant select, insert on household_rebase to mizan_app;

-- The shekel is not offered (product decision); its rows are dropped and no longer fetched.
delete from world_rate where currency = 'ILS';
