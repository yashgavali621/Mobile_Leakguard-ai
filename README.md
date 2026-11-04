import pypandoc, os

readme = r'''<div align="center">

# 🛡️ LeakGuard AI

### Know Where Your Money Leaks.

A privacy-conscious Android application foundation for tracking financial activity and building smarter awareness of recurring payments, subscriptions, and potential money leaks.

<br/>

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Java-orange?logo=openjdk&logoColor=white)](https://www.java.com/)
[![UI](https://img.shields.io/badge/UI-XML-4285F4)](https://developer.android.com/develop/ui/views/layout/declaring-layout)
[![Backend](https://img.shields.io/badge/Backend-Supabase-3ECF8E?logo=supabase&logoColor=white)](https://supabase.com/)
[![Build](https://img.shields.io/badge/Build-Gradle_8.7-02303A?logo=gradle&logoColor=white)](https://gradle.org/)

</div>

---

## 📖 About the Project

**LeakGuard AI** is an Android project focused on helping users gain better visibility into their financial activity. The product direction is to identify recurring payments, manage subscriptions, highlight price changes, and surface potentially unusual transactions from supported data sources.

The application is being developed in phases. The current repository contains the Android foundation, onboarding and authentication flow, profile integration, navigation, and Supabase database setup. Financial data ingestion, automated detection, and AI-powered insights are planned future development phases and are **not represented as completed features** in the current build.

> **Current implementation:** Android application foundation, Supabase email authentication, profile handling, five-section navigation, and database migrations.

## ✨ Current Implementation

- **Onboarding flow** — introductory screens for first-time users.
- **Authentication** — Supabase email registration, login, session refresh, logout, and password recovery.
- **Encrypted session storage** — authentication session data stored using AndroidX encrypted preferences.
- **Profile integration** — create, retrieve, and update user profile data through Supabase.
- **Five-section navigation** — Dashboard, Subscriptions, Alerts, AI Insights, and Profile.
- **Honest empty states** — screens without a connected data pipeline display empty states rather than fabricated financial data.
- **Database foundation** — SQL migrations for the initial schema, indexes, triggers, storage setup, and Row Level Security (RLS) policies.
- **Layered code structure** — separates presentation, domain, data, and repository responsibilities.

## 🧰 Technology Stack

| Area | Technology |
|---|---|
| Platform | Android |
| Language | Java |
| User interface | XML layouts, Material Components |
| Architecture | MVVM-inspired layered architecture |
| UI state | ViewModel, LiveData, View Binding |
| Networking | Retrofit, OkHttp |
| JSON parsing | Gson |
| Backend | Supabase |
| Authentication | Supabase Auth REST API |
| Database API | Supabase PostgREST |
| Session security | AndroidX Security / EncryptedSharedPreferences |
| Build system | Gradle |
| Database setup | Supabase SQL migrations |

## 🏗️ Architecture

The project uses a layered structure to keep UI code, application logic, and remote data access organized.

```text
com.leakguard.ai
│
├── data/
│   ├── local/              # Local session management
│   ├── remote/
│   │   ├── dto/            # API request and response models
│   │   └── supabase/       # Supabase APIs and networking
│   └── repository/         # Authentication and profile repositories
│
├── domain/
│   └── model/              # Core application models
│
├── presentation/
│   ├── splash/
│   ├── onboarding/
│   ├── auth/
│   ├── main/
│   ├── dashboard/
│   ├── subscriptions/
│   ├── alerts/
│   ├── insights/
│   ├── profile/
│   └── common/
│
└── utils/                  # Shared utilities and helpers
```

**Typical data flow**

```text
Activity / Fragment
        ↓
     ViewModel
        ↓
    Repository
        ↓
 Retrofit / Supabase API
        ↓
   Supabase Backend
```

Presentation components should access remote data through repositories rather than calling API interfaces directly.

## 🚀 Getting Started

### 1. Prerequisites

Install the following tools:

- [Android Studio](https://developer.android.com/studio) — recent stable version recommended
- **JDK 17** — the JDK bundled with Android Studio is suitable
- **Android SDK Platform 34** and Android SDK Build-Tools
- [Git](https://git-scm.com/downloads)
- A [Supabase](https://supabase.com/) account and project

**Project configuration:** Android Gradle Plugin 8.5.2, Gradle 8.7, `compileSdk 34`, `targetSdk 34`, and `minSdk 26` (Android 8.0).

### 2. Clone the repository

```bash
git clone https://github.com/yashgavali621/Mobile_Leakguard-ai.git
```

Open the cloned project root — the folder containing `settings.gradle` — in Android Studio.

### 3. Create and configure Supabase

1. Sign in to [Supabase](https://supabase.com/) and create a project.
2. Wait for the project to finish provisioning.
3. Open **Project Settings → API** (the exact menu label may vary).
4. Copy the **Project URL** and the **anon / publishable key**.

### 4. Set up the database

In the Supabase dashboard, open **SQL Editor** and run the migration scripts in order:

1. `supabase/migrations/0001_init_schema.sql`
2. `supabase/migrations/0002_storage_setup.sql`
3. `supabase/migrations/0003_email_sms_integration.sql` — run after the first two when preparing the email/SMS integration database objects.

Run each script once and verify that it completes without errors. Review the SQL and RLS policies in your own Supabase project before using real data.

### 5. Configure local project properties

In the project root, make a copy of:

```text
local.properties.example
```

Name the copy:

```text
local.properties
```

Add your Android SDK path and your Supabase project values. Example for Windows:

```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

Replace the sample values with your own. Android Studio may configure `sdk.dir` automatically; if so, retain the SDK path it supplies.

The optional integration values can remain placeholders until those integrations are implemented and configured:

```properties
GOOGLE_WEB_CLIENT_ID=
GOOGLE_ANDROID_CLIENT_ID=
BACKEND_BASE_URL=
```

> **Important:** `local.properties` is machine-specific and must not be committed to Git. Use only the Supabase anon / publishable key in the Android client. Never place a Supabase `service_role` key, database password, OAuth client secret, or other server secret in the Android app or a public repository.

### 6. Sync and run the app

1. Open the project root in Android Studio.
2. Select **File → Sync Project with Gradle Files**.
3. Allow Android Studio to download the required Gradle and Android dependencies.
4. Start an Android emulator (API 26 or later) or connect an Android device with USB debugging enabled.
5. Select the `app` run configuration.
6. Click **Run ▶**.

The expected first-run navigation is:

```text
Splash Screen
     ↓
Onboarding
     ↓
Login / Register
     ↓
Dashboard
```

Create a test account and check the Supabase **Authentication** section and **Table Editor** to verify that the account and profile record were created.

### 7. Build from the terminal (optional)

From the project root, a local Gradle installation can build the debug APK:

**Windows PowerShell**

```powershell
gradle assembleDebug
```

If the Gradle wrapper scripts and wrapper JAR are available in your local checkout, you can use:

```powershell
.\gradlew.bat assembleDebug
```

The repository currently contains Gradle wrapper configuration but may not contain the wrapper scripts/JAR. If the wrapper command reports that `GradleWrapperMain` cannot be found, use Android Studio's Gradle sync or install Gradle 8.7 and generate the wrapper:

```powershell
gradle wrapper --gradle-version 8.7
```

The debug APK is normally created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 🔐 Security & Privacy

Security is an important part of the project's design.

- Keep local configuration in `local.properties` and secret environment files that are excluded by `.gitignore`.
- Use the Supabase anon / publishable key in the Android client.
- Never expose the Supabase `service_role` key in a mobile application or public repository; it can bypass Row Level Security.
- Keep Row Level Security enabled and review policies before deploying.
- Do not log access tokens, passwords, credentials, or sensitive financial information.
- Request sensitive Android permissions only when the related feature is implemented, necessary, and clearly explained to the user.
- If a real key or password has ever been committed or exposed, revoke or rotate it in the provider dashboard. Removing it from the latest commit does not make the exposed secret safe again.

## 🗺️ Development Roadmap

The following items describe the planned direction of the project. Roadmap items are not claims that the features are already available.

| Phase | Scope | Status |
|---|---|---|
| 1 | Android foundation, design system, navigation, and authentication | Foundation implemented |
| 2 | Database foundation and profile improvements | Database foundation included |
| 3 | Bank statement upload and transaction import | Planned |
| 4 | Gmail integration with secure server-side OAuth | Planned |
| 5 | Notification access and permission centre | Planned |
| 6 | Policy-compliant SMS integration | Planned |
| 7 | Financial event processing and local cache | Planned |
| 8 | Recurring payment, subscription, price-change, and duplicate detection | Planned |
| 9 | Alerts and notification workflows | Planned |
| 10 | Gemini-powered AI insights through a server-side function | Planned |
| 11 | Analytics, budgets, savings, goals, financial health, and reports | Planned |
| 12 | Security hardening, testing, and release preparation | Planned |

## 🧪 Verification Checklist

When setting up the project on a new machine, verify:

- [ ] `local.properties` contains the correct SDK path and Supabase values.
- [ ] The required Supabase migrations run without errors.
- [ ] Android Studio completes Gradle sync.
- [ ] The app launches on an API 26+ emulator or device.
- [ ] Registration creates a Supabase Auth user.
- [ ] A profile record is created for the new user.
- [ ] Login and password recovery work.
- [ ] Logout clears the local session.
- [ ] A returning signed-in user can restore their session.
- [ ] Navigation between the five main sections works.
- [ ] Invalid credentials and network failures show understandable errors.

## 🛠️ Troubleshooting

| Issue | What to check |
|---|---|
| Supabase connection/configuration message | Confirm `local.properties` exists and contains the correct project URL and publishable key. Sync Gradle again. |
| Invalid API key | Use the anon / publishable key from the same Supabase project as the URL. Do not use the service-role key. |
| Authentication request fails | Check internet connectivity, Supabase Auth settings, and local configuration values. |
| Database request fails | Confirm the required migrations ran successfully and review the relevant RLS policies. |
| Gradle sync fails | Verify JDK 17, SDK Platform 34, internet connectivity, and the Gradle configuration. |
| Gradle wrapper JAR is missing | Use Android Studio's Gradle sync or generate the wrapper using an installed Gradle 8.7 distribution. |

For additional project-specific notes, see:

- [`FIX-DATA-NOT-SHOWING.md`](FIX-DATA-NOT-SHOWING.md)
- [`FIXES-v2.md`](FIXES-v2.md)
- [`INTEGRATION-NOTES.md`](INTEGRATION-NOTES.md)

## 🤝 Contributing

Contributions, suggestions, and issue reports are welcome.

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Make your changes and test them.
4. Commit your changes: `git commit -m "Add: describe your change"`.
5. Push your branch: `git push origin feature/your-feature`.
6. Open a Pull Request with a clear description of the changes.

Please do not include API keys, passwords, access tokens, personal information, or local configuration files in commits.

## 📄 License

No license has currently been specified. Unless a license is added to this repository, the default copyright rules apply; reuse, redistribution, and modification are not automatically granted.

---

<div align="center">

**LeakGuard AI — Build better financial awareness, one insight at a time.**

Made with ☕ and a focus on privacy.

</div>
'''

out = "/mnt/data/README.md"
pypandoc.convert_text(readme, "md", format="md", outputfile=out, extra_args=["--standalone"])
print(f"Created {out} ({os.path.getsize(out):,} bytes)")
