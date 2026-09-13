-- Phase 4: valuation may follow the local market's own quote (a dealer feed in IQD per gram of
-- pure metal) instead of world spot × the dollar rate.

alter table market_setting add column price_source varchar(8) not null default 'WORLD';
alter table market_setting add constraint ck_market_setting_price_source check (price_source in ('WORLD', 'LOCAL'));
