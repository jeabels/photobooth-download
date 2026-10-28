# Food POS: offline-first Android POS and business intelligence app

A point of sale, rolling inventory and financial management app for a retail food business.
It runs **100% locally** on the device (Room/SQLite) and mirrors its data to **Firebase Firestore**
in the background whenever an internet connection is available.

| | |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3, adaptive layout |
| Local database | Room (SQLite), the single source of truth |
| Cloud | Firebase Firestore + Anonymous Auth, background sync with WorkManager |
| Architecture | MVVM, `StateFlow`, Coroutines, manual DI (`AppContainer`) |
| Min / target SDK | 26 (Android 8.0) / 35 |

---

## Features by module

### 1. Point of Sale and checkout
- Product **grid or list view** (toggle), search, and category chips. Every product shows a **live stock counter**
  that already subtracts what is in the cart. Products that have run out are disabled.
- **Cart**: add, subtract, remove, clear. You can't add more than the sellable stock.
- **Checkout**: Walk-in/Pick-up or **Delivery** with a **dynamic delivery fee** (typed, or one-tap presets
  from Settings). Customer name, phone and delivery address (required for delivery), and order notes.
- **Payment methods**: Cash (amount received, quick-cash buttons, change calculated), **E-Wallet** and
  **Bank Transfer** (optional reference number).
- **Queue numbers**: generated automatically (`A-001`, `A-002`, …), restarting every day. Each device has its
  own prefix (Settings), so two tills never hand out the same number.
- **Real-time stock deduction** in the same database transaction as the order. Stock comes out of the batches
  that expire first (FEFO). Spoiled batches are never sold.
- Order history with search and filters, **receipt reprint**, and **void** (Owner only). Voiding puts the
  items back into the exact batches they came from.

### 2. Bluetooth thermal printer (ESC/POS)
- Native **RFCOMM socket** (Serial Port Profile UUID `00001101-…`) to a paired printer. If the secure
  connection fails it falls back to an insecure one, which some cheap printers require.
- Hand-written **ESC/POS byte commands** (`printer/EscPos.kt`): initialize, alignment, bold, double width
  and height, feed, cut, and **`GS v 0` raster images** sent in 128-row bands so small printer buffers don't overflow.
- The **business logo** is converted at runtime: scaled to the paper, alpha-flattened, converted to
  greyscale, **Floyd–Steinberg dithered** to 1-bit, packed MSB-first and **centred** on the paper width.
- Receipts include the logo, a **centred** header, the queue number in large type, each item with
  **quantity × unit price** and line total, a **bold** double-height **TOTAL**, payment, cash and change,
  customer details and a footer.
- 58 mm (32 characters, 384 dots) and 80 mm (48 characters, 576 dots) paper. Optional auto-print after checkout,
  a test page, and an on-screen **receipt preview** with exactly the printed layout.

### 3. Rolling inventory
- Stock is tracked in **batches**: batch number, **manufacturing date**, **expiration date**, quantity
  received, unit cost and a supplier note.
- **Inventory movement ledger**: every change is an immutable row: *New delivery*, *Sale*,
  *Manual adjustment*, *Spoilage write-off*, *Voided sale*. A batch's stock on hand is the sum of its ledger.
  This also makes multi-device sync conflict-free.
- **Alerts** pushed to the UI live (badge, snackbar, Alerts screen) and as a system notification (checked every 6 h):
  - **Out of Stock**: no sellable stock left
  - **Running Low**: at or below the product's threshold
  - **Spoiled**: past the expiry date (with a one-tap write-off)
  - **Spoiling Soon**: expires within the product's custom number of days
- Each product has its own **low-stock threshold** and **spoiling-soon days**. The defaults are set in Settings.

### 4. Expenses and financial ledger
- Log operating expenses: **amount, date, category, description, notes**. You can edit and delete them;
  deletions are soft so they sync too.
- Real-time **Gross Revenue, Total Orders, Total Expenses, Net Profit** (and average order value) for Today,
  7 days, This month, This year, or All time.
  *Gross revenue* = sum of completed order totals (voided orders are excluded). *Net profit* = gross revenue − expenses.

### 5. Dashboards: all drawn with Compose `Canvas`
No chart libraries (no MPAndroidChart, Vico, etc.). The code is in `ui/charts/`.
- **Bar chart**: daily sales for the last 30 days. Tap a bar for its exact value.
- **Interactive calendar**: monthly sales heat map. Tap a day for its sales and order count; arrows move between months.
- **Line graph**: sales trend with **pill filters: Weekly / Monthly / Yearly**.
- **Donut/pie chart**: expenses by category. Tap a slice or a legend row.
- Every chart is wrapped in a responsive **`BoxWithConstraints`**. The plot gets a minimum width per data point
  and **scrolls horizontally** when that is wider than the screen, so points never squish together on phones.
  The y-axis stays fixed while the plot scrolls, and the chart opens scrolled to the newest data.

### 6. Security and roles
- Passwords are hashed with **salted SHA-256**: a 16-byte `SecureRandom` salt per user, digest re-hashed
  10,000 times (key stretching), constant-time comparison. Stored as `sha256$<iterations>$<hex>`.
  Plain passwords are never stored or synced.
- **Owner vs Staff**. The first account on a store becomes the Owner. Every later registration is **Staff
  in a Pending state** and can't sign in until an Owner approves it (Team screen). Owners can also reject or
  remove access, and promote staff or demote owners. The app keeps at least one active owner.
- Staff see the POS, Orders, Inventory (view + receive deliveries) and Alerts. Owner-only screens
  (Dashboard, Expenses, Stock ledger, Team, Settings, product edits, adjustments, write-offs, voids) are not
  even registered in staff navigation.
- **Adaptive layout**: phones and small tablets get a **sliding navigation drawer**. Large tablets
  (expanded width) get a **permanent side-by-side drawer**, and on wide screens the POS shows the cart
  next to the product grid.

---

## Project structure

```
app/src/main/java/com/foodpos/app/
├── PosApplication.kt, MainActivity.kt, AppContainer.kt   app start-up and dependency wiring
├── core/            Money (cents as Long), Dates, ids
├── data/local/      Room entities, DAOs, PosDatabase
├── data/repository/ Auth, Users, Inventory, Orders (checkout transaction), Expenses, Settings
├── data/sync/       SyncManager (WorkManager scheduling), SyncWorker, FirestoreSync (push/pull/merge)
├── data/analytics/  SalesAnalytics: pure report maths for the dashboard
├── alerts/          StockCalculator, InventoryAlerts, AlertNotifier (+ periodic worker)
├── printer/         EscPos commands, MonoRaster (dithering), BitmapRaster, Receipt layout,
│                    ReceiptFormatter, EscPosRenderer, BluetoothPrinterService, ReceiptPrinter
├── security/        PasswordHasher, SessionManager
└── ui/              Compose screens + ViewModels: auth, pos, orders, inventory, alerts,
                     dashboard, charts, expenses, users, settings, theme, components
app/src/test/        JVM unit tests (hashing, money, ESC/POS bytes, dithering, receipt layout,
                     alerts, analytics)
firebase/            Firestore security rules and Firebase CLI config
```

Money is always stored as whole cents in a `Long`. Every row id is a UUID, so records created offline on
different devices never collide.

---

## Building

Requirements: Android Studio (Ladybug or newer) or JDK 17 with the Android SDK (platform 35).

```bash
cd pos-android
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

The app builds and runs **without** any Firebase setup. In that case it works fully offline and the cloud
icon shows "Offline mode".

GitHub Actions (`.github/workflows/android-pos.yml`) runs the unit tests and builds the debug and minified
release APKs on every push that touches `pos-android/`. The APKs are attached to each run as the
`food-pos-apks` artifact.

---

## Firebase setup (cloud sync)

1. In the [Firebase console](https://console.firebase.google.com/) create a project, for example *food-pos*.
2. **Build → Firestore Database → Create database** (production mode, a region near the store).
3. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
4. **Project settings → Your apps → Add app → Android**, package name **`com.foodpos.app`**.
   Download **`google-services.json`** and put it at `pos-android/app/google-services.json`
   (it is git-ignored). The Google Services Gradle plugin is applied automatically when the file exists.
5. Deploy the security rules:
   ```bash
   cd pos-android/firebase
   npm install -g firebase-tools
   firebase login
   firebase deploy --only firestore --project <your-project-id>
   ```
   (Or paste `firebase/firestore.rules` into *Firestore → Rules* in the console.)
6. Rebuild. In CI, store the file's contents in the repository secret **`POS_GOOGLE_SERVICES_JSON`** instead.

### How sync works
- Every local write marks the row `dirty` and asks WorkManager for a sync. The sync runs once the device is online:
  immediately, or hours later after an offline stretch. It retries with exponential backoff and also runs every 15 minutes.
- **Push**: dirty rows are uploaded in batches of 400 with a server timestamp, then marked clean (unless they
  were edited again during the upload).
- **Pull**: documents whose server timestamp is newer than the last pull are merged with **last-write-wins** on
  `updatedAt`. Stock ledger rows and order lines are append-only, so stock levels never conflict between devices.
- Data layout: `businesses/{syncKey}/{users|products|batches|movements|orders|expenses}/{id}`.
- **Adding a second device**: on its sign-in screen choose *Join an existing store (sync key)* and enter the
  key shown under **Settings → Cloud sync** on the owner's device. Accounts and data download, and staff can sign in.

### Security notes
- Anonymous Auth means only app installs can talk to Firestore, and a store's data can only be reached by someone
  who knows its random 20-character sync key. Password hashes are synced so staff can sign in on any till;
  plain passwords never leave the device.
- For a deployment with many unrelated stores, the next step is Firebase Email/Password Auth for owners plus a
  `members` list per business checked in the rules. The data layout already supports this.

---

## Signed release APK

1. Create a keystore once and keep it safe. Losing it means you can't publish updates.
   ```bash
   keytool -genkeypair -v -keystore food-pos-release.jks -alias foodpos \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Local build**: create `pos-android/keystore.properties` (git-ignored):
   ```properties
   storeFile=/absolute/path/to/food-pos-release.jks
   storePassword=…
   keyAlias=foodpos
   keyPassword=…
   ```
   then
   ```bash
   ./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
   ```
3. **CI build**: add these repository secrets, and every run then produces a signed, R8-minified `app-release.apk`:
   - `POS_KEYSTORE_BASE64`: output of `base64 -w0 food-pos-release.jks`
   - `POS_KEYSTORE_PASSWORD`, `POS_KEY_ALIAS`, `POS_KEY_PASSWORD`

   Without them the release APK is built unsigned (`app-release-unsigned.apk`).

---

## First run

1. Open the app and create the **Owner** account.
2. **Settings**: business name, address, phone, receipt footer and logo; pair the printer in Android's Bluetooth
   settings, then tap *Paired devices*, pick it, choose 58/80 mm and print a test page; set the queue prefix,
   currency and delivery fee shortcuts.
3. **Inventory → +**: add products (price, unit, category, low-stock and spoiling-soon thresholds).
4. **Receive delivery** for each product (batch number, quantity, manufacturing and expiry dates).
5. Sell from **Point of Sale**. Staff register on the sign-in screen, and the owner approves them under **Team**.
