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
   It lets the script save bookings, store renters' photos in your Drive, and email you about new bookings.
5. Click **Deploy → New deployment**, then the gear icon → **Web app**. Set **Execute as: Me** and
   **Who has access: Anyone**, then click **Deploy**.
6. Copy the **Web app URL**. It looks like `https://script.google.com/macros/s/AKfy…/exec`.

## 2. The shop's booking link

Take the part of the URL between `/s/` and `/exec`. That is the shop's ID. The booking link is:

```
https://jeabels.github.io/photobooth-download/rent/?shop=<shop ID>
```

Share that link on the shop's Facebook page, as its "Book Now" button, and in post captions.

## 3. Fill in the shop's details

Setup adds three tabs. Edit them any time; the website updates within a minute.

| Tab | What to change |
|---|---|
| **Settings** | Shop name, area, pick-up place, mobile, Messenger page, Facebook link, **banner image**, **brand colour**, deposit, hours per rental day, late fee per hour, hand-over options, what's included, minimum notice, how far ahead renters can book, **closed dates**, and the email for new bookings. |
| **Units** | One row per phone: Unit ID (short, no spaces, never change it once bookings exist), name, detail (storage · colour), rate per day, **poster image**, Show on website (Yes/No), and how it's written in the month tabs. |
| **Policies** | The rules renters must tick: **Operational** and **Financial** sections. |

**Banner and poster images:** upload the image to Google Drive and paste its share link into
**Banner image** (Settings) or **Poster image** (Units). Then use the sheet's menu (it has the
shop's name) → **Make banner and poster images public** so the website can show them. A banner
looks best about 3 times wider than it is tall, e.g. 1920 × 640.

## 4. Taking bookings

- New bookings arrive in the **Website Bookings** tab as **Pending**, with the renter's details
  and a Google Drive link to their 3 requirement photos. The shop gets an email for each one.
- A Pending or Confirmed booking blocks that unit on the website from pick-up to return.
- Set **Status** to **Confirmed** after checking the photos. The booking is also copied into
  that month's tab.
- Set **Cancelled** to free the unit, or **Returned** when the phone is back.
- Walk-in or Messenger bookings: add a row in Website Bookings with Unit ID, Pick-up, Return and
  Status, so the website blocks those times too.
- Renters can tap **Check my booking** on the page and enter their reference and mobile number
  to see whether they're Pending, Confirmed, Cancelled or Returned.

## Updating the script later

Paste the new `Code.gs` over the old one, save, run **setup** again (it never overwrites the
shop's Settings, Units or Policies), then **Deploy → Manage deployments → pencil (Edit) →
Version: New version → Deploy**. Use Manage deployments, not New deployment, so the shop's link
stays the same.
