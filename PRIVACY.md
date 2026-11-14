# Rollcall Privacy Policy

**Effective date:** 14 November 2026
**Maintainer:** the independent developer behind [@inianexe](https://github.com/inianexe)
**Contact:** [GitHub profile](https://github.com/inianexe). Please do not post passwords, attendance records, or other private student information in public issues.

Rollcall is an independent student project. It is not affiliated with MyCamu or an educational institution. This policy describes the Android app and optional Rollcall web account. Cloud account features become available only after the Firebase and Cloudflare services are configured and deployed.

## What Rollcall accesses

Rollcall opens the MyCamu portal in an Android WebView. You enter your sign-in details on that portal. Rollcall does not send your password to a Rollcall server. The WebView keeps the portal's sign-in session on your device, and Rollcall uses that session to request attendance information from MyCamu over HTTPS.

Rollcall reads the information needed for its dashboard and calculations:

- course names and codes, attended and total periods, and class attendance history;
- timetable entries, including course, time, staff, and location when supplied by the portal;
- leave type, status, dates, and period count when supplied by the portal;
- student name, institute, and roll/admission identifier when supplied by the portal; and
- portal account identifiers needed to make the requests for the signed-in account.

The app also saves your theme, attendance target, reminder setting, and reminder schedule on the device. Optional class reminders are created locally by Android. If you create a Rollcall account, the app can send a snapshot of the data listed above, including available KCT grades and exam details, to the Rollcall cloud service so you can view it on the web. This is optional and separate from MyCamu sign-in.

## Data inventory

| Data | How it is used | Sent to | Stored by Rollcall | Linked to you | Tracking | Required or optional |
|---|---|---|---|---|---|---|
| MyCamu sign-in session and cookies | Keep your portal session and authenticate requests | MyCamu through its WebView/HTTPS endpoints | In the Android WebView's private app data | Yes | No | Needed to connect; you may disconnect at any time |
| Portal account identifiers | Select the signed-in student's academic record | MyCamu with authenticated requests | Android app-private preferences | Yes | No | Needed to sync |
| Attendance, course, class-history, timetable, leave, and profile details | Show your dashboard, history, reminders, and attendance estimates | Retrieved from MyCamu; not sent to a Rollcall-operated server | Android app-private preferences | Yes | No | Needed for the related feature; portal fields may be unavailable |
| Theme, target, and reminder preferences | Remember your choices and schedule local reminders | Nowhere | Android app-private preferences and Android's local alarm/notification services | Not sent to Rollcall | No | Optional |
| Rollcall account email and authentication data | Sign in to the optional web mirror | Firebase Authentication (Google, email link, or existing password) | Firebase account service | Yes | No | Optional; web access requires a verified email and trusted-phone approval |
| Synced attendance, schedule, class marks, leave/OD, profile, KCT grades and exams | Show a read-only web dashboard | Cloudflare Worker/D1 over HTTPS | D1 snapshot storage; protected by the provider's at-rest encryption | Yes | No | Optional; only after linking the trusted app |
| Web sign-in challenges, hashed session, and short-lived phone-replacement code hashes | Approve browser sign-in and confirm a replacement phone | Cloudflare Worker/D1, Firebase Cloud Messaging, and Resend | D1 stores only code hashes and expiry/attempt metadata; browser sign-in codes are encrypted and sent only to the linked app; replacement codes are sent to the verified account email | Yes | No | Optional; only used for Rollcall web accounts |

Rollcall does not include advertising, analytics, or crash-reporting. Its optional account uses Firebase Authentication/Cloud Messaging, Cloudflare Workers/D1, and Resend for transactional email; those providers process data under their own terms. Web one-time codes go to the linked Rollcall app. A replacement-phone confirmation code goes to the verified account email and expires after 10 minutes. The flow does not collect a phone number or send SMS. The app does not request contacts, location, photos, or microphone access. MyCamu's pages may set their own cookies or load their own service providers; those parties are governed by their own privacy notices.

## Where data is stored and how to delete it

Attendance snapshots, portal account context, profile fields, and app preferences are stored in Android app-private storage on your device. When you opt into a Rollcall account, the selected snapshot is sent over HTTPS and stored in Cloudflare D1. Transport is encrypted; the cloud copy is not end-to-end encrypted, so the service operators and infrastructure can process it. Firebase stores account credentials; Rollcall does not receive or store your plaintext password. Keep your device protected with a screen lock and install Android updates. Android backup is disabled for Rollcall.

To clear phone data, open **About → Disconnect and clear saved data**. This clears the saved attendance, timetable, history, leave and profile data, the WebView's local portal session and website storage, and scheduled reminders/background sync. It does not delete your MyCamu account or cloud Rollcall account. To delete the cloud account and snapshot, use **About → Rollcall Web Account → Delete Rollcall cloud account**; Rollcall asks you to reauthenticate with a linked Google account or one-time email link, then deletes the Firebase identity and Rollcall cloud rows. Local phone data remains. Signing out on the website ends that browser session but retains the cloud snapshot. Signing out in the app does not delete the snapshot. Uninstalling Rollcall removes its app-private data but does not delete the cloud account.

## MyCamu and other services

MyCamu and KCT are external services. Rollcall reads portal data in the Android app using the signed-in portal session. The website does not sign in to either portal or request portal passwords. KCT grades and exam details sync when the user opens the KCT portal in the app; the MyCamu background refresh may run through Android WorkManager while its session remains valid. The website only displays the snapshot provided by the app. These institutions control their services and records; contact them for requests about data held on their systems.

If you open a GitHub link from About, your browser connects to GitHub under GitHub's own privacy terms.

## Security and permissions

Rollcall uses HTTPS for portal and cloud requests, rejects invalid TLS certificates, blocks mixed-content and cleartext app traffic, disables Android app backup, and keeps the local copy in app-private storage. Web sign-in requires a verified Firebase email, a short-lived code retrieved inside the trusted app, and an HttpOnly secure browser session cookie. Push notifications do not contain the code. Replacing a trusted phone requires a separate short-lived code sent to the verified account email; it is hashed at rest, expires after 10 minutes, and is limited to five attempts. Linking the replacement phone signs out existing web sessions. No security measure is perfect; a compromised, rooted, or unlocked device can expose information stored on it.

The app requests internet access, notification permission for optional reminders, and permission to receive the system boot event so Android can restore reminder schedules. It does not use an overlay or accessibility service.

## Children and student accounts

Rollcall is a student tool and does not include an age-check or parental-consent flow. If you are under 18, use it only when your parent or guardian and institution allow you to use the connected portal and app. The maintainer should review age and consent requirements before offering the app broadly to minors.

## Your choices and rights

You can disconnect and clear the copy held by Rollcall at any time using the in-app control above. Rights that apply to you depend on your location and the data controller. For information or requests about data stored by Rollcall, use the contact link above. For portal account data held by MyCamu or your institution, contact that service or institution.

## Changes

This policy may change when Rollcall's features or data practices change. The current version will be posted in the [Rollcall repository](https://github.com/inianexe/Rollcall/blob/main/PRIVACY.md) with a new effective date.

This is a plain-language project policy, not legal advice. The maintainer should have it reviewed against applicable Indian privacy law and any other laws where Rollcall is distributed before a broad public launch.
