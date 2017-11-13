# First evolution for database.

# --- !Ups

create table users (
  user_id bigint not null,
  user_name varchar(64) not null,
  email varchar(256) not null,
  hash bigint not null,
  salt bigint not null,
  user_role integer not null,
  created_at timestamp not null
);

alter table users add constraint users_user_name_key unique (user_name);
alter table users add constraint users_email_key unique (email);
alter table users add constraint pk_users primary key (user_id);

create sequence users_seq;

create table application_token (
  application_token_id bigint not null,
  user_id bigint not null,
  token bigint not null,
  created_at timestamp not null
);

alter table application_token add constraint application_token_user_id_token_key unique (user_id, token);
alter table application_token add constraint pk_application_token primary key (application_token_id);
alter table application_token add constraint application_token_user_id_fkey foreign key (user_id) references users(user_id) on delete cascade;

create sequence application_token_seq;

create table contract_plan (
  contract_plan_id bigint not null,
  plan_name varchar(256) not null,
  max_form_format integer not null,
  deprecated boolean not null
);

alter table contract_plan add constraint pk_contract_plan primary key (contract_plan_id);
alter table contract_plan add constraint contract_plan_plan_name unique (plan_name);

create sequence contract_plan_seq;

create table contracted_user (
  contracted_user_id bigint not null,
  contract_plan_id bigint not null,
  user_id bigint not null,
  contract_from timestamp not null,
  contract_to timestamp not null
);

create sequence contracted_user_seq;

alter table contracted_user add constraint pk_contracted_user primary key (contracted_user_id);

alter table contracted_user
    add constraint contracted_user_contract_plan_id_fkey foreign key (contract_plan_id) references contract_plan(contract_plan_id);

alter table contracted_user
    add constraint contracted_user_user_id_fkey foreign key (user_id) references users(user_id) on delete cascade;

alter table contracted_user add constraint contracted_user_contract_plan_id_user_id unique (contract_plan_id, user_id);

create table form_config (
  form_config_id bigint not null,
  contracted_user_id bigint not null,
  config_name varchar(256) not null,
  revision bigint not null,
  config text not null,
  comment varchar(8192) not null,
  created_at timestamp not null
);

create sequence form_config_seq;

alter table form_config add constraint pk_form_config primary key (form_config_id);

alter table form_config
   add constraint form_config_contracted_user_id_fkey foreign key (contracted_user_id) references contracted_user(contracted_user_id) on delete cascade;

alter table form_config add constraint form_config_contracted_user_id_config_name_revision unique (contracted_user_id, config_name, revision);

create table form_branch (
  form_branch_id bigint not null,
  form_config_id bigint not null,
  branch_no integer not null
);

alter table form_branch add constraint pk_form_branch primary key (form_branch_id);

alter table form_branch
   add constraint form_branch_form_config_id_fkey foreign key (form_config_id) references form_config(form_config_id) on delete cascade;

create sequence form_branch_seq;

# --- !Downs

drop sequence form_branch_seq;
drop table form_branch;

drop sequence form_config_seq;
drop table form_config;

drop table contracted_user;
drop sequence contracted_user_seq;

drop sequence contract_plan_seq;
drop table contract_plan;

drop sequence application_token_seq;
drop table application_token;

drop sequence users_seq;
drop table users;
