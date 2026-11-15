# OASIS INFOBYTE — Android App Development

Public internship repository for **Vikas Daddi**. Android work is organized in the task-card format requested by the internship guide.

## Project

### Android — Task 3: Calculator

Folder: [`Android-Task3-Calculator/`](Android-Task3-Calculator/)

Native Android calculator written in Java and XML. It includes arithmetic with precedence, colorful light and dark themes, calculation history, memory controls, extra math operations, a custom launcher icon, and a self-hosted signed-APK update flow using GitHub Releases.

- [Project README and Android Studio setup](Android-Task3-Calculator/README.md)
- [Calculator UI preview](Android-Task3-Calculator/ui-preview.svg)
- [Interactive browser demo](https://vikas-daddi.github.io/OIBSIP/)
- [Latest Android APK release](https://github.com/Vikas-daddi/OIBSIP/releases/latest)

The browser demo is a live companion demo; the APK is the native Android application. Android requires the device owner to approve APK installation.

## Automatic APK update setup

The Android app checks the latest stable release in this repository when opened (at most once every six hours). When it finds a newer release, it offers to download the signed APK. Android then shows its package installer for the user to confirm. Android does not permit this sideloaded app to silently install its own update.

GitHub Actions builds and publishes a new signed APK when changes are pushed to `main` under `Android-Task3-Calculator/`. For this to work, configure the repository secrets listed in the project README first. The same upload key must sign every release; losing or replacing that key prevents Android from installing updates over the existing app.

## Internship submission reminders

- Repository name: `OIBSIP` (public).
- Keep each project in its task-named folder with code, README, screenshots, and output files.
- For the Android task, record a demo video with a 2-second opening card showing your full name, assigned track, and task title.
- Post the demo to LinkedIn, tag Oasis Infobyte, and include `#oasisinfobyte`; leave substantive comments on two cohort demo posts.
- Submit through the official task form when it opens.

See the task list supplied for the internship for the complete checklist and deadlines.
