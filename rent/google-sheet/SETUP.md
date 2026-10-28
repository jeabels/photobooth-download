# Phone rental booking page: setup for a new shop

Each shop gets its own booking page, run from its own Google Sheet. The shop
owner changes everything on their page from that Sheet, with no code to edit.

## 1. Make the shop's Google Sheet (about 5 minutes)

1. Go to **sheets.google.com** and create a blank sheet, e.g. "Juan Phone Rentals Bookings".
2. Click **Extensions → Apps Script**.
3. Delete what's in the editor, paste in all of `Code.gs`, and press **Ctrl + S** to save.
4. In the menu at the top, choose **setup** and click **Run**. Google asks for permission.
   Choose your account, then **Advanced → Go to (project) (unsafe) → Allow**.
   This is your own script, so the warning is expected.
   It lets the script save bookings, store renters' photos and your pictures in your Drive,
   email you about new bookings, and refresh your earnings dashboard every morning.
5. Click **Deploy → New deployment**, then the gear icon → **Web app**. Set **Execute as: Me** and
   **Who has access: Anyone**, then click **Deploy**.
6. Copy the **Web app URL**. It looks like `https://script.google.com/macros/s/AKfy…/exec`.
7. Send that URL to the seller of this booking page so they can switch your page on.

## 2. The shop's booking link

Once your page is switched on you get a short link:

```
https://jeabels.github.io/photobooth-download/rent/<your shop's short name>
```

Share it on the shop's Facebook page, as its "Book Now" button, and in post captions.

**Your own web address (optional):** buy a domain (for example `juanrentals.com`) from a seller
such as Namecheap, GoDaddy or Hostinger. In the domain's settings, turn on **URL redirect / forwarding**
to your short link. Renters who type your domain land on your booking page.

## 3. Fill in the shop's details

Setup adds these tabs. Edit them any time; the website updates within a minute.

| Tab | What to change |
|---|---|
| **Settings** | Shop name, area, pick-up place, mobile, Messenger page, Facebook link, **banner image** and how it is **zoomed, positioned and sized**, **rate card images**, **brand colour**, deposit, hours per rental day, late fee per hour, hand-over options, what's included, minimum notice, how far ahead renters can book, **closed dates**, and the email for new bookings. |
| **Units** | One row per phone: Unit ID (short, no spaces, never change it once bookings exist), name, detail (storage · colour), rate per day, **poster image**, Show on website (Yes/No), and how it's written in the month tabs. |
| **Policies** | The rules renters must tick: **Operational** and **Financial** sections. |
| **Promos** | Promo codes. **Discount** is pesos (`100`) or a percent (`10%`) off the rental. Optional start and end dates, and **Max uses** (blank for no limit). Set **Active** to **Yes** to use it. The example code is off until you switch it on. |
| **Delivery** | One row per area you deliver to, with its fee. Renters who choose delivery pick their area and the fee is added to their total. Leave it empty if renters book their own courier. |
| **Dashboard** | Made for you: earnings this month, last month, this year and all time; earnings by month (with a chart), by unit and by promo code; and pick-ups in the next 7 days. It counts **Confirmed** and **Returned** bookings, not deposits. |

### Pictures: banner, rate cards and posters

Use the sheet's menu (it has the shop's name):

- **Upload an image (banner, rate card or poster):** choose where the picture goes, pick it from
  your computer or phone, and press Upload. It's saved in a Drive folder named
  "<shop name> website images" and appears on the website within a minute.
  - **Banner**: the picture across the top of the page.
  - **Rate card**: a price list picture shown in a **Rates** section above the units. Add as many as
    you like, or choose "replaces all rate pictures" to start over. To remove one, delete its line
    in **Settings → Rate card images**.
  - **Poster**: the picture on a unit's card.
- **Adjust banner (zoom, position, size):** sliders to **zoom in or out**, move the picture **left–right**
  and **up–down**, and change its **size (shape)**: from 4:1 (short) to 1:1 (square), or Auto for the
  picture's own shape. The preview shows it as renters will see it. Press **Save banner**;
  **Reset to normal** undoes it.

You can also paste a Google Drive share link into **Banner image** (Settings) or **Poster image**
(Units), then use the menu → **Make banner and poster images public** so the website can show it.
A banner looks best about 3 times wider than it is tall, e.g. 1920 × 640.

Renters can tap the banner, a rate card or a poster to see it bigger, and use **+** and **−** to zoom.

## 4. The owner page

The booking link is for renters. The owner has their own page, at the booking link with `/admin` added:

```
https://jeabels.github.io/photobooth-download/rent/<your shop's short name>/admin
```

1. Open the owner page. The first time, it asks you to **create your owner account**: choose an
   **admin name** (no spaces) and a **password** (at least 6 characters), and type the
   **Owner setup code**. The code is in your Google Sheet's **Settings** tab (row "Owner setup code"),
   or use the menu with your shop's name → **Show owner setup code**. The code stops strangers who
   find the link from making an account.
2. After that, sign in with your admin name and password. Tick **Keep me signed in on this device**
   on your own phone so you don't need to type them each time.
3. **Staff:** give them the setup code. They tap **Register an admin name and password** on the
   sign-in screen. Under **Accounts** you can see everyone who can sign in, remove someone, and change
   your password. To stop new sign-ups, make a new code (menu → **Show owner setup code** → Yes).
4. **Forgot your password?** Use the menu → **Remove all owner page accounts**, then register again.
   Your bookings are not touched.

What's on it:

- **Dashboard:** sales today, this week and this month; pending bookings to review; phones rented out
  now; today's pick-ups and returns. A **Daily / Weekly / Monthly** sales chart (tap a column for its
  numbers, or **Show as table**), **Most rented units** for the same period, and today's and
  tomorrow's pick-ups and returns.
- **Bookings:** four stages, each with its count:
  - **Pending**: new bookings waiting for you. Tap **Accept** or **Decline**.
  - **Reserved**: accepted, not picked up yet.
  - **Ongoing**: picked up and not back yet. Ones past their return time say **late**. Tap
    **Mark as completed (returned)** when the phone is back.
  - **Completed**: returned. These count in your sales.

  Also **Today**, **Cancelled** and **All**, and a search by name, ref, mobile, email or unit. Tap a
  booking to:
  - accept, decline, complete or cancel it
  - change the unit, dates and times, or the renter's details
  - **give that renter a discount**: type it in pesos and press **Recalculate** for the new total
  - call or text the renter, and open their requirement photos
- **+ New booking:** add walk-in or Messenger bookings. They block the unit on the booking page too.

The page won't save a change that puts a unit in two Pending or Confirmed bookings at the same time.
Every change goes straight into the **Website Bookings** tab.

## 5. Emails to renters

Renters type their **active email (Gmail)** on the booking page. When you **accept** a booking, the
renter gets an email from your Gmail with the booking ref, phone, pick-up and return times, total and
deposit, your **Payment details**, and how to reach you. Declining or cancelling sends a short email too.
This works whether you change the Status on the owner page or type it in the sheet.

In **Settings**:
- **Payment details**: what renters pay to, e.g. `GCash 0917 000 0000 (Juan Cruz). Send a screenshot of your payment on Messenger.`
- **Email renters**: `Yes`, or `No` to stop these emails.
- **Booking page link**: filled in by the first website booking, and used for the "Check your booking" link in the emails.

The **Renter emailed** column shows what was sent and when, so nobody gets the same email twice. A
free Gmail account can send about 100 emails a day.

## 6. Taking bookings

- New bookings arrive in the **Website Bookings** tab as **Pending**, with the renter's details
  and a Google Drive link to their 3 requirement photos. The shop gets an email for each one.
- The **Estimate** column is the total the renter saw: rental, less any promo, plus any delivery
  fee (the deposit is separate). The sheet works it out itself, and also fills in **Promo code**,
  **Discount** and **Delivery fee**.
- A Pending or Confirmed booking blocks that unit on the website from pick-up to return.
- Set **Status** to **Confirmed** after checking the photos. The booking is also copied into
  that month's tab, and the dashboard updates.
- Set **Cancelled** to free the unit, or **Returned** when the phone is back.
- Walk-in or Messenger bookings: add a row in Website Bookings with Unit ID, Pick-up, Return and
  Status, so the website blocks those times too. Fill in Estimate to count it in the dashboard.
- Renters can tap **Check my booking** on the page and enter their reference and mobile number
  to see whether they're Pending, Confirmed, Cancelled or Returned.

## Updating the script later

Paste the new `Code.gs` over the old one, save, run **setup** again and allow any new permissions.
Setup never overwrites the shop's tabs; it only adds the tabs and settings that are new. Then click
**Deploy → Manage deployments → pencil (Edit) → Version: New version → Deploy**. Use Manage
deployments, not New deployment, so the shop's link stays the same. Reload the sheet to see the
new menu items.
