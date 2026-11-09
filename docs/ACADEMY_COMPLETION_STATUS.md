# PixelToonz Academy – completion and release gates

This repository is a native Kotlin Android application. The existing student login/catalogue/profile foundation is retained.

## Existing source features
- Supabase email/password sign-in and registration.
- Encrypted access/refresh token storage using Android Keystore AES-GCM.
- Refresh-token exchange on cold start when the access token is absent.
- Student course catalogue, announcements, enrollments and lesson progress.
- Profile name editing.
- Admin/instructor role-aware entry point and course draft creation.
- Media3 player activity exists in source.

## Added secure payment backend
- `create-payment-order`: validates the authenticated user and published paid course, calculates price from the database, creates a Razorpay order, and stores a pending order server-side.
- `verify-payment`: validates the Razorpay HMAC signature, fetches the payment from Razorpay, checks captured status/order/amount/currency, then marks the payment paid using service-role credentials.
- `202611090001_payment_security.sql`: removes client payment writes and restricts paid-course access to verified payment rows.

## Required owner configuration (cannot be performed from this repository)
Set Supabase Edge Function secrets: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `RAZORPAY_KEY_ID`, and `RAZORPAY_KEY_SECRET`. Deploy both Edge Functions and apply the migration after the base schema. Use Razorpay test mode first.

## Remaining work before production
- Android Razorpay Checkout SDK integration and invoking both Edge Functions from the course UI.
- Complete password recovery deep-link callback and new-password screen; configure the redirect URL in Supabase Auth.
- Firebase Cloud Messaging Android integration, token registration, notification channels and server-side send function; requires Firebase project and app configuration.
- Full staff catalogue management (edit/delete/publish courses, lesson authoring, student/payment reports, announcements) with audited role policies.
- Configure release signing secrets and run the signed release workflow.
- Physical-device QA on supported Android versions and real network/payment test accounts.

## Release signing secrets
- `ANDROID_KEYSTORE_BASE64`: Base64-encoded private JKS/PKCS12 keystore.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never commit keystores, passwords, Razorpay secrets or Supabase service-role keys. CI success is not proof of device testing or payment production readiness.
