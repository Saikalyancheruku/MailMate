# MailMate — Android-only Gmail SMTP app

MailMate is a native Android application for sending individual job-application emails through Gmail SMTP. It does not require a hosted backend or a running computer/server after installation.

## Features

- Gmail SMTP defaults: `smtp.gmail.com`, port `587`, STARTTLS.
- SMTP connection test.
- SMTP password encrypted with AES-GCM using a key stored in Android Keystore.
- Email templates stored on the device, with `{{role}}` and `{{your_name}}` placeholders.
- Import multiple PDF/DOC/DOCX resume versions from phone storage; app copies them into its private app storage.
- Parse multiple recipients separated by commas, semicolons, spaces, tabs, or new lines.
- Send a separate email with the chosen resume attached to each recipient.
- Local send history for the latest 250 recipient-level results.
- No account, template, resume, or send-history data is uploaded to a MailMate server. SMTP traffic goes directly from the phone to the configured SMTP server.

## Gmail setup

1. Enable 2-Step Verification on the Google Account that will send mail.
2. Create a Google **App Password** for this app/account. Google may not offer App Passwords for some account types or security configurations (for example, some organization-managed accounts or Advanced Protection).
3. In MailMate, open **SMTP** and enter:
   - Host: `smtp.gmail.com`
   - Port: `587`
   - Gmail address: your complete email address
   - Google App Password: the app password (not your normal account password)
4. Tap **Save settings**, then **Test SMTP connection**.
5. Add templates and resume versions, then use **Send**. Send a test email to yourself first.

Google can enforce sending limits and may temporarily restrict sending. MailMate does not bypass those limits.

## Build/install

### Android Studio (local build)

1. Install Android Studio on a computer and the Android SDK Platform 35.
2. Open this folder as an existing Android Studio project.
3. Allow Gradle sync to complete, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Install the generated debug APK on your Android phone. You may need to allow installation from the source you used to open the APK.

A computer is needed only if you choose to build locally. Once installed, the app runs on the phone and needs no server/hosting. The current environment used to prepare this source did not have the Android SDK or Gradle installed, so an APK could not be compiled and verified here.

### Build without setting up a local PC

A GitHub Actions workflow is included at `.github/workflows/android.yml`. Put this project in a GitHub repository, then open **Actions → Build MailMate Android APK → Run workflow**. Download the `MailMate-debug-apk` artifact and install the APK on your phone. This uses GitHub only as a one-time build service; the installed app does not depend on GitHub or any hosted backend at runtime. Do not put SMTP credentials or resumes in the repository.

## Local data and security notes

- SMTP app password is encrypted using Android Keystore and AES-GCM.
- Templates, resume copies, and history are stored in app-private local storage.
- Android app backup is disabled in the manifest.
- Uninstalling the app deletes its local data; export/backup features are not included in this first version.
- Use a phone screen lock. Do not share the installed app with anyone who should not send from your account.
- This is a starter implementation, not independently security-audited. SMTP settings are for Gmail/Google Workspace using port 587 STARTTLS; other providers and port 465 implicit TLS are not configured in this version.

## Project structure

- `app/src/main/java/com/mailmate/app/MainActivity.kt` — Compose UI and workflows
- `LocalStore.kt` — local data and Android Keystore encryption
- `SmtpSender.kt` — SMTP connection, MIME message, attachment
- `Models.kt` — local data models
