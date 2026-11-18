-- ADR csp-and-job-lock-1: one API instance runs each scheduled job.
create table scheduled_job_lock (
    name         varchar(64)  primary key,
    locked_until timestamptz  not null,
    locked_at    timestamptz  not null,
    locked_by    varchar(128) not null
);

grant select, insert, update on scheduled_job_lock to mizan_app;
