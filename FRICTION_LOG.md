# Amazon Steady: friction log

Build, Ship, Shape Amazon Developer Hackathon 2026, Fire TV track. Each entry: task, steps, expected vs. actual, severity, workaround, suggestion.

**1. Fire TV documentation link is broken**
- *Task:* open the Fire TV docs from the hackathon Resources page.
- *Steps:* Resources → Fire TV → "Fire TV documentation".
- *Expected:* the Fire TV docs. *Actual:* HTTP 404. The URL is `.../documentation.html%20?sc_category=...`, with an encoded space before the query string.
- *Severity:* Medium. It's the first link a Fire TV developer clicks.
- *Workaround:* deleted `%20` from the URL by hand.
- *Suggestion:* fix the href, and add a link checker to the hackathon page build.

**2. No clear Fire TV emulator path on Windows**
- *Task:* run a Fire TV app locally on Windows.
- *Steps:* read the Fire TV track, Resources and FAQ. The Vega SDK install guide says Windows and WSL are not supported. The FAQ mentions "Android TV Emulator", but no doc names a system image for Fire OS.
- *Expected:* a Fire TV emulator, or a documented "use system image X for Fire OS N".
- *Actual:* we had to infer that Fire OS 8 is Android 11, then pick `system-images;android-30;android-tv;x86` ourselves.
- *Severity:* High for Windows developers.
- *Workaround:* Android TV API 30 AVD.
- *Suggestion:* a "Fire TV on Windows" page with the exact `sdkmanager`/`avdmanager` commands, and an official Fire OS image.

**3. `sdkmanager` rejects JDK 23**
- *Task:* download the TV system image from the command line.
- *Steps:* `sdkmanager --list` with `JAVA_HOME` set to JDK 23.
- *Expected:* a package list. *Actual:* "Java version 17 or higher is required. To override this check set SKIP_JDK_VERSION_CHECK", even though 23 is higher.
- *Severity:* Medium.
- *Workaround:* pointed `JAVA_HOME` at JDK 21.
- *Suggestion:* fix the version parsing, or name the JDK it actually detected in the error.

**4. Android 11 package visibility silently mutes text-to-speech**
- *Task:* give the coach a voice with `TextToSpeech` on the API 30 TV image.
- *Steps:* create a `TextToSpeech` instance; no errors.
- *Expected:* speech. *Actual:* silence. Logcat showed `AppsFilter: interaction: com.steady.tv -> com.google.android.tts BLOCKED`.
- *Severity:* High for accessibility apps, because it fails silently.
- *Workaround:* added `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>` to the manifest.
- *Suggestion:* add this to the Fire TV starter manifest and the TTS section of the Fire TV docs, since many TV apps for accessibility will hit it.

**5. Starter sample is two years behind**
- *Task:* start from `hello-world-fire-tv`.
- *Expected:* current Compose for TV dependencies.
- *Actual:* Compose BOM 2023.08, Kotlin 1.9 with the old `kotlinCompilerExtensionVersion`, AGP 8.2, `tv-foundation` alpha. These don't match current Kotlin 2.x Compose compiler plugin setups.
- *Severity:* Low.
- *Workaround:* wrote the Gradle files from scratch with Kotlin 2.2 and the Compose compiler plugin.
- *Suggestion:* refresh the sample and pin it in CI.

**6. Focus plus vertical scroll hides the header on the TV home screen**
- *Task:* request initial focus on "Start session" in a vertically scrolling home screen.
- *Expected:* the screen stays at the top.
- *Actual:* bring-into-view scrolled about 200px, hiding the greeting.
- *Severity:* Low.
- *Workaround:* `scrollTo(0)` after requesting focus.
- *Suggestion:* a Fire TV sample showing a scrolling home screen with initial focus.

**7. Phone camera needs HTTPS, which local TV companion apps don't have**
- *Task:* use a phone on the same Wi-Fi as a camera for the TV.
- *Expected:* open the LAN URL from the QR code and use the camera. *Actual:* browsers block `getUserMedia` on plain-HTTP LAN addresses.
- *Severity:* Medium for any phone-plus-TV experience.
- *Workaround:* a Cloudflare quick tunnel (`npm run tunnel`).
- *Suggestion:* a Fire TV "second screen" pairing sample showing secure phone ↔ TV pairing.

**8. Compose for TV text fields trap the D-pad**
- *Task:* a first-run setup form (name, goal, family contact) navigated with the Fire TV remote.
- *Steps:* focus a `TextField`, then press D-pad Down.
- *Expected:* focus moves to the next field. *Actual:* the text field consumes Up/Down, so the remote can't leave it.
- *Severity:* High for any TV app with a form.
- *Workaround:* `onPreviewKeyEvent` that calls `focusManager.moveFocus(FocusDirection.Down/Up)`, plus `ImeAction.Next`.
- *Suggestion:* a TV-aware text field in `androidx.tv:tv-material`, or a Fire TV form sample.

**9. Hackathon rules pages block non-browser clients**
- *Task:* have an AI coding agent read the rules and FAQ.
- *Expected:* readable pages. *Actual:* HTTP 403 for common fetchers, and only some paths load.
- *Severity:* Low.
- *Workaround:* fetched with a browser user agent.
- *Suggestion:* allow read access, since AI tools are part of how developers now read requirements.
