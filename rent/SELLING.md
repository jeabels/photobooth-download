# Selling the booking page to other rental shops

Each shop that buys the booking page runs it from its own Google Sheet (see
[google-sheet/SETUP.md](google-sheet/SETUP.md)). You decide which shops' pages are on with one
file: [`shops.json`](shops.json).

## Add a new shop

1. Send the owner `google-sheet/Code.gs` and `google-sheet/SETUP.md`. They follow steps 1 to 7 and
   send you their **Web app URL**.
2. Take the part of their URL between `/s/` and `/exec`. That is their **shop ID**.
3. Open `rent/shops.json` on GitHub, tap the pencil (Edit), and add the shop to the list:

   ```json
   {
     "slug": "juanrentals",
     "name": "Juan Phone Rentals",
     "shop": "AKfycb…their shop ID…",
     "paidUntil": "2026-11-30",
     "note": "Monthly, paid by GCash on Oct 30"
   }
   ```

   - **slug**: their short link name: lowercase letters, numbers and dashes only.
   - **paidUntil**: the last day they've paid for, written as YYYY-MM-DD. Leave it `""` for a shop that never expires.
   - Put a comma between shops.
4. Commit the change. Within a few minutes their page is live at
   `https://jeabels.github.io/photobooth-download/rent/juanrentals`.

## Monthly fee

- **Renew:** when they pay, move **paidUntil** to the end of the new month and commit.
- **Late payment:** their page keeps working for `graceDays` days after **paidUntil** (5 by default), then shows
  "Online booking for <shop> is paused". Their Google Sheet and bookings are untouched, so the page
  comes back as soon as you move **paidUntil** forward.
- **Pause now:** set **paidUntil** to a past date. **Remove for good:** delete their entry.

A shop that isn't in `shops.json` gets "This booking link isn't set up", whether they use their short
link or a full `?shop=` link. Your own shop (Snaps MNL) always works.

This check runs in the renter's browser, so someone technical could copy the page and host it
themselves. It's enough to stop ordinary use without paying.

## Their own web address

The short link works on its own. If a shop wants `juanrentals.com`, they buy the domain and turn on
**URL redirect / forwarding** to their short link in the domain seller's settings (SETUP.md, step 2).
