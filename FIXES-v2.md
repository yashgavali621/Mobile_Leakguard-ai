# LeakGuard AI — Phase 1 fix & polish pass

## 1. Why login (and register, and password reset) all failed

Two separate root causes, both of which hit every auth screen at once — which
is why "everything had the same problem".

**A. Stale token poisoning the anonymous auth endpoints.**
`AuthInterceptor` attached `Authorization: Bearer <stored access token>` to
*every* Supabase request. But GoTrue's `/auth/v1/signup`, `/auth/v1/token`
(login + refresh) and `/auth/v1/recover` must be called with the project's
anon/publishable key. Once any expired token was in encrypted prefs, Supabase
answered `401 invalid JWT` and the user could never log back in without
clearing app data.
→ Fixed: the interceptor now keeps an explicit list of anonymous endpoints and
always sends the anon key for them. The user token is still used everywhere
else so PostgREST row-level security keeps working.

**B. Hard crash when `local.properties` was missing.**
`SupabaseConfig.getBaseUrl()` / `getPublishableKey()` threw
`IllegalStateException`. Both are called from `RetrofitClient.build()`, which
runs inside `new AuthRepository(context)` — which runs in
`SplashActivity.onCreate()` and in `AuthViewModel`'s constructor. So the app
died during Activity creation, on launch and on every auth screen.
→ Fixed: the getters degrade to safe placeholders, and `AuthRepository` checks
`SupabaseConfig.isConfigured()` first and returns a readable message instead.

**Also fixed**
- A failed (non-"email not confirmed") login now clears any stale local
  session, so a bad state can't carry into the next attempt.
- Added `lifecycle-viewmodel-savedstate`, required at runtime by
  `ComponentActivity`'s default ViewModel factory.
- Login: Done key on the password field submits, keyboard dismisses on submit,
  autofill hints added, the error banner clears on retry.

> Still required to actually reach a server: copy `local.properties.example` to
> `local.properties` and fill in `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`.
> Without those the app now shows a clear message instead of crashing.

## 2. Colour grade

- Navy is deeper and cooler (`#123253`), and a single saturated teal
  (`#2DBFA8`) carries every accent — focus rings, cursors, progress, links.
  The old palette used three similar blues with no focal colour.
- The background moves off warm beige (`#F7F6F3`) to a cool slate-white
  (`#F4F6F9`). Beige beside navy read as dated, and white cards now actually
  lift off the page.
- Text and divider ramps retuned for contrast (body text `#101828`).

## 3. Layout / composition

Auth screens were a flat left-aligned stack with no hierarchy. Each now has:
a navy gradient banner carrying the logo, and a white form card that overlaps
the banner — brand, then task, then action. Inputs gained leading icons,
buttons are bolder and flat, and errors render as a proper tinted banner rather
than loose red text. Status bar goes navy with light icons on these screens so
the clock doesn't vanish into the gradient.

## 4. Logo

New `ic_logo_mark` — a guard shield whose inner form reads as a sealed currency
droplet with a check inside. Ships as:
- `drawable/ic_logo_mark.xml` — on light backgrounds
- `drawable/ic_logo_mark_light.xml` — on the navy banner / splash
- `drawable/ic_launcher_foreground.xml` + gradient background — adaptive icon,
  artwork kept inside the 66dp safe zone so no launcher mask clips it
- `drawable/ic_launcher_monochrome.xml` — themed-icon support on Android 13+

## 5. Full debug pass (this round)

Went through all 40 Java files and 47 resource files by hand — no Android SDK
in this environment to run a real Gradle build, so this was a full static
audit: every `binding.<id>` checked against its layout's actual IDs, every
`@color/@string/@dimen/@drawable/@style` reference checked against where
it's defined, every Activity checked against the manifest, every endpoint
path checked for the leading-slash mistake that silently drops part of a
Retrofit base URL.

**Found one build-breaking bug:** `values/styles.xml` referenced
`@color/bottom_nav_item_tint` for the bottom navigation's icon/label tint,
but that color was never defined anywhere in the project. An unresolved
resource reference fails resource linking for the *entire* app, not just the
bottom nav — so the project wouldn't compile at all, which is consistent with
every screen "having the same problem." Added
`res/color/bottom_nav_item_tint.xml` as a proper selector: teal
(`accent_teal_dark`) when a tab is selected, muted grey
(`text_tertiary`) otherwise.

**Found one release-build landmine:** `security-crypto` (used by
`SessionManager` for encrypted token storage) pulls in Google Tink, and
without explicit ProGuard keep rules, R8 can strip classes Tink needs
reflectively. That's invisible in a debug build and only surfaces as a crash
on first launch of a signed/minified release build. Added `-keep` rules for
`com.google.crypto.tink.**` and `androidx.security.crypto.**` in
`proguard-rules.pro` so login doesn't work in debug and then break the moment
you build for release.

**Everything else audited clean:** all Activity/Fragment view-binding field
references match their layout's actual IDs; all string/color/dimen/drawable
references resolve; all 6 manifest-declared activities match real classes;
all Retrofit endpoint paths are relative with no leading slash (which would
silently drop the Supabase host's path); all DTOs' `@SerializedName` values
match the Supabase/PostgREST column and GoTrue field names; the RLS-scoped
`user_id=eq.` query params are present on every `profiles` call.

## 6. Second full pass — every remaining file (SQL, config, docs, drawables)

Extended the audit to the files not yet covered: both SQL migrations, both
`.example` config files, `.gitignore`, `README.md`, the Edge Functions
placeholder README, and every drawable.

**No bugs found in:**
- `0001_init_schema.sql` — every table has RLS enabled with matching
  select/insert/update/delete policies on `auth.uid() = user_id`;
  `profiles.user_id` matches what `ProfileRepository` filters on.
- `0002_storage_setup.sql` — `storage.foldername(name)[2]` correctly
  targets the user_id segment of `statements/{user_id}/{file}` (Postgres
  arrays are 1-indexed, segment 1 is `"statements"`, segment 2 is the id).
- `.gitignore`, `.env.example`, `build.gradle`, `settings.gradle`,
  `gradle.properties` — all consistent with each other.

**Fixed (minor, non-build-breaking):**
- `README.md`'s troubleshooting section still described the old crash-on-
  missing-config behavior and didn't mention the "Invalid API key" / "Invalid
  path" cases from this conversation. Rewritten to match current behavior.
- Three fragment layouts (`fragment_subscriptions.xml`, `fragment_alerts.xml`,
  `fragment_ai_insights.xml`) and `fragment_profile.xml` had hardcoded
  English strings (`"Subscriptions"`, `"ACCOUNT"`, `"Currency"`, etc.)
  instead of `@string/` references — inconsistent with the rest of the app
  and unable to be localized. Moved to proper string resources.
- Removed one unused import (`androidx.annotation.NonNull` in
  `OnboardingActivity.java`) — harmless but a lint warning.

**Final state:** all 47 XML files parse cleanly, zero unresolved
`@color`/`@string`/`@dimen`/`@drawable`/`@style` references anywhere in the
project, zero `binding.<id>` calls that don't exist in their layout, zero
`Intent` targets pointing at a class that doesn't exist, zero duplicate
string keys.
