-- T-world-rates: the rest of the world's currencies per US dollar, one row per currency and day.
-- Public data like prices: no household, no row-level security. The dinar is not stored here;
-- it comes from the Baghdad market and Central Bank feeds.

create table world_rate (
    currency    char(3)        not null,
    day         date           not null,
    per_usd     numeric(24,10) not null check (per_usd > 0),
    source      varchar(40)    not null,
    fetched_at  timestamptz    not null,
    primary key (currency, day)
);

create index ix_world_rate_day on world_rate (day desc);

grant select, insert, update on world_rate to mizan_app;
