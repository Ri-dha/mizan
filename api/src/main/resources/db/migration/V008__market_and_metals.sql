-- Market data (FR-MKT-01..05) is global, not per household: one cached quote per instrument
-- and one row per instrument per day for charts and month-end snapshots.

create table price_quote (
    instrument    varchar(24)  primary key,
    price_micros  bigint       not null,
    source        varchar(40)  not null,
    quoted_at     timestamptz  not null,
    fetched_at    timestamptz  not null
);

create table price_history (
    instrument    varchar(24)  not null,
    day           date         not null,
    price_micros  bigint       not null,
    source        varchar(40)  not null,
    primary key (instrument, day)
);

grant select, insert, update, delete on price_quote, price_history to mizan_app;

-- Metals ledger (FR-MTL-01..09). Weight in milligrams; money in whole minor units of the
-- purchase currency with the FX rate frozen at purchase (BR-09).

create table metal_lot (
    id                  uuid         primary key,
    household_id        uuid         not null references household (id) on delete cascade,
    owner_id            uuid         not null references app_user (id),
    visibility          varchar(8)   not null default 'SHARED',
    metal               varchar(8)   not null,
    purity_label        varchar(8)   not null,
    purity_basis_points integer      not null,
    weight_mg           bigint       not null,
    weight_unit_entered varchar(12)  not null,
    quantity_entered    varchar(24)  not null,
    purchase_date       date         not null,
    metal_cost          bigint       not null,
    making_charge       bigint       not null default 0,
    fees                bigint       not null default 0,
    currency            varchar(3)   not null,
    fx_rate_micros      bigint       not null default 1000000,
    form                varchar(12)  not null,
    dealer              varchar(80),
    location            varchar(80),
    serial              varchar(80),
    held_for            varchar(80),
    note                text,
    field_clocks        jsonb        not null default '{}'::jsonb,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    deleted_at          timestamptz,
    constraint ck_metal_lot_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_metal_lot_metal check (metal in ('GOLD', 'SILVER')),
    constraint ck_metal_lot_form check (form in ('COIN', 'BAR', 'JEWELLERY', 'SCRAP')),
    constraint ck_metal_lot_purity check (purity_basis_points between 1 and 10000)
);

create index ix_metal_lot_household on metal_lot (household_id) where deleted_at is null;
call make_syncable('metal_lot');

-- The method is stored on the sale so a later change of default never rewrites it (BR-11).
create table metal_disposal (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    metal           varchar(8)   not null,
    sold_on         date         not null,
    weight_mg       bigint       not null,
    proceeds        bigint       not null,
    fees            bigint       not null default 0,
    currency        varchar(3)   not null,
    fx_rate_micros  bigint       not null default 1000000,
    method          varchar(20)  not null,
    buyer           varchar(80),
    note            text,
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_metal_disposal_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_metal_disposal_method check (method in ('FIFO', 'SPECIFIC', 'WEIGHTED_AVERAGE'))
);

create index ix_metal_disposal_household on metal_disposal (household_id) where deleted_at is null;
call make_syncable('metal_disposal');

create table metal_disposal_lot (
    id             uuid         primary key,
    household_id   uuid         not null references household (id) on delete cascade,
    owner_id       uuid         not null references app_user (id),
    visibility     varchar(8)   not null default 'SHARED',
    disposal_id    uuid         not null references metal_disposal (id) deferrable initially deferred,
    lot_id         uuid         not null references metal_lot (id) deferrable initially deferred,
    weight_mg      bigint       not null,
    metal_cost     bigint       not null,
    making_charge  bigint       not null,
    fees           bigint       not null,
    field_clocks   jsonb        not null default '{}'::jsonb,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    deleted_at     timestamptz,
    constraint ck_metal_disposal_lot_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_metal_disposal_lot_lot on metal_disposal_lot (lot_id) where deleted_at is null;
call make_syncable('metal_disposal_lot');

-- A user-entered price supersedes the feed until a newer one is entered (FR-MTL-09, BR-10).
-- For XAU and XAG the price is IQD per gram of pure metal; for USDIQD it is IQD per dollar.
create table market_override (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    instrument      varchar(24)  not null,
    price_micros    bigint       not null,
    effective_from  date         not null,
    note            text,
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_market_override_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_market_override_household on market_override (household_id, instrument) where deleted_at is null;
call make_syncable('market_override');

-- One row per household: which dollar rate governs valuation (FR-MKT-03), the local premium
-- (FR-MTL-10) and the default disposal method per metal (BR-11).
create table market_setting (
    id                       uuid         primary key,
    household_id             uuid         not null references household (id) on delete cascade,
    owner_id                 uuid         not null references app_user (id),
    visibility               varchar(8)   not null default 'SHARED',
    rate_kind                varchar(10)  not null default 'PARALLEL',
    gold_premium_basis_points   integer   not null default 0,
    silver_premium_basis_points integer   not null default 0,
    gold_method              varchar(20)  not null default 'FIFO',
    silver_method            varchar(20)  not null default 'FIFO',
    field_clocks             jsonb        not null default '{}'::jsonb,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    deleted_at               timestamptz,
    constraint ck_market_setting_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_market_setting_rate_kind check (rate_kind in ('OFFICIAL', 'PARALLEL'))
);

create unique index ux_market_setting_household on market_setting (household_id) where deleted_at is null;
call make_syncable('market_setting');
