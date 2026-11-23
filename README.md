# QR Master Web — Proof of Concept

The QR Master website, built with **Kotlin + Compose Multiplatform Web (WebAssembly)**.
No hand-written HTML/CSS/JavaScript UI and no WebView — the entire interface is
Kotlin/Compose, rendered in the browser. It uses the **existing** QR Master
Firebase project (`link-to-qr-25307`) — the same accounts, wallet and data as
the Android app. Nothing in the Android app, Admin Panel, or production
Firebase data/rules is changed by this project.

**Live (after deploy):** https://ayansikligar098-commits.github.io/qr-master-web/

## What this POC includes

- Dashboard with sidebar + top bar, matching the QR Master reference design
  (light/dark theme, responsive: desktop sidebar / mobile drawer + bottom nav)
- Working navigation (Dashboard, All Tools, QR Generator, Wallet, Premium, Login)
- **Firebase email/password Login & Register** (REST API, existing project)
- **Live wallet balance** read from Firestore (read-only)
- **Real QR Code Generator** — pure-Kotlin QR encoder (Nayuki port, MIT),
  verified by decoding generated codes with an independent decoder;
  PNG download (pure-Kotlin PNG encoder)
- Remaining tools show a placeholder page (full website phase)

## Tech

- Kotlin 2.2.20, Compose Multiplatform 1.9.0, `wasmJs` target
- Ktor client (Wasm) + kotlinx.serialization for Firebase REST
  (Identity Toolkit + Firestore REST — no official Firebase SDK exists for Wasm)
- Pure-Kotlin QR encoder (`qr/QrGen.kt`) and PNG encoder (`ui/Png.kt`)
- The only JavaScript is the standard toolchain-generated loader plus two
  one-line browser bridges (file download), declared from Kotlin via `@JsFun`

## Build & Run

Requires JDK 17+ and Gradle 8.14+.

```bash
# Dev server (opens in browser)
gradle wasmJsBrowserDevelopmentRun

# Production bundle -> build/dist/wasmJs/productionExecutable/
gradle wasmJsBrowserDistribution
```

Deployment is automatic: pushing to `main` runs the GitHub Actions workflow
(`.github/workflows/deploy.yml`), which builds the bundle and deploys it to
GitHub Pages.

## Browser support

Chrome/Edge 119+, Firefox 120+, Safari 18.2+ (WebAssembly GC required).

## Project layout

```
src/wasmJsMain/kotlin/qrmaster/web/
  Main.kt            entry point (ComposeViewport)
  ui/App.kt          shell: sidebar, top bar, bottom nav, navigation state
  ui/Screens.kt      Dashboard, All Tools, QR Generator, Wallet, Premium, Login
  ui/Theme.kt        QR Master purple design system (light + dark)
  ui/Icons.kt        hand-drawn vector icons (Canvas)
  ui/Png.kt          pure-Kotlin PNG encoder
  ui/Platform.kt     browser download bridge (@JsFun)
  data/Firebase.kt   Auth + Firestore REST, session storage
  data/FirebaseConfig.kt  existing project config (public client key)
  qr/QrGen.kt        pure-Kotlin QR encoder (Nayuki port, MIT license inside)
```
