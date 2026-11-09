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


-- Hardened role and paid-course access controls.
create table if not exists public.course_payments (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references auth.users(id) on delete cascade,
  course_id uuid not null references public.courses(id) on delete cascade,
  provider text not null default 'razorpay',
  provider_order_id text not null unique,
  provider_payment_id text unique,
  amount_paise bigint not null check (amount_paise > 0),
  currency text not null default 'INR',
  status text not null default 'pending' check (status in ('pending','paid','failed','refunded')),
  verified_at timestamptz,
  created_at timestamptz not null default now()
);
alter table public.course_payments enable row level security;

create table if not exists public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  fcm_token text not null unique,
  updated_at timestamptz not null default now()
);
alter table public.device_tokens enable row level security;

create or replace function public.current_user_role()
returns text language sql stable security definer set search_path = public as $$
  select role from public.profiles where id = auth.uid()
$$;

create or replace function public.has_course_access(target_course uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.courses c
    where c.id = target_course and c.published = true and (
      c.is_free = true or exists (
        select 1 from public.course_payments p
        where p.course_id = c.id and p.student_id = auth.uid()
          and p.status = 'paid' and p.verified_at is not null
      )
    )
  )
$$;

-- Users must never be able to promote themselves by editing their profile.
revoke update on public.profiles from authenticated;
grant update (full_name) on public.profiles to authenticated;

drop policy if exists "published courses readable" on public.courses;
create policy "published or staff courses readable" on public.courses for select to authenticated
using (published = true or public.current_user_role() in ('admin','instructor'));
drop policy if exists "lessons readable for published courses" on public.lessons;
create policy "lessons require course entitlement or staff" on public.lessons for select to authenticated
using (public.has_course_access(course_id) or public.current_user_role() = 'admin'
  or exists (select 1 from public.courses c where c.id = course_id and c.created_by = auth.uid()));
drop policy if exists "students enroll themselves" on public.enrollments;
create policy "enroll only free courses" on public.enrollments for insert to authenticated
with check (student_id = auth.uid() and exists (
  select 1 from public.courses c where c.id = course_id and c.published = true and c.is_free = true
));
drop policy if exists "published announcements readable" on public.announcements;
create policy "published announcements readable" on public.announcements for select to authenticated
using (published = true or public.current_user_role() in ('admin','instructor'));

drop policy if exists "student sees own payment" on public.course_payments;
create policy "student sees own payment" on public.course_payments for select to authenticated
using (student_id = auth.uid());
drop policy if exists "student creates pending payment" on public.course_payments;
create policy "student creates pending payment" on public.course_payments for insert to authenticated
with check (student_id = auth.uid() and status = 'pending' and verified_at is null
  and exists (select 1 from public.courses c where c.id = course_id and c.published = true and c.is_free = false));
-- No client update policy: only a trusted payment webhook using service_role may mark a payment paid.

drop policy if exists "device token own rows" on public.device_tokens;
create policy "device token own rows" on public.device_tokens for all to authenticated
using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Admins can manage catalogue; instructors can manage only courses they own.
drop policy if exists "staff creates courses" on public.courses;
create policy "staff creates courses" on public.courses for insert to authenticated
with check (public.current_user_role() = 'admin' or (public.current_user_role() = 'instructor' and created_by = auth.uid()));
drop policy if exists "staff updates courses" on public.courses;
create policy "staff updates courses" on public.courses for update to authenticated
using (public.current_user_role() = 'admin' or (public.current_user_role() = 'instructor' and created_by = auth.uid()))
with check (public.current_user_role() = 'admin' or (public.current_user_role() = 'instructor' and created_by = auth.uid()));
drop policy if exists "staff deletes courses" on public.courses;
create policy "staff deletes courses" on public.courses for delete to authenticated
using (public.current_user_role() = 'admin' or (public.current_user_role() = 'instructor' and created_by = auth.uid()));

drop policy if exists "staff manages lessons" on public.lessons;
create policy "staff manages lessons" on public.lessons for all to authenticated
using (public.current_user_role() = 'admin' or exists (
  select 1 from public.courses c where c.id = course_id and c.created_by = auth.uid()
))
with check (public.current_user_role() = 'admin' or exists (
  select 1 from public.courses c where c.id = course_id and c.created_by = auth.uid()
));

drop policy if exists "staff manages announcements" on public.announcements;
create policy "staff manages announcements" on public.announcements for all to authenticated
using (public.current_user_role() = 'admin' or public.current_user_role() = 'instructor')
with check (public.current_user_role() = 'admin' or public.current_user_role() = 'instructor');
