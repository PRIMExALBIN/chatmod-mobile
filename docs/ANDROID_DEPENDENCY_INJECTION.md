# Android Dependency Injection

ChatMod Mobile uses Koin for Android dependency injection. Koin is a lightweight Kotlin DI framework published under the Apache 2.0 license, so it fits the free/open-source production posture without adding paid infrastructure or hosted services.

Run the source gate before Android release work:

```powershell
npm run android:di:check
```

## Current Graph

`ChatModCoreModule` owns the app-level graph:

- Application `CoroutineScope`.
- Room database and DAOs.
- Demo or HTTP `ChatModApiClient`, selected from `BuildConfig.CHATMOD_USE_DEMO_API`.
- `ChatModRepository`.
- `ChatModSessionManager`.
- `SettingsStore`.
- `PlayBillingManager` for Google Play product, purchase, restore, and acknowledgement flow.
- Local privacy wipe store.
- Dashboard log store.
- Syncing command/timer store.
- Pending cloud sync queue.
- Syncing bot log sink.
- First-party crash reporter.
- Opt-in usage analytics reporter.

`ChatModApplication` starts Koin once, then exposes a narrow compatibility bridge for services and workers that still need the application surface.

`MainActivity` consumes the graph directly with Koin `inject()` properties so the dashboard no longer reaches into `ChatModApplication` for dependencies.

## Migration Rule

New Android features should request dependencies from Koin instead of constructing repositories, API clients, settings stores, or DAOs directly in UI and service classes. Keep direct constructors only in focused unit tests or tiny value-only classes.

## Manual QA Still Required

- Build the Android app with JDK 17+ and Android SDK API 35.
- Open the dashboard and verify the same account/session state appears after process restart.
- Start and stop the foreground service after Koin startup.
- Trigger the Quick Settings tile and WorkManager pending sync drain path.
- Verify demo API mode still works for CI and local previews.

## Source References

- Koin setup docs: https://insert-koin.io/docs/setup/koin/
- Koin Android quickstart: https://insert-koin.io/docs/quickstart/android/
