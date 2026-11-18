-- GenZ Keyboard community backend (Supabase / PostgreSQL)
--
-- Apply with:  supabase db push   (or paste into the SQL editor)
--
-- Security model: every table is protected by row-level security. The Android app ships only the
-- PUBLISHABLE anon key, which is safe to distribute *because* of these policies. No service-role
-- key ever leaves the server.

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------- profiles
create table if not exists profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  handle       text unique not null check (char_length(handle) between 3 and 32),
  display_name text not null check (char_length(display_name) between 1 and 64),
  bio          text check (char_length(bio) <= 500),
  avatar_url   text,
  created_at   timestamptz not null default now()
);

alter table profiles enable row level security;
create policy "profiles are public"        on profiles for select using (true);
create policy "own profile insert"         on profiles for insert with check (auth.uid() = id);
create policy "own profile update"         on profiles for update using (auth.uid() = id);

-- ---------------------------------------------------------------- themes
create table if not exists themes (
  id            uuid primary key default gen_random_uuid(),
  author_id     uuid not null references profiles(id) on delete cascade,
  name          text not null check (char_length(name) between 1 and 64),
  description   text check (char_length(description) <= 2000),
  tags          text[] not null default '{}',
  category      text not null default 'other',
  -- the ThemeDoc JSON, validated client-side by ThemeCodec before upload
  doc           jsonb not null,
  -- storage paths, never public URLs
  wallpaper_path text,
  preview_path   text,
  version       integer not null default 1,
  is_public     boolean not null default true,
  is_removed    boolean not null default false,
  downloads     integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create index if not exists themes_author_idx  on themes(author_id);
create index if not exists themes_public_idx  on themes(is_public, is_removed, created_at desc);
create index if not exists themes_tags_idx    on themes using gin(tags);

alter table themes enable row level security;
-- anyone may read a public, non-removed theme; authors always see their own
create policy "read public themes" on themes for select
  using ((is_public and not is_removed) or auth.uid() = author_id);
create policy "insert own theme"   on themes for insert with check (auth.uid() = author_id);
create policy "update own theme"   on themes for update using (auth.uid() = author_id);
create policy "delete own theme"   on themes for delete using (auth.uid() = author_id);

-- ---------------------------------------------------------------- reviews (one per user per theme)
create table if not exists reviews (
  id         uuid primary key default gen_random_uuid(),
  theme_id   uuid not null references themes(id) on delete cascade,
  author_id  uuid not null references profiles(id) on delete cascade,
  rating     smallint not null check (rating between 1 and 5),
  body       text check (char_length(body) <= 2000),
  is_removed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (theme_id, author_id)              -- no duplicate reviews from one account
);

alter table reviews enable row level security;
create policy "read reviews"      on reviews for select using (not is_removed);
create policy "insert own review" on reviews for insert with check (auth.uid() = author_id);
create policy "update own review" on reviews for update using (auth.uid() = author_id);
create policy "delete own review" on reviews for delete using (auth.uid() = author_id);

-- ---------------------------------------------------------------- favourites
create table if not exists favourites (
  user_id    uuid not null references profiles(id) on delete cascade,
  theme_id   uuid not null references themes(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, theme_id)
);

alter table favourites enable row level security;
create policy "read own favourites"   on favourites for select using (auth.uid() = user_id);
create policy "insert own favourite"  on favourites for insert with check (auth.uid() = user_id);
create policy "delete own favourite"  on favourites for delete using (auth.uid() = user_id);

-- ---------------------------------------------------------------- design posts
create table if not exists design_posts (
  id         uuid primary key default gen_random_uuid(),
  author_id  uuid not null references profiles(id) on delete cascade,
  title      text not null check (char_length(title) between 1 and 120),
  body       text check (char_length(body) <= 8000),
  tags       text[] not null default '{}',
  image_paths text[] not null default '{}',
  is_removed boolean not null default false,
  created_at timestamptz not null default now()
);

alter table design_posts enable row level security;
create policy "read posts"      on design_posts for select using (not is_removed);
create policy "insert own post" on design_posts for insert with check (auth.uid() = author_id);
create policy "update own post" on design_posts for update using (auth.uid() = author_id);
create policy "delete own post" on design_posts for delete using (auth.uid() = author_id);

create table if not exists post_likes (
  user_id uuid not null references profiles(id) on delete cascade,
  post_id uuid not null references design_posts(id) on delete cascade,
  primary key (user_id, post_id)
);
alter table post_likes enable row level security;
create policy "read likes"   on post_likes for select using (true);
create policy "insert like"  on post_likes for insert with check (auth.uid() = user_id);
create policy "delete like"  on post_likes for delete using (auth.uid() = user_id);

create table if not exists post_comments (
  id         uuid primary key default gen_random_uuid(),
  post_id    uuid not null references design_posts(id) on delete cascade,
  author_id  uuid not null references profiles(id) on delete cascade,
  body       text not null check (char_length(body) between 1 and 2000),
  is_removed boolean not null default false,
  created_at timestamptz not null default now()
);
alter table post_comments enable row level security;
create policy "read comments"      on post_comments for select using (not is_removed);
create policy "insert own comment" on post_comments for insert with check (auth.uid() = author_id);
create policy "delete own comment" on post_comments for delete using (auth.uid() = author_id);

-- ---------------------------------------------------------------- reports + moderation
create table if not exists reports (
  id           uuid primary key default gen_random_uuid(),
  reporter_id  uuid not null references profiles(id) on delete cascade,
  subject_type text not null check (subject_type in ('theme','review','post','comment','profile')),
  subject_id   uuid not null,
  reason       text not null check (char_length(reason) between 3 and 500),
  status       text not null default 'open' check (status in ('open','actioned','dismissed')),
  created_at   timestamptz not null default now(),
  unique (reporter_id, subject_type, subject_id)   -- one report per user per item
);
alter table reports enable row level security;
create policy "insert own report" on reports for insert with check (auth.uid() = reporter_id);
create policy "read own reports"  on reports for select using (auth.uid() = reporter_id);

-- moderation is performed server-side with the service role; the app never holds that key.
create table if not exists moderators (
  user_id uuid primary key references profiles(id) on delete cascade,
  since   timestamptz not null default now()
);
alter table moderators enable row level security;
create policy "read moderators" on moderators for select using (true);

-- ---------------------------------------------------------------- rating aggregate
create or replace view theme_ratings as
select t.id as theme_id,
       coalesce(round(avg(r.rating)::numeric, 2), 0) as average_rating,
       count(r.id) as review_count
from themes t
left join reviews r on r.theme_id = t.id and not r.is_removed
group by t.id;

-- ---------------------------------------------------------------- storage
-- two private-ish buckets: previews are public-read, wallpapers public-read, writes are per-user.
insert into storage.buckets (id, name, public) values
  ('theme-previews', 'theme-previews', true),
  ('theme-wallpapers', 'theme-wallpapers', true),
  ('post-images', 'post-images', true)
on conflict (id) do nothing;

create policy "anyone reads theme previews" on storage.objects for select
  using (bucket_id in ('theme-previews','theme-wallpapers','post-images'));
create policy "authors upload to their folder" on storage.objects for insert
  with check (
    bucket_id in ('theme-previews','theme-wallpapers','post-images')
    and auth.uid()::text = (storage.foldername(name))[1]
  );
create policy "authors delete their own uploads" on storage.objects for delete
  using (auth.uid()::text = (storage.foldername(name))[1]);

-- ---------------------------------------------------------------- version bump
-- The client cannot express "version = version + 1" in a PATCH body, so the server does it.
create or replace function touch_theme() returns trigger as $$
begin
  new.updated_at := now();
  if new.name <> old.name
     or coalesce(new.description,'') <> coalesce(old.description,'')
     or new.category <> old.category
     or new.tags <> old.tags then
    new.version := old.version + 1;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists themes_touch on themes;
create trigger themes_touch before update on themes
  for each row execute function touch_theme();
