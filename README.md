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


## Security and extended-feature status (November 2026)

### Implemented in source
- Android Keystore AES-256-GCM encrypted access/refresh-token storage.
- Supabase refresh-token exchange on app restore when access token is missing.
- Student profile name editing.
- Role-aware admin/instructor dashboard entry and draft course creation.
- AndroidX Media3 in-app player for HTTPS lesson URLs.
- RLS protections for student-owned records, staff course authoring, and paid-course entitlements.

### Integrations requiring owner setup before they are live
- **Supabase:** add repository Actions secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY`, then run the latest `supabase/schema.sql` in the SQL Editor. Assign staff roles manually from the trusted Supabase SQL console only.
- **Paid courses:** the database intentionally does not let the Android client mark an order as paid. A trusted server-side payment-order and signed-webhook integration must be deployed and configured for your selected provider before accepting money. Do not ship paid courses until this is complete.
- **Push notifications:** create a Firebase project, register Android package `com.pixeltoonz.app`, download `google-services.json` into `app/`, and configure Firebase Cloud Messaging. Push delivery is not active until this is configured and an FCM service/token registration flow is added.
- **Password reset:** configure Supabase Auth redirect URLs for the app's reset deep link and complete the app-side recovery callback before production.
- **Release signing:** create a private Android upload keystore locally. Store its Base64 contents and passwords as GitHub Actions secrets; never commit the keystore or passwords.
- **Device QA:** install the generated APK on physical Android devices and verify auth, refresh, video playback, navigation, network loss, and role-specific RLS behavior. CI compilation alone is not device testing.

### Important security note
The Supabase anon/publishable key is not a secret; authorization must be enforced by RLS. Never put a Supabase service-role key, payment secret, webhook secret, or Firebase service-account credential in the Android app. Paid entitlements must be written only by a trusted server after payment signature verification.
