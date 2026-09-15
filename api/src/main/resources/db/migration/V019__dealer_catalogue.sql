-- Baghdad Bullion House: the local market's bid and ask per gram become the household's
-- default price source, and the dealer's product list is kept for the metals screen.

create table market_catalogue (
    source      varchar(40)  primary key,
    fetched_at  timestamptz  not null,
    as_of       timestamptz  not null,
    payload     text         not null
);

grant select, insert, update, delete on market_catalogue to mizan_app;

alter table market_setting alter column price_source set default 'LOCAL';
update market_setting set price_source = 'LOCAL' where price_source = 'WORLD';
