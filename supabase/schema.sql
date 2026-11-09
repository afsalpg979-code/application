-- PixelToonz Academy database schema
-- Run this in Supabase SQL Editor. Keep Row Level Security enabled.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '',
  role text not null default 'student' check (role in ('student','instructor','admin')),
  created_at timestamptz not null default now()
);
create table if not exists public.courses (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text not null default '',
  thumbnail_url text,
  level text not null default 'Beginner',
  is_free boolean not null default true,
  price numeric(10,2) not null default 0,
  published boolean not null default false,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
create table if not exists public.lessons (
  id uuid primary key default gen_random_uuid(),
  course_id uuid not null references public.courses(id) on delete cascade,
  title text not null,
  description text not null default '',
  video_url text,
  duration_minutes integer not null default 0,
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);
create table if not exists public.enrollments (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  course_id uuid not null references public.courses(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(student_id, course_id)
);
create table if not exists public.lesson_progress (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  lesson_id uuid not null references public.lessons(id) on delete cascade,
  completed boolean not null default false,
  updated_at timestamptz not null default now(),
  unique(student_id, lesson_id)
);
create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  body text not null default '',
  published boolean not null default false,
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, full_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'full_name',''));
  return new;
end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute procedure public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.courses enable row level security;
alter table public.lessons enable row level security;
alter table public.enrollments enable row level security;
alter table public.lesson_progress enable row level security;
alter table public.announcements enable row level security;

drop policy if exists "profiles read own" on public.profiles;
create policy "profiles read own" on public.profiles for select to authenticated using (id = auth.uid());
drop policy if exists "profiles update own" on public.profiles;
create policy "profiles update own" on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
drop policy if exists "published courses readable" on public.courses;
create policy "published courses readable" on public.courses for select to authenticated using (published = true);
drop policy if exists "lessons readable for published courses" on public.lessons;
create policy "lessons readable for published courses" on public.lessons for select to authenticated using (
  exists (select 1 from public.courses c where c.id = course_id and c.published = true)
);
drop policy if exists "students read own enrollments" on public.enrollments;
create policy "students read own enrollments" on public.enrollments for select to authenticated using (student_id = auth.uid());
drop policy if exists "students enroll themselves" on public.enrollments;
create policy "students enroll themselves" on public.enrollments for insert to authenticated with check (student_id = auth.uid());
drop policy if exists "students read own progress" on public.lesson_progress;
create policy "students read own progress" on public.lesson_progress for select to authenticated using (student_id = auth.uid());
drop policy if exists "students write own progress" on public.lesson_progress;
create policy "students write own progress" on public.lesson_progress for insert to authenticated with check (student_id = auth.uid());
drop policy if exists "students update own progress" on public.lesson_progress;
create policy "students update own progress" on public.lesson_progress for update to authenticated using (student_id = auth.uid()) with check (student_id = auth.uid());
drop policy if exists "published announcements readable" on public.announcements;
create policy "published announcements readable" on public.announcements for select to authenticated using (published = true);

-- Admins: set profiles.role = 'admin' manually in SQL editor after creating your admin account.
-- Add admin-only policies before enabling any client-side course/lesson management UI.
