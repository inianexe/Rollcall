# Firebase Authentication setup

Project configuration checked on 2026-11-14 for **rollcall-ab5d1**:

- Firebase Google sign-in is enabled; the Android OAuth clients are present in `android/app/google-services.json` for `com.iniexe.rollcall`.
- Email/Password and Email Link (passwordless sign-in) are enabled.
- Firebase Authentication authorized domains include `rollcall-campus-companion.pages.dev`, `rollcall-ab5d1.firebaseapp.com`, and `rollcall-ab5d1.web.app`.
- Google Auth Platform branding uses the app name **RollCall**. The verified project-owner email remains selected as the temporary support contact because the dedicated support account is not yet offered by the selector. The dedicated account has a project-scoped Beta role for OAuth branding management; it must sign in to Google Cloud Console before the support address can be selected.
- No Cloudflare deployment or remote D1 migration was performed.

## Google sign-in on Android

The Android client configuration and Firebase provider are in place. Test first-time Google sign-in and provider linking with a controlled account on a physical device; confirm that linking retains the Firebase UID. Do not enable cloud sync during account-switching tests.

## Email-link sign-in (Android and web)

The Firebase Email Link provider is enabled. Android uses Firebase Hosting's `https://rollcall-ab5d1.firebaseapp.com` action link and handles `/__/auth/links` with an Android App Link. The web flow uses the current origin as its continuation URL. The configured Firebase authorized domains include the production Pages host.

Still verify these flows using a controlled account and an email address explicitly provided for testing:

1. Complete a same-device email-link sign-in on Android and web.
2. Check the cross-device web flow, which asks the user to re-enter the email address.
3. Check Android link dispatch, expired/reused link messaging, and email-link reauthentication before account deletion.
4. Do not put email addresses, passwords, or ID tokens in continuation URLs.

## Web Google configuration

The Firebase web app config and authorized domain are present. The web client uses Firebase `signInWithPopup`; test popup handling, provider conflicts, the trusted-phone challenge, sign-out, session expiry, and stale-data clearing in a controlled account.

## Verification checklist

- Confirm Firebase project ID `rollcall-ab5d1` and Android package `com.iniexe.rollcall`.
- Check provider switches and authorized domains in Console before claiming a method is enabled.
- Keep the Admin SDK private key and Resend key only in Cloudflare secrets; never add them to Android, web config, docs, or Git.
- Exercise provider linking with controlled accounts and record the UID before and after.
- Validate Android App Link delivery with the signed artifact that will be distributed. Debug and release certificates differ.
- Run Android unit tests and web API tests, then separately perform controlled device and staging tests. Local tests do not contact Firebase or production services.

Firebase's supported Android flow and requirements are documented in [Google sign-in](https://firebase.google.com/docs/auth/android/google-signin) and [email-link authentication](https://firebase.google.com/docs/auth/android/email-link-auth); browser completion is in [Firebase Web email-link auth](https://firebase.google.com/docs/auth/web/email-link-auth).
