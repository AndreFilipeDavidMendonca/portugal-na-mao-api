alter table media_item
alter column external_id type text,
    alter column thumb_url type text;

alter table media_item
alter column title type varchar(500);