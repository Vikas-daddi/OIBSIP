# Oasis Calculator

An Android calculator built with Java and XML for the Oasis Infobyte Android App Development track (Task 3). The app uses a custom Oasis launcher icon and checks public GitHub Releases for signed APK updates; it does not use Google Play.

## Features

- Responsive four-column keypad built with Android `GridLayout`.
- Addition, subtraction, multiplication, and division with normal operator precedence.
- Decimal input, sign toggle, percentage, clear, and backspace controls.
- Parentheses and grouped expressions with normal operator precedence.
- Expandable scientific panel with sine, cosine, tangent (in degrees), square root, square, natural log, and log base ten.
- Square root and square operations for the current number.
- Calculation history with the latest 30 results saved across app launches; tap an item to reuse its answer.
- Persistent calculator memory: `MC`, `MR`, `M+`, and `M−`.
- Copy the displayed answer using the **Copy** button or by long-pressing the result.
- Colorful light and dark themes with a top-bar switch; long-press the switch to return to the phone's system theme.
- Live expression preview, high-contrast display, and a custom Oasis calculator logo inspired by the reference layout.
- Division-by-zero and malformed-expression handling with a visible `Error` state.
- Calculator state retained through screen recreation, such as device rotation.
- Accessible labels for calculator controls and the result display.
- Best-effort update check against the public `Vikas-daddi/OIBSIP` GitHub Releases feed.
- Update download via Android `DownloadManager`, followed by Android's package installer for user approval.
- No passwords, API keys, or GitHub authentication token are embedded in the app.

See the [light theme mockup](ui-preview.svg) and [dark theme mockup](ui-preview-dark.svg), or open the [preview page](ui-preview.html) to switch between them.

## Requirements

- Android Studio (Giraffe or newer recommended).
- Android SDK Platform 36 and Java 17 (Android Studio's bundled JDK is suitable).
- Android emulator or a USB-connected Android device with developer options enabled.

## Open and run

1. Open Android Studio and choose **Open**.
2. Select this project folder (`Android-Task3-Calculator`), not just the `app` folder.
3. Allow Gradle sync to finish. If prompted, install Android SDK Platform 36 through SDK Manager.
4. Select an emulator or connected device, then click **Run**.

The app's application ID is `com.oasisinfobyte.calculator`. No credentials or API keys are needed.

## How to use

Tap digits and operators to build an expression, then tap `=` to evaluate it. The calculator follows normal precedence (multiplication and division before addition and subtraction). `AC` clears the expression, the backspace key removes the last character, `()` adds grouping parentheses, and `±` toggles the current number's sign. Open **Scientific** for trig and logarithm functions plus persistent memory controls. The history icon reopens recent answers. Tap **Copy** or long-press the result to copy it.

Tap the moon or sun button in the top bar to switch themes. Long-press it to follow the phone's system theme again.

Examples:

- `8 + 2 × 3 =` displays `14`.
- `9 ÷ 0 =` displays `Error` and a clear explanation above the display.
- `12.5 − 2.5 =` displays `10`.

## Updating the app without Google Play

The app checks GitHub Releases when it starts, no more than once every six hours. When it finds a newer `vMAJOR.MINOR.PATCH` release containing an asset named `oasis-calculator.apk`, it asks whether to download the update. The download uses Android's Download Manager, then Android opens the system installer. Android requires the user to allow installs from this app and confirm the update; this app cannot silently replace itself.

The repository workflow publishes a signed APK when calculator code is pushed to `main`. Before releases can succeed, configure these GitHub Actions repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Create a private release keystore once and keep a secure backup outside this repository. For example, run this in PowerShell and enter your own strong passwords when prompted:

```powershell
keytool -genkeypair -v -keystore oasis-calculator-upload.jks -alias oasis-upload -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\oasis-calculator-upload.jks)))
```

Store the printed base64 value as `ANDROID_KEYSTORE_BASE64`, and the passwords and alias as their matching secrets under **GitHub → OIBSIP → Settings → Secrets and variables → Actions**. Do not commit the keystore or its passwords. The same key must sign every release or Android will refuse the update. If a workflow ran before the secrets were configured, run **Build and publish Android updates** again from the Actions tab. Once secrets are set, pushes to the app folder automatically publish the next signed release.

Users should install the first production APK from the repository's Releases page. An APK installed directly with Android Studio uses a debug signing key and cannot be updated by the release-signed APK. The first release is available from [GitHub Releases](https://github.com/Vikas-daddi/OIBSIP/releases).

For update troubleshooting, check that the device has internet access, the newest GitHub Release includes `oasis-calculator.apk`, the APK was signed with the same key, and Android's **Install unknown apps** permission is enabled for Oasis Calculator.

## Project structure

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/oasisinfobyte/calculator/
│   ├── MainActivity.java
│   └── CalculatorExpression.java
└── res/
    ├── drawable/key_background.xml
    ├── layout/activity_main.xml
    └── values/
```

## Internship submission checklist

- Put this project folder in the public GitHub repository named exactly `OIBSIP`.
- Keep this task in the guide's folder naming format: `Android-Task3-Calculator`.
- Add screenshots of the running app to this folder before submission.
- The repository root also includes an interactive browser companion demo at [the live demo site](https://vikas-daddi.github.io/OIBSIP/); it is not a substitute for demonstrating the native Android app.
- Record a working demo video with a 2-second opening card showing your full name, assigned track, and task title. The guide also asks for a LinkedIn post tagging Oasis Infobyte with `#oasisinfobyte`, substantive comments on two cohort demo posts, and submission through the official task form when it opens.

## Implementation notes

Expression parsing is implemented locally using a small recursive-descent parser, avoiding dynamic code evaluation. Arithmetic uses Java `double`; the display trims trailing zeroes. This keeps the app dependency-free and suitable for the task's basic arithmetic scope.
