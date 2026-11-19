-- Cardino cloud storage setup (Supabase)
-- Run this whole script once in Supabase Dashboard -> SQL Editor.
-- Cards are public share-by-link content. Do not store private/sensitive information.

create table if not exists public.cardino_cards (
  code text primary key check (code ~ '^[A-Za-z0-9]{10}$'),
  payload jsonb not null
    check (jsonb_typeof(payload) = 'object' and octet_length(payload::text) <= 12000),
  created_at timestamptz not null default now()
);

alter table public.cardino_cards enable row level security;
revoke all on public.cardino_cards from anon, authenticated;
grant insert on public.cardino_cards to anon;

drop policy if exists "Cardino: anonymous can create cards" on public.cardino_cards;
create policy "Cardino: anonymous can create cards"
  on public.cardino_cards
  for insert to anon
  with check (
    code ~ '^[A-Za-z0-9]{10}$'
    and jsonb_typeof(payload) = 'object'
    and octet_length(payload::text) <= 12000
  );

-- Recipients can fetch one card by its random code; the table itself cannot be listed.
create or replace function public.get_cardino_card(p_code text)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select payload
  from public.cardino_cards
  where code = p_code
    and p_code ~ '^[A-Za-z0-9]{10}$'
  limit 1;
$$;

revoke all on function public.get_cardino_card(text) from public, anon, authenticated;
grant execute on function public.get_cardino_card(text) to anon;

-- Public media is readable by possession of its object URL.
-- Uploads are limited to 20 MiB and the media formats used by Cardino.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'cardino-media',
  'cardino-media',
  true,
  20971520,
  array[
    'image/jpeg',
    'audio/mpeg',
    'audio/mp4',
    'audio/aac',
    'audio/wav',
    'audio/x-wav',
    'audio/ogg',
    'audio/webm',
    'audio/flac',
    'audio/x-m4a'
  ]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Cardino: upload only card media paths" on storage.objects;
create policy "Cardino: upload only card media paths"
  on storage.objects
  for insert to anon
  with check (
    bucket_id = 'cardino-media'
    and name ~ '^cards/[A-Za-z0-9]{10}/(photo\.jpg|music\.(mp3|m4a|mp4|aac|wav|ogg|oga|webm|flac))$'
  );
