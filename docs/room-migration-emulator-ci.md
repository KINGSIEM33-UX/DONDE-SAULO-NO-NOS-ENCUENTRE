# Room migration emulator CI

`.github/workflows/room-migration-emulator-ci.yml` is intentionally scoped to
the Android instrumented migration suite. Its emulator step runs only:

```text
./gradlew --no-daemon --stacktrace --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.data.AppDatabaseMigrationTest \
  :app:connectedDebugAndroidTest
```

The runner therefore executes the three `@Test` methods in
`com.example.data.AppDatabaseMigrationTest`:

1. `roomOpensAndValidatesSchemaFromVersion7To8PreservingLegacyRows`
2. `roomOpensAndValidatesSchemaFromVersion6Through7To8`
3. `concurrentFavoriteInsertsAreSerializedAndLegacyDuplicatesRemain`

The workflow does **not** run `ExampleInstrumentedTest`, any other
instrumented test, or `:app:testDebugUnitTest`. Unit tests remain the
responsibility of `.github/workflows/android-ci.yml`, which runs
`:app:assembleDebug :app:testDebugUnitTest` inside the Firebase emulator
wrapper. The Room workflow keeps Gradle's plain console output and stacktraces
in the Actions log; it does not ignore failures or turn test errors into
successful runs.
