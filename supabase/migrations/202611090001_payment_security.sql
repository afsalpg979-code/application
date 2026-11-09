-- Apply after the base supabase/schema.sql.
-- Payment rows are created/updated only by trusted Edge Functions using service_role.
drop policy if exists "student creates pending payment" on public.course_payments;

-- Ensure paid-course access is based on a verified, captured payment.
create or replace function public.has_course_access(target_course uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from public.courses c
    where c.id = target_course and c.published = true and (
      c.is_free = true or exists (
        select 1 from public.course_payments p
        where p.course_id = c.id
          and p.student_id = auth.uid()
          and p.status = 'paid'
          and p.verified_at is not null
      )
    )
  );
$$;

-- Do not expose payment mutation privileges to mobile clients.
revoke insert, update, delete on public.course_payments from anon, authenticated;
grant select on public.course_payments to authenticated;

-- Prevent students from creating paid enrollments directly.
drop policy if exists "enroll only free courses" on public.enrollments;
create policy "enroll only free courses" on public.enrollments
for insert to authenticated
with check (
  student_id = auth.uid()
  and exists (
    select 1 from public.courses c
    where c.id = course_id and c.published = true and c.is_free = true
  )
);
