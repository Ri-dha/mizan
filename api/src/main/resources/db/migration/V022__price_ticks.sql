-- T-price-history: every change of a price, and the day's range alongside its close.
-- Prices are public data: no household, no row-level security.

create table price_tick (
    id            bigserial    primary key,
    instrument    varchar(24)  not null,
    observed_at   timestamptz  not null,
    price_micros  bigint       not null,
    source        varchar(40)  not null,
    constraint uq_price_tick unique (instrument, observed_at)
);

create index ix_price_tick_instrument_time on price_tick (instrument, observed_at desc);

-- Append-only: a correction is a new tick, never an edit (ADR prices-2).
create function price_tick_immutable() returns trigger language plpgsql as $$
begin
    raise exception 'price_tick rows cannot be changed';
end;
$$;

create trigger trg_price_tick_immutable before update on price_tick
    for each row execute function price_tick_immutable();

alter table price_history
    add column open_micros bigint,
    add column high_micros bigint,
    add column low_micros  bigint;

update price_history set open_micros = price_micros, high_micros = price_micros, low_micros = price_micros;

alter table price_history
    alter column open_micros set not null,
    alter column high_micros set not null,
    alter column low_micros  set not null;

grant select, insert on price_tick to mizan_app;
grant usage on sequence price_tick_id_seq to mizan_app;
