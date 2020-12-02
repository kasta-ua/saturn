create type saturn_state as enum('running', 'completed', 'failed');

create table saturn (
  id serial primary key,
  name text not null,
  args text,
  state saturn_state not null,
  scheduled_at timestamptz not null,
  started_at timestamptz not null default now(),
  completed_at timestamptz
);

create index saturn_state_idx on saturn(state);
create index saturn_scheduled_at_idx on saturn(scheduled_at);
