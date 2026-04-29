alter table media_item
    add column if not exists entity_type varchar(50),
    add column if not exists entity_id bigint,
    add column if not exists media_type varchar(50),
    add column if not exists storage_key text,
    add column if not exists mime_type varchar(100),
    add column if not exists position integer default 0;

update media_item
set entity_type = 'POI',
    entity_id = poi_id,
    media_type = coalesce(type, 'IMAGE'),
    storage_key = url
where poi_id is not null
  and entity_type is null;

update media_item
set entity_type = 'DISTRICT',
    entity_id = district_id,
    media_type = coalesce(type, 'IMAGE'),
    storage_key = url
where district_id is not null
  and entity_type is null;

insert into media_item (
    type,
    url,
    entity_type,
    entity_id,
    media_type,
    provider,
    storage_key,
    position,
    created_at
)
select
    'IMAGE',
    pi.data,
    'POI',
    pi.poi_id,
    'IMAGE',
    'legacy-poi-image',
    pi.data,
    coalesce(pi.position, 0),
    now()
from poi_image pi
where exists (select 1 from poi p where p.id = pi.poi_id)
  and pi.data is not null;

alter table media_item
    alter column entity_type set not null,
alter column entity_id set not null,
    alter column media_type set not null,
    alter column storage_key set not null;

alter table media_item
drop column if exists poi_id,
    drop column if exists district_id,
    drop column if exists type,
    drop column if exists url;

drop table if exists poi_image;

create index if not exists idx_media_item_entity
    on media_item(entity_type, entity_id);

create index if not exists idx_media_item_entity_position
    on media_item(entity_type, entity_id, position);