-- Cardino cloud storage setup (Supabase)
-- Run this whole script once in Supabase Dashboard -> SQL Editor.
-- Cardino is a public share-by-link product: anyone with a card code can read that card.
-- Never store private or sensitive information in shared cards.

create table if not exists public.cardino_cards (
  code text primary key
    check (code ~ '^[A-Za-z0-9]{10}$'),
  payload jsonb not null
    check (jsonb_typeof(payload) = 'object' and octet_length(payload::text) <= 12000),
  created_at timestamptz not null default now()
);

alter table public.cardino_cards enable row level security;
grant select, insert on public.cardino_cards to anon;

drop policy if exists "Cardino: public can read cards by code" on public.cardino_cards;
create policy "Cardino: public can read cards by code"
  on public.cardino_cards
  for select
  to anon
  using (true);

drop policy if exists "Cardino: anonymous can create cards" on public.cardino_cards;
create policy "Cardino: anonymous can create cards"
  on public.cardino_cards
  for insert
  to anon
  with check (
    code ~ '^[A-Za-z0-9]{10}$'
    and jsonb_typeof(payload) = 'object'
    and octet_length(payload::text) <= 12000
  );

-- Media is public because recipients must be able to play it without signing in.
-- Max file size: 20 MiB. Only the types used by Cardino are accepted.
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

drop policy if exists "Cardino: public media read" on storage.objects;
create policy "Cardino: public media read"
  on storage.objects
  for select
  to anon
  using (bucket_id = 'cardino-media');

drop policy if exists "Cardino: upload only card media paths" on storage.objects;
create policy "Cardino: upload only card media paths"
  on storage.objects
  for insert
  to anon
  with check (
    bucket_id = 'cardino-media'
    and name ~ '^cards/[A-Za-z0-9]{10}/(photo\.jpg|music\.(mp3|m4a|mp4|aac|wav|ogg|oga|webm|flac))$'
  );
