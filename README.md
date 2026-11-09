# PixelToonz Academy – Android

Native Android app built with Kotlin and Android views, backed by Supabase.

## Implemented in this iteration
- Email/password student sign-in and registration through Supabase Auth
- Password recovery email request
- Student dashboard and published course catalogue
- Course details, lesson list and external video playback
- Student enrollments and lesson completion writes
- Published announcements and account profile
- Session persistence and sign-out
- GitHub Actions debug APK build

## Required Supabase setup
1. Create a Supabase project.
2. Run [supabase/schema.sql](supabase/schema.sql) in the Supabase SQL Editor.
3. Confirm Email provider is enabled in Authentication > Providers.
4. In GitHub, open Settings > Secrets and variables > Actions and create:
   - `SUPABASE_URL` – your project URL, e.g. `https://project-ref.supabase.co`
   - `SUPABASE_ANON_KEY` – the anon/publishable key from the Supabase API settings.
5. Never add the Supabase `service_role` key to the app or workflow.
6. Push to main or manually run the Build PixelToonz APK workflow.

Without those two secrets the app still compiles, but sign-in and data requests show a setup message.

## Schema and content
Run `supabase/schema.sql`. Create courses in the Supabase Table Editor, add lessons using their course UUID, and set `published = true`. Add announcements and set them published. Promote a trusted administrator by setting their profile role to `admin` from the SQL Editor.

## Build
GitHub Actions creates a debug APK artifact named `PixelToonz-debug-apk`. Open Actions > latest successful run > Artifacts to download it.

## Production work still required
This is a functional foundation, not a production-complete LMS. Before public release:
- Replace basic session preferences with encrypted token storage and implement token refresh.
- Complete deep-link password reset handling and profile editing.
- Add protected paid-course entitlement checks and payment gateway integration before exposing paid lessons.
- Add admin/instructor screens with server-side role-checked policies.
- Add push notifications, analytics, upload/storage management and device testing.
- Configure a release signing key using GitHub secrets; never commit signing credentials.
