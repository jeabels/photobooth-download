# Snaps Photobooth

A self-service photobooth for Android tablets. Guests pick a layout, pay with cash or GCash, take their shots, add stickers and text, print, and scan a QR code to keep a digital copy.

This repository is the whole product: the Android app, the booth screen inside it, the Supabase database setup, and an optional guest download page.

```
snaps-photobooth/
├── app/                              Android app (Kotlin)
│   └── src/main/
│       ├── assets/booth/index.html   The booth screen — themes, layouts, camera, editor, sales
│       ├── assets/booth/vendor/      Offline QR code library
│       └── java/com/snapsstudio/booth/
│           ├── MainActivity.kt       Full-screen WebView, camera, file uploads, kiosk
│           ├── BoothBridge.kt        What the booth screen can ask the app to do
│           ├── BluetoothPrinter.kt   Receipt printer over classic Bluetooth
│           ├── BleLink.kt            …and over Bluetooth LE, as a fallback
│           ├── PhotoPrinter.kt       Colour photo printing through Android's print system
│           └── FileSaver.kt          Saves strips to the Gallery and CSVs to Downloads
├── supabase/setup.sql                Photos bucket (your project, or a buyer's own)
├── supabase/sales.sql                Download codes (your project only)
├── supabase/functions/download-gate  Emails you codes, hands out the APK, connects booths
├── docs/index.html                   Guest download page (a copy is hosted on GitHub Pages)
└── .github/workflows/build-apk.yml   GitHub builds the APK for you on every push
```

---

## 1. Put the project on GitHub

The zip already contains a Git history with the first commit.

1. On github.com, click **New repository**. Name it `snaps-photobooth`, set it to **Private**, and leave "Add a README" **unticked**.
2. Unzip this project, open a terminal in the `snaps-photobooth` folder, and run (use your GitHub username):

   ```bash
   git remote add origin https://github.com/YOUR-USERNAME/snaps-photobooth.git
   git push -u origin main
   ```

   With GitHub Desktop instead: **File → Add local repository →** choose the folder **→ Publish repository**, keeping "Keep this code private" ticked.

## 2. Get the APK

**Easiest — let GitHub build it.** Every push to `main` builds the app automatically.
Open your repository → **Actions → Build APK →** the latest run **→ Artifacts → SnapsPhotobooth-apk**. The zip holds `SnapsPhotobooth.apk`.

To publish a version on the **Releases** page, tag it:

```bash
git tag v1.0.0
git push origin v1.0.0
```

**Or build it yourself.** In Android Studio: **File → Open →** the `snaps-photobooth` folder, wait for Gradle to sync, then press **Run** with the tablet connected by USB.

## 3. Supabase

Supabase keeps the digital copies guests download with the QR code. Your own project already has everything (`setup.sql` and `sales.sql`). Buyers don't need an account: the download code connects their booth for them (section 6).

To give a buyer their **own** project instead of sharing yours, create a project for them, run [`supabase/setup.sql`](supabase/setup.sql) in it, and put its project reference and publishable key on their row (section 6).

## 4. Set up the tablet

1. Copy `SnapsPhotobooth.apk` to the tablet and open it. Allow "Install unknown apps" when asked.
2. Open **Snaps Photobooth** and allow **Camera** and **Nearby devices**.
3. The first time it opens, it asks for the **6-digit download code**. Enter it and tap **Connect**: digital copies are switched on and the booth name is filled in. (Skipped it? **⋮ → Advanced → Digital copies → Connect**.)
4. Tap **⋮** in the top-right corner and enter the PIN `1234`. Change it straight away under **Advanced → Owner PIN**.

### Receipt (thermal) printer: RP30A and similar

1. Turn the printer on. In **Android Settings → Bluetooth**, pair it. The PIN is usually `0000` or `1234`.
2. In the booth: **Printer → Receipt printer → Choose printer**, then tap your printer.
3. Tap **Print test strip**. If faces come out as dark blobs, lower **Darkness**.
4. **Texture**: **Fine** scatters tiny dots for the sharpest detail; **Smooth** uses an even dot pattern for softer skin and cleaner backgrounds. Try both on your paper.

The app uses classic Bluetooth first and switches to Bluetooth LE automatically for printers that need it. RawBT and other helper apps aren't needed.

### Colour photo printer on photo paper

1. Make sure Android can see the printer. Go to **Android Settings → Connected devices → Printing** and turn on **Default Print Service** (Mopria). Some printers need their maker's plugin instead: HP Smart, Epson iPrint, Canon Print Service or Brother Print Service.
2. In the booth: **Printer → Photo printer**, then pick the paper you load: 2×6, 4×6, 5×7 or A6.
3. Tap **Print a test page**. In Android's print screen, choose your printer and paper once; Android remembers them.

### GCash

1. Save your GCash QR as an image from the GCash app.
2. In the booth: **Payments → Your GCash QR → Upload QR**, then zoom and move it until it fills the frame.

Guests pick **GCash**, scan the QR, send the amount shown and tap **I've paid** to start their shots. Every tap is logged under **Sales**, so you can match it against your GCash history.

### Kiosk lock

Go to **Advanced → Lock booth** to pin the app so guests can't leave it. Android asks you to confirm. If nothing happens, turn on **Settings → Security → App pinning** first. The Back button only closes dialogs; it never leaves the booth.

## 5. Guest download page

When a guest scans the QR code, their phone opens a page with the booth's name, the strip in colour or black & white, and a **Save to my phone** button. On Android the photo downloads straight to the phone; on iPhone the share sheet opens with **Save Image**.

One page serves every booth, yours and your buyers', because each QR says which Supabase project the photo is in. It lives at **https://jeabels.github.io/photobooth-download/**, which the app already uses by default (`DOWNLOAD_PAGE` near the top of the script in `index.html`).

To put the page online, once:

1. On GitHub, create a new **Public** repository named exactly `photobooth-download`, with no README.
2. Unzip `photobooth-download.zip`, open a terminal in that folder, and run:
   ```bash
   git remote add origin https://github.com/jeabels/photobooth-download.git
   git push -u origin main
   ```
3. In that repository: **Settings → Pages → Build and deployment → Deploy from a branch → main / (root) → Save**. The page is live a minute later.

The booth checks the page is online before using it. If it isn't, the QR opens the photo directly, and the guest presses and holds it to save, so a QR never leads to a missing page. **Advanced → Test link** tells you which of the two the booth is using.

## 6. Sales page and download codes

Send customers **https://jeabels.github.io/photobooth-download/app/**. They can try the real booth there (demo mode: DEMO watermark, no printing or uploads), then request the app. Each request emails **you** a 6-digit code. Once they've paid, send them the code. They enter it on the same page, on any device, and the APK downloads. A code allows 3 downloads within 7 days. Ten wrong guesses from one address block it for an hour.

**The same code connects their booth.** On first launch the app asks for it and fills in the digital-copy settings by itself. A code can connect up to 5 tablets within 60 days.

Where their photos go:

- **Your project (default).** Leave `cloud_ref` and `cloud_key` empty on their row. Nothing to do. Every booth shares your free 1 GB of storage, roughly 3,000 guest sessions in total, so watch **Storage → photos** and delete old files under `p/` now and then, or upgrade the plan.
- **Their own project.** Before sending the code, create a Supabase project for them, run `supabase/setup.sql` in it, then in **your** project open **Table Editor → download_requests →** their row and fill in `cloud_ref` (the part before `.supabase.co`) and `cloud_key` (its `sb_publishable_…` key).

Setup, once, in **your** Supabase project (buyers never do this):

1. **SQL Editor → New query**: paste all of `supabase/sales.sql` and **Run**. Run it again after every update; it only adds what's missing.
2. **Storage → releases → Upload file**: upload `SnapsPhotobooth.apk` (from the latest GitHub build). Upload again, replacing it, whenever you release a new version.
3. **Email.** Sign up at [resend.com](https://resend.com) **with the Gmail that should receive codes**, then **API Keys → Create API key** and copy it.
4. **Edge Functions → download-gate → Code**: paste all of `supabase/functions/download-gate/index.ts` and **Deploy**. Keep **Verify JWT** (or "Enforce JWT verification") **off**.
5. **Edge Functions → Secrets**: `RESEND_API_KEY`, `OWNER_EMAIL` (your Gmail) and `BOOTH_PUBLIC_KEY` (your `sb_publishable_…` key, which booths sharing your project use).
6. Open the sales page, send yourself a request, and check your Gmail for the code.

If the page says "The download service is not set up yet", add one more secret, `SB_SECRET_KEY`, with your **secret** key from **Project Settings → API Keys** (it stays on the server).

See who asked, downloaded and connected: **Table Editor → download_requests**.

The website itself (guest page, sales page and demo) is built by GitHub on every push: **Actions → the latest run → Artifacts → website**. Copy its contents into the `photobooth-download` repository and push.

## 7. Selling it to other photobooth owners

- **Keep this repository private.** It's your source code.
- **Give each buyer** the download code and section 4 of this guide. Their sales stay on their own tablet; their photos go to your project or their own (section 6).
- **Sign your releases with your own key,** so updates install over older versions without wiping a buyer's settings:

  ```bash
  keytool -genkeypair -v -keystore release.jks -alias snaps -keyalg RSA -keysize 2048 -validity 10000
  base64 -w0 release.jks > release.jks.b64     # macOS: base64 -i release.jks -o release.jks.b64
  ```

  In GitHub, go to **Settings → Secrets and variables → Actions** and add `KEYSTORE_BASE64` (the contents of `release.jks.b64`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`snaps`) and `KEY_PASSWORD`. **Back up `release.jks` and its passwords.** If you lose them, you can't ship updates to existing buyers.
- **For each new version,** raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit, and push a new tag.
- **To rebrand a copy,** change `app_name` in `app/src/main/res/values/strings.xml` and `applicationId` in `app/build.gradle.kts`. Buyers pick from 10 themes, 35 header typefaces and 12 start-button designs, and set their own header and wallpaper, under **Theme & branding**.
- There's no licence-key check yet: anyone who has the APK can install it.

## 8. Changing the booth screen

Everything guests see is in `app/src/main/assets/booth/index.html`. You can open that file straight in Chrome on a computer to try changes. There, printing uses Chrome's print dialog and Web Bluetooth instead of the app. Commit and push, and GitHub builds a new APK.

## Troubleshooting

| What happens | What to do |
|---|---|
| Black camera preview | Android Settings → Apps → Snaps Photobooth → Permissions → Camera → Allow. |
| No printers listed | Pair the printer in Android Bluetooth settings first, and allow **Nearby devices** for the app. |
| "Could not reach the printer" | Turn the printer off and on, keep it within a few metres, then tap **Choose printer** again. |
| Photo printer isn't in the print screen | Turn on Mopria (Default Print Service) or install the printer maker's print plugin. |
| QR opens "not found" | Tap **Test digital copies** under Advanced → Digital copies; if it fails, connect again with the download code. |
| "That code could not be used" when connecting | Check the tablet has internet, and that `sales.sql` was run again and `download-gate` redeployed after this update. |
| Test link says the download page is not online | Check the `photobooth-download` repository is **Public** and Pages is switched on (section 5). |
| Uploads stop after a quiet week | Free Supabase projects pause after 7 days without use. Open the project in the dashboard and choose **Restore**, or upgrade the plan. |
| Settings vanished after reinstall | Uninstalling clears the app's data. Install updates over the old version instead; that needs the same signing key. |

## Licence

Proprietary. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
