# First evolution.

# --- !Ups

create table users (
  user_id bigint not null,
  user_name varchar(64) not null unique,
  email varchar(255) not null,
  hash bigint not null,
  salt bigint not null,
  created_at timestamp not null
);

create sequence users_seq;

# --- !Downs

drop sequence users_seq;
drop table users;
