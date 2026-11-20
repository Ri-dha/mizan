-- T-support-reports (ADR support-1): problem and feedback reports between a person and the operators.
-- Not household data and not end-to-end encrypted: operators must read them, and the app says so.

create table support_report (
    id                   uuid         primary key,
    user_id              uuid         not null references app_user (id) on delete cascade,
    category             varchar(16)  not null,
    subject              varchar(120) not null,
    status               varchar(16)  not null default 'OPEN',
    app_version          varchar(40),
    platform             varchar(40),
    created_at           timestamptz  not null,
    last_message_at      timestamptz  not null,
    unread_by_user       boolean      not null default false,
    unread_by_operator   boolean      not null default true,
    constraint ck_support_report_category check (category in ('PROBLEM', 'SUGGESTION', 'QUESTION')),
    constraint ck_support_report_status check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED'))
);

create index ix_support_report_user on support_report (user_id, last_message_at desc);
create index ix_support_report_queue on support_report (status, last_message_at desc);

create table support_message (
    id             uuid         primary key,
    report_id      uuid         not null references support_report (id) on delete cascade,
    author_id      uuid         references app_user (id) on delete set null,
    from_operator  boolean      not null,
    body           varchar(4000) not null,
    created_at     timestamptz  not null
);

create index ix_support_message_report on support_message (report_id, created_at);

create table support_attachment (
    id            uuid         primary key,
    report_id     uuid         not null references support_report (id) on delete cascade,
    message_id    uuid         not null references support_message (id) on delete cascade,
    content_type  varchar(20)  not null,
    size_bytes    integer      not null,
    position      smallint     not null,
    created_at    timestamptz  not null,
    constraint ck_support_attachment_type check (content_type in ('image/jpeg', 'image/png'))
);

create index ix_support_attachment_message on support_attachment (message_id);

grant select, insert, update, delete on support_report, support_message, support_attachment to mizan_app;
