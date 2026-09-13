-- Other assets (FR-AST-01..06): vehicles, property, electronics and the rest, each with a
-- valuation history. Money in whole minor units; the purchase rate is frozen (BR-09).

create table asset (
    id                        uuid         primary key,
    household_id              uuid         not null references household (id) on delete cascade,
    owner_id                  uuid         not null references app_user (id),
    visibility                varchar(8)   not null default 'SHARED',
    type                      varchar(16)  not null,
    name                      varchar(80)  not null,
    purchase_date             date,
    purchase_price            bigint       not null default 0,
    currency                  varchar(3)   not null,
    fx_rate_micros            bigint       not null default 1000000,
    liquidity                 varchar(8)   not null,
    depreciation_method       varchar(20)  not null default 'NONE',
    annual_rate_basis_points  integer      not null default 0,
    salvage_value             bigint       not null default 0,
    attributes                jsonb        not null default '{}'::jsonb,
    status                    varchar(8)   not null default 'HELD',
    sold_on                   date,
    sale_price                bigint,
    note                      text,
    sort_order                integer      not null default 0,
    field_clocks              jsonb        not null default '{}'::jsonb,
    created_at                timestamptz  not null default now(),
    updated_at                timestamptz  not null default now(),
    deleted_at                timestamptz,
    constraint ck_asset_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_asset_type check (type in ('VEHICLE', 'PROPERTY', 'ELECTRONICS', 'EQUIPMENT', 'FURNITURE', 'LIVESTOCK', 'OTHER')),
    constraint ck_asset_liquidity check (liquidity in ('LIQUID', 'ILLIQUID')),
    constraint ck_asset_depreciation check (depreciation_method in ('NONE', 'STRAIGHT_LINE', 'DECLINING_BALANCE')),
    constraint ck_asset_status check (status in ('HELD', 'SOLD')),
    constraint ck_asset_rate check (annual_rate_basis_points between 0 and 10000)
);

create index ix_asset_household on asset (household_id) where deleted_at is null;
call make_syncable('asset');

create table asset_valuation (
    id            uuid         primary key,
    household_id  uuid         not null references household (id) on delete cascade,
    owner_id      uuid         not null references app_user (id),
    visibility    varchar(8)   not null default 'SHARED',
    asset_id      uuid         not null references asset (id) deferrable initially deferred,
    valued_on     date         not null,
    value         bigint       not null,
    source        varchar(10)  not null default 'MANUAL',
    note          text,
    field_clocks  jsonb        not null default '{}'::jsonb,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    deleted_at    timestamptz,
    constraint ck_asset_valuation_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_asset_valuation_source check (source in ('MANUAL', 'ESTIMATED'))
);

create index ix_asset_valuation_asset on asset_valuation (asset_id, valued_on desc) where deleted_at is null;
call make_syncable('asset_valuation');

-- FR-NET-06: snapshots record the liquid split; earlier snapshots keep null.
alter table net_worth_snapshot add column liquid_assets bigint;
alter table net_worth_snapshot add column illiquid_assets bigint;
