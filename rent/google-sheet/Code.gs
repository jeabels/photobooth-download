/**
 * Phone rental booking sheet — Google Apps Script for the /rent/ booking page.
 * Each shop has its own copy, in its own Google Sheet (Extensions > Apps Script).
 *
 * Owner tabs (created and pre-filled by setup; edit them to change the website)
 *   Settings  shop name, contact, Messenger, banner image and how it is zoomed,
 *             positioned and shaped, rate card images, deposit, rental rules,
 *             hand-over options, inclusions, closed dates, booking emails.
 *   Units     one row per phone: ID, name, detail, rate, poster image, shown or
 *             hidden, and how it is written in the month tabs.
 *   Policies  the operational and financial rules renters must tick.
 *   Promos    promo codes: % or peso discount, dates, how many times it can be used.
 *   Delivery  delivery areas and their fees. Empty means renters pay the courier.
 *   Dashboard earnings by month, by unit and by promo code, and the next pick-ups
 *             (rebuilt by the menu, every morning, and when a booking's status changes).
 *
 * The sheet's menu (it has the shop's name) can upload images straight to the
 * banner, a unit's poster or the rate cards, and adjust the banner by eye.
 *
 * Website Bookings tab
 *   Every booking from the website lands here as "Pending", with all the
 *   renter's details. A row blocks its unit on the website, from pick-up to
 *   return, while its Status is Pending or Confirmed. Set it to Cancelled (or
 *   Returned) to free the unit. Rows typed in by hand block units too, as long
 *   as Unit ID, Pick-up and Return are filled in.
 *
 * Month tabs (October, SEPTEMBER, MAY TRACKING DATA, …)
 *   When a booking's Status is changed to "Confirmed", a short row goes into the
 *   tab for its pick-up month: B Pick Up · C Rental Duration · D Name · E Unit ·
 *   F Status (RESERVED) · H rental total, with ref, mobile and hand-over in a
 *   note on the name. A missing month tab is copied from the previous month's,
 *   or made with these headers.
 *
 * Requirement photos go to a private Google Drive folder per booking, linked
 * from the booking's Photos column. Each new booking is emailed to the
 * Settings "Notify email".
 *
 * Web app
 *   GET               -> shop settings for the page, and booked time slots per
 *                        unit (unit id, start, end); no renter details.
 *   GET ?ref=&mobile= -> status of one booking, if the mobile number matches.
 *   GET ?promo=       -> whether a promo code can be used now, and its discount.
 *   POST              -> a booking; refused if the unit is already booked for an
 *                        overlapping time, the pick-up is too soon, a date is closed,
 *                        or the promo code or delivery area isn't valid. The sheet
 *                        works out the total itself.
 *   POST {action: "photos"} -> the 3 photos for a booking just saved.
 *   POST {action: "admin"}  -> the owner app (rent/admin/): register an admin name and password
 *                        (with the Settings "Owner setup code") or sign in, then read all bookings, and edit
 *                        or add them.
 *
 * Set up (once): paste this file into Extensions > Apps Script, run setup(),
 * then Deploy > New deployment > Web app, Execute as: Me, Who has access:
 * Anyone. The shop's booking link is the /rent/ page with ?shop=<deployment id>.
 */

var SHEET_NAME = 'Website Bookings';
var SETTINGS_TAB = 'Settings', UNITS_TAB = 'Units', POLICIES_TAB = 'Policies';
var PROMOS_TAB = 'Promos', DELIVERY_TAB = 'Delivery', DASH_TAB = 'Dashboard';
var OWNER_TABS = [SETTINGS_TAB, UNITS_TAB, POLICIES_TAB, PROMOS_TAB, DELIVERY_TAB];
var TZ = 'Asia/Manila';
var HEADERS = ['Ref', 'Submitted', 'Status', 'Unit ID', 'Unit', 'Pick-up', 'Return', 'Days', 'Estimate',
  'Hand-over', 'Name', 'Address', 'Mobile', 'Social (FB/IG)', 'Purpose',
  'Emergency name', 'Emergency relationship', 'Emergency mobile', 'Emergency address', 'Notes', 'Added to', 'Photos',
  'Promo code', 'Discount', 'Delivery fee'];
var COL = { status: 3, unitId: 4, pickup: 6, ret: 7, days: 8, estimate: 9, addedTo: 21, photos: 22, promo: 23, discount: 24, fee: 25 };
var STATUSES = ['Pending', 'Confirmed', 'Cancelled', 'Returned'];
var BLOCKING = ['Pending', 'Confirmed'];
var EARNING = ['Confirmed', 'Returned'];
/* Month-tab status for a confirmed website booking (must be one of that column's drop-down values). */
var MONTH_STATUS = 'RESERVED';
var MONTH_HEADERS = ['', 'Pick Up', 'Rental Duration', 'Name', 'Unit', 'Status', 'Rental Fee', 'Total', 'Deposit'];

/* What setup writes into new Settings, Units and Policies tabs. Existing tabs are never overwritten;
   settings missing from an older Settings tab are added at its end. */
var DEFAULT_SETTINGS = [
  ['Shop name', 'Snaps MNL', 'Shown on the booking page, in emails and in the Drive folder name'],
  ['Area', 'Sampaloc, Manila', 'Shown in the page footer'],
  ['Pick-up place', 'Zukiya Residences, Sampaloc, Manila', 'Where renters pick up and return'],
  ['Mobile', '09562086962', 'Text or call number shown to renters'],
  ['Messenger page', '100095276233681', 'Facebook page username or page ID (the part after facebook.com/), for the Messenger button'],
  ['Facebook page link', 'https://www.facebook.com/profile.php?id=100095276233681', 'Link in the page footer'],
  ['Banner image', 'https://jeabels.github.io/photobooth-download/rent/units/banner.webp', 'Google Drive link or image link. Blank shows the shop name instead. Easiest: menu > Upload an image'],
  ['Banner zoom (%)', 100, '100 shows the whole width. Higher zooms in, lower zooms out. Easiest: menu > Adjust banner'],
  ['Banner position', '50% 50%', 'Which part stays in view when zoomed in: left-right %, then top-bottom % (50% 50% is the middle)'],
  ['Banner shape', 'Auto', 'Auto keeps the picture\'s own shape. Or width:height, e.g. 4:1 for a shorter banner, 2:1 for a taller one'],
  ['Rate card images', '', 'Price list pictures shown above the units, one link per line. Easiest: menu > Upload an image'],
  ['Brand colour', '#8E1016', 'Colour of buttons and the header, as a hex code like #8E1016 (blank keeps the standard red)'],
  ['Deposit', 500, 'Refundable security deposit in pesos (0 for none)'],
  ['Hours per rental day', 25, 'How many hours one rental day lasts'],
  ['Late fee per hour', 100, 'In pesos'],
  ['Hand-over options', 'Pick up at Zukiya Residences, Delivery', 'Comma-separated. The first is pick-up at your place; the rest are deliveries'],
  ['Included with every rental', 'iPhone with case, Unit pouch, 64GB OTG file transfer, Cleaning wipes, Power bank, Fast charger', 'Comma-separated'],
  ['Minimum notice (hours)', 2, 'How long before pick-up a renter can still book online'],
  ['Book up to (days ahead)', 180, 'How far ahead renters can book'],
  ['Closed dates', '', 'Days with no pick-ups or returns, comma-separated, e.g. 2026-12-25, 2027-01-01'],
  ['Notify email', '', 'Each new booking is emailed here. Blank turns the emails off'],
  ['Owner setup code', '', 'Needed to register an admin name and password on the owner page (booking link + /admin). Setup makes one. Change it to stop new sign-ups']
];
var DEFAULT_UNITS = [
  ['16pro-desert', 'iPhone 16 Pro', '128GB · Desert Titanium', 1000, 'https://jeabels.github.io/photobooth-download/rent/units/iphone-16-pro-desert-titanium.webp', 'Yes', '16'],
  ['14pm-black', 'iPhone 14 Pro Max', '128GB · Space Black', 800, 'https://jeabels.github.io/photobooth-download/rent/units/iphone-14-pro-max.webp', 'Yes', '14'],
  ['13pm-blue', 'iPhone 13 Pro Max', '256GB · Sierra Blue', 700, 'https://jeabels.github.io/photobooth-download/rent/units/iphone-13-pro-max-sierra-blue.webp', 'Yes', '13pm'],
  ['13pm-graphite', 'iPhone 13 Pro Max', '256GB · Graphite', 700, 'https://jeabels.github.io/photobooth-download/rent/units/iphone-13-pro-max-graphite.webp', 'Yes', '13pm (2)'],
  ['13-starlight-z1', 'iPhone 13', '128GB · Starlight #Z1', 600, 'https://jeabels.github.io/photobooth-download/rent/units/iphone-13-starlight.webp', 'Yes', '13 (Z1)'],
  ['13-starlight-j2', 'iPhone 13', '128GB · Starlight #J2', 600, 'https://jeabels.github.io/photobooth-download/rent/units/iphone-13-starlight.webp', 'Yes', '13 (J2)']
];
var UNIT_HEADERS = ['Unit ID', 'Name', 'Detail', 'Rate per day', 'Poster image', 'Show on website', 'Month-tab name'];
var DEFAULT_POLICIES = [
  ['Operational', 'Rental duration is strictly limited to 25 hours from pick-up.'],
  ['Operational', 'A late return penalty of ₱100 per hour applies.'],
  ['Operational', 'Down payments are non-refundable.'],
  ['Operational', 'Pick-up and return are at Zukiya Residences, Sampaloc, Manila.'],
  ['Operational', 'I will not jailbreak, root, or install unauthorized apps.'],
  ['Operational', 'I am willing to pay a ₱500 security deposit (refundable).'],
  ['Operational', 'I agree to shoulder all shipping fees, both delivery and return.'],
  ['Operational', "If a late return affects another renter's schedule, a penalty fee equal to the total booking rate will apply."],
  ['Financial', 'Minimum penalty of ₱5,000 for any identified internal or external damage.'],
  ['Financial', 'Liability for full repair costs if damage exceeds the initial penalty.'],
  ['Financial', 'Obligation to pay the full replacement value in the event of loss or theft.'],
  ['Financial', 'Late returns may result in the forfeiture of my security deposit.'],
  ['Financial', 'Security protocol: I agree that the unit will be placed in Lost/Locked Mode if I fail to provide updates, especially when time is running out.']
];
var PROMO_HEADERS = ['Code', 'Discount', 'Valid from', 'Valid until', 'Max uses', 'Active', 'Notes'];
var DEFAULT_PROMOS = [['WELCOME100', 100, '', '', 50, 'No', 'Example: ₱100 off. Type 10% for a percent off. Set Active to Yes to use it']];
var DELIVERY_HEADERS = ['Area', 'Fee'];

/** Run once from the Apps Script editor (and again after pasting a new version): adds the owner tabs
    and the Website Bookings tab, and asks for Drive, email and trigger access. */
function setup() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.setSpreadsheetTimeZone(TZ);
  var defaults = DEFAULT_SETTINGS.map(function (r) {
    return r[0] === 'Notify email' ? [r[0], Session.getEffectiveUser().getEmail(), r[2]] : r;
  });
  addTab_(ss, SETTINGS_TAB, ['Setting', 'Value', 'Notes'], defaults);
  addMissingSettings_(ss.getSheetByName(SETTINGS_TAB), defaults);
  addTab_(ss, UNITS_TAB, UNIT_HEADERS, DEFAULT_UNITS);
  addTab_(ss, POLICIES_TAB, ['Section', 'Policy'], DEFAULT_POLICIES);
  addTab_(ss, PROMOS_TAB, PROMO_HEADERS, DEFAULT_PROMOS);
  addTab_(ss, DELIVERY_TAB, DELIVERY_HEADERS, []);
  ss.getSheetByName(DELIVERY_TAB).getRange(1, 1).setNote('One row per area you deliver to, with its fee in pesos, e.g. "Quezon City" and 150. ' +
    'Renters who choose delivery pick their area and the fee is added to their total. Leave empty if renters book their own courier.');
  var pol = ss.getSheetByName(POLICIES_TAB);
  pol.getRange(2, 1, pol.getMaxRows() - 1, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(['Operational', 'Financial'], true).build());
  var units = ss.getSheetByName(UNITS_TAB);
  units.getRange(2, 6, units.getMaxRows() - 1, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(['Yes', 'No'], true).build());
  var promos = ss.getSheetByName(PROMOS_TAB);
  promos.getRange(2, 6, promos.getMaxRows() - 1, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(['Yes', 'No'], true).build());
  promos.getRange(2, 3, promos.getMaxRows() - 1, 2).setNumberFormat('mmm d, yyyy');

  var sh = ss.getSheetByName(SHEET_NAME) || ss.insertSheet(SHEET_NAME);
  sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
  sh.setFrozenRows(1);
  var rows = sh.getMaxRows() - 1;
  sh.getRange(2, COL.status, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(STATUSES, true).build());
  sh.getRange(2, COL.unitId, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInRange(units.getRange('A2:A'), true).build());
  sh.getRange(2, 2, rows, 1).setNumberFormat('mmm d, yyyy h:mm am/pm');
  sh.getRange(2, COL.pickup, rows, 2).setNumberFormat('ddd mmm d, yyyy h:mm am/pm');
  sh.getRange(2, 13, rows, 1).setNumberFormat('@');
  sh.getRange(2, 18, rows, 1).setNumberFormat('@');
  photoFolder_();                    // asks for Drive access now, so photo uploads work later
  MailApp.getRemainingDailyQuota();  // asks for email access now, so booking emails work later
  var daily = ScriptApp.getProjectTriggers().some(function (t) { return t.getHandlerFunction() === 'updateDashboard'; });
  if (!daily) ScriptApp.newTrigger('updateDashboard').timeBased().everyDays(1).atHour(6).create();
  ensureSetupCode_();
  CacheService.getScriptCache().remove('config');
  updateDashboard();
}

function addTab_(ss, name, headers, rows) {
  if (ss.getSheetByName(name)) return;
  var sh = ss.insertSheet(name);
  sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
  if (rows.length) sh.getRange(2, 1, rows.length, rows[0].length).setValues(rows);
  sh.setFrozenRows(1);
  sh.autoResizeColumns(1, headers.length);
}

/* A Settings tab from an older version gets the settings it doesn't have yet, with their defaults. */
function addMissingSettings_(sh, defaults) {
  var have = {};
  if (sh.getLastRow() > 1) sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues().forEach(function (r) { have[String(r[0]).trim()] = 1; });
  defaults.forEach(function (r) { if (!have[r[0]]) sh.appendRow(r); });
}

/* ---- owner settings ---- */

function settings_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SETTINGS_TAB), out = {};
  DEFAULT_SETTINGS.forEach(function (r) { out[r[0]] = r[1]; });
  if (!sh || sh.getLastRow() < 2) return out;
  sh.getRange(2, 1, sh.getLastRow() - 1, 2).getValues().forEach(function (r) {
    var k = String(r[0]).trim(); if (k) out[k] = r[1];
  });
  return out;
}

/* Writes one setting's value, adding its row if the Settings tab doesn't have it. */
function saveSetting_(key, value) {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SETTINGS_TAB);
  if (!sh) { setup(); sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SETTINGS_TAB); }
  var keys = sh.getLastRow() > 1 ? sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues() : [];
  for (var i = 0; i < keys.length; i++) {
    if (String(keys[i][0]).trim() === key) { sh.getRange(i + 2, 2).setValue(value); return; }
  }
  var def = DEFAULT_SETTINGS.filter(function (r) { return r[0] === key; })[0];
  sh.appendRow([key, value, def ? def[2] : '']);
}

function list_(v) { return String(v == null ? '' : v).split(',').map(function (s) { return s.trim(); }).filter(Boolean); }
function clamp_(n, lo, hi, dflt) {
  if (n === '' || n == null) return dflt;
  n = Number(n);
  return isNaN(n) ? dflt : Math.min(hi, Math.max(lo, n));
}

/* Google Drive links become direct image links; other links are used as they are. */
function imageUrl_(v) {
  var s = String(v == null ? '' : v).trim(), m = s.match(/\/d\/([\w-]{20,})/) || s.match(/[?&]id=([\w-]{20,})/);
  if (m && /google\.com/.test(s)) return 'https://lh3.googleusercontent.com/d/' + m[1] + '=w2400';
  return /^https:\/\//.test(s) ? s : '';
}
function driveId_(v) {
  var m = String(v || '').match(/\/d\/([\w-]{20,})/) || String(v || '').match(/[?&]id=([\w-]{20,})/);
  return m ? m[1] : '';
}
/* "Rate card images": one link per line (or comma-separated). */
function links_(v) { return String(v == null ? '' : v).split(/[\s,]+/).filter(function (s) { return /^https:\/\//.test(s); }); }

/* "50% 50%", "30 70", "top", "left bottom" -> { x, y } in percent. */
function position_(v) {
  var s = String(v == null ? '' : v).toLowerCase(), n = s.match(/-?\d+(\.\d+)?/g) || [], x = 50, y = 50;
  if (n.length) { x = Number(n[0]); if (n.length > 1) y = Number(n[1]); }
  else {
    if (/left/.test(s)) x = 0; if (/right/.test(s)) x = 100;
    if (/top/.test(s)) y = 0; if (/bottom/.test(s)) y = 100;
  }
  return { x: clamp_(x, 0, 100, 50), y: clamp_(y, 0, 100, 50) };
}
/* "Auto" -> 0 (the picture's own shape); "3:1", "16/9", "2.5" -> width divided by height. */
function shape_(v) {
  var s = String(v == null ? '' : v).trim().toLowerCase(), m = s.match(/^(\d+(?:\.\d+)?)\s*[:\/x]\s*(\d+(?:\.\d+)?)$/);
  var r = m ? Number(m[1]) / Number(m[2]) : Number(s);
  return r > 0 ? Math.min(8, Math.max(0.5, r)) : 0;
}

/* Units from the Units tab (all of them, including hidden ones). */
function units_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(UNITS_TAB);
  var rows = sh && sh.getLastRow() > 1 ? sh.getRange(2, 1, sh.getLastRow() - 1, UNIT_HEADERS.length).getValues() : DEFAULT_UNITS;
  return rows.map(function (r, i) {
    return { id: String(r[0]).trim(), name: String(r[1]).trim(), detail: String(r[2]).trim(), rate: Number(r[3]) || null,
      poster: imageUrl_(r[4]), rawPoster: r[4], show: String(r[5]).trim().toLowerCase() !== 'no', monthName: String(r[6]).trim(), row: i + 2 };
  }).filter(function (u) { return u.id && u.name; });
}

function policies_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(POLICIES_TAB);
  var rows = sh && sh.getLastRow() > 1 ? sh.getRange(2, 1, sh.getLastRow() - 1, 2).getValues() : DEFAULT_POLICIES;
  var out = { operational: [], financial: [] };
  rows.forEach(function (r) {
    var text = String(r[1]).trim(); if (!text) return;
    (String(r[0]).trim().toLowerCase() === 'financial' ? out.financial : out.operational).push(text);
  });
  return out;
}

/* Delivery areas and fees from the Delivery tab. */
function delivery_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(DELIVERY_TAB);
  if (!sh || sh.getLastRow() < 2) return [];
  return sh.getRange(2, 1, sh.getLastRow() - 1, 2).getValues().map(function (r) {
    return { area: String(r[0]).trim(), fee: Math.max(0, Number(r[1]) || 0) };
  }).filter(function (a) { return a.area; });
}

/* What the booking page needs, kept for a minute so the page loads quickly. No private details. */
function config_() {
  var cache = CacheService.getScriptCache(), hit = cache.get('config');
  if (hit) return JSON.parse(hit);
  var s = settings_(), pol = policies_(), pos = position_(s['Banner position']);
  var c = {
    name: String(s['Shop name']).trim(), area: String(s['Area']).trim(), pickupPlace: String(s['Pick-up place']).trim(),
    mobile: String(s['Mobile']).replace(/[^\d+]/g, ''), messenger: String(s['Messenger page']).trim(),
    facebookUrl: String(s['Facebook page link']).trim(), banner: imageUrl_(s['Banner image']),
    bannerZoom: clamp_(s['Banner zoom (%)'], 25, 400, 100), bannerX: pos.x, bannerY: pos.y, bannerShape: shape_(s['Banner shape']),
    rateCards: links_(s['Rate card images']).map(imageUrl_).filter(Boolean),
    brandColor: /^#[0-9a-f]{6}$/i.test(String(s['Brand colour']).trim()) ? String(s['Brand colour']).trim() : '',
    deposit: Number(s['Deposit']) || 0, hoursPerDay: Number(s['Hours per rental day']) || 24,
    lateFeePerHour: Number(s['Late fee per hour']) || 0, handover: list_(s['Hand-over options']),
    inclusions: list_(s['Included with every rental']), minNoticeHours: Number(s['Minimum notice (hours)']) || 0,
    maxDaysAhead: Number(s['Book up to (days ahead)']) || 365, closedDates: list_(s['Closed dates']).filter(function (d) { return /^\d{4}-\d{2}-\d{2}$/.test(d); }),
    units: units_().filter(function (u) { return u.show; }).map(function (u) { return { id: u.id, name: u.name, detail: u.detail, rate: u.rate, poster: u.poster }; }),
    policies: pol.operational, liabilities: pol.financial,
    delivery: delivery_(), promos: promoRows_().some(function (p) { return p.active; })
  };
  if (!c.handover.length) c.handover = ['Pick up'];
  cache.put('config', JSON.stringify(c), 60);
  return c;
}

/** Menu item: lets anyone with the link view the Drive images used for the banner, rate cards and posters. */
function publishImages() {
  var s = settings_();
  var links = [s['Banner image']].concat(links_(s['Rate card images']), units_().map(function (u) { return u.rawPoster; }));
  var done = 0, failed = 0, seen = {};
  links.forEach(function (v) {
    var id = driveId_(v);
    if (!id || seen[id]) return; seen[id] = 1;
    try { DriveApp.getFileById(id).setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW); done++; } catch (e) { failed++; }
  });
  CacheService.getScriptCache().remove('config');
  SpreadsheetApp.getActiveSpreadsheet().toast(done + ' image(s) made public' + (failed ? ', ' + failed + ' could not be changed (check you own them)' : '') + '.');
}

/* ---- images: upload from the sheet, and the banner adjuster ---- */

function esc_(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return '&#' + c.charCodeAt(0) + ';'; }); }

/** Menu item: upload a picture from this computer or phone as the banner, a rate card or a unit's poster. */
function showUpload() {
  var opts = '<option value="banner">Banner (top of the booking page)</option>' +
    '<option value="rates">Rate card (added to the rate pictures)</option>' +
    '<option value="rates-replace">Rate card (replaces all rate pictures)</option>' +
    units_().map(function (u) { return '<option value="unit:' + esc_(u.id) + '">Poster: ' + esc_(u.name + (u.detail ? ' · ' + u.detail : '')) + '</option>'; }).join('');
  SpreadsheetApp.getUi().showModalDialog(HtmlService.createHtmlOutput(UPLOAD_HTML.replace('{{OPTIONS}}', function () { return opts; })).setWidth(440).setHeight(520), 'Upload an image');
}

/* Called by the upload dialog: saves the picture to the shop's Drive folder, makes it viewable by link
   and puts it where it was meant to go. */
function uploadImage(target, name, base64) {
  var bytes = Utilities.base64Decode(String(base64 || ''));
  if (!bytes.length) throw new Error('That file is empty.');
  if (bytes.length > 15e6) throw new Error('That picture is too big. Use one under 15 MB.');
  var folder = imageFolder_();
  var file = folder.createFile(Utilities.newBlob(bytes, 'image/jpeg', String(name || 'image').replace(/\.\w+$/, '').slice(0, 80) + '.jpg'));
  var shared = true;
  try { file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW); } catch (e) { shared = false; }
  var link = file.getUrl(), where;
  if (target === 'banner') { saveSetting_('Banner image', link); where = 'the banner'; }
  else if (target === 'rates' || target === 'rates-replace') {
    var now = target === 'rates' ? links_(settings_()['Rate card images']) : [];
    saveSetting_('Rate card images', now.concat([link]).join('\n'));
    where = target === 'rates' ? 'rate picture ' + (now.length + 1) : 'the only rate picture';
  } else if (/^unit:/.test(target)) {
    var id = target.slice(5), u = units_().filter(function (x) { return x.id === id; })[0];
    if (!u) throw new Error('That unit is no longer in the Units tab.');
    SpreadsheetApp.getActiveSpreadsheet().getSheetByName(UNITS_TAB).getRange(u.row, 5).setValue(link);
    where = 'the poster for ' + u.name + (u.detail ? ' · ' + u.detail : '');
  } else throw new Error('Choose where the picture goes.');
  CacheService.getScriptCache().remove('config');
  return 'Saved as ' + where + '. The booking page shows it within a minute.' +
    (shared ? '' : ' Google wouldn\'t make it public, so the website may not show it. Check your Drive sharing settings.');
}

function imageFolder_() {
  var name = String(settings_()['Shop name'] || 'Rental').trim() + ' website images';
  var found = DriveApp.getFoldersByName(name);
  return found.hasNext() ? found.next() : DriveApp.createFolder(name);
}

/** Menu item: zoom, move and resize the banner with sliders, seeing it as renters will. */
function showBannerEditor() {
  var s = settings_(), pos = position_(s['Banner position']);
  var data = { src: imageUrl_(s['Banner image']), zoom: clamp_(s['Banner zoom (%)'], 25, 400, 100), x: pos.x, y: pos.y, shape: shape_(s['Banner shape']) };
  var json = JSON.stringify(data).replace(/</g, '\\u003c'), html = BANNER_HTML.replace('{{DATA}}', function () { return json; });
  SpreadsheetApp.getUi().showSidebar(HtmlService.createHtmlOutput(html).setTitle('Adjust banner'));
}

/* Called by the banner adjuster. shape 0 means Auto. */
function saveBanner(zoom, x, y, shape) {
  var r = Number(shape) || 0;
  saveSetting_('Banner zoom (%)', clamp_(zoom, 25, 400, 100));
  saveSetting_('Banner position', clamp_(x, 0, 100, 50) + '% ' + clamp_(y, 0, 100, 50) + '%');
  saveSetting_('Banner shape', r > 0 ? ratioText_(r) : 'Auto');
  CacheService.getScriptCache().remove('config');
  return 'Saved. The booking page updates within a minute.';
}
function ratioText_(r) {
  var named = { 4: '4:1', 3: '3:1', 2.5: '5:2', 2: '2:1', 1.7778: '16:9', 1.5: '3:2', 1.3333: '4:3', 1: '1:1' };
  var k = Math.round(r * 10000) / 10000;
  return named[k] || (Math.round(r * 100) / 100) + ':1';
}

var UPLOAD_HTML = '<!doctype html><html><head><base target="_top"><style>' +
  'body{font:14px/1.45 Arial,sans-serif;margin:0;padding:4px 2px;color:#222}label{display:block;font-weight:bold;margin:12px 0 4px}' +
  'select,input{width:100%;box-sizing:border-box;font:inherit;padding:7px}#pv{display:block;max-width:100%;max-height:200px;margin:12px auto 0;border-radius:6px}' +
  'button{margin-top:14px;width:100%;padding:10px;font:inherit;font-weight:bold;background:#1a73e8;color:#fff;border:0;border-radius:6px;cursor:pointer}' +
  'button:disabled{opacity:.6}#out{margin-top:10px;min-height:2.8em}.hint{color:#666;font-size:12.5px}</style></head><body>' +
  '<label for="where">Where does it go?</label><select id="where">{{OPTIONS}}</select>' +
  '<label for="file">Picture</label><input type="file" id="file" accept="image/*">' +
  '<div class="hint">Large pictures are resized to 2400 px on the long side. A banner looks best about 3 times wider than tall.</div>' +
  '<img id="pv" hidden alt=""><button id="go">Upload</button><div id="out"></div>' +
  '<script>' +
  'var $=function(i){return document.getElementById(i)};' +
  '$("file").onchange=function(){var f=this.files[0];if(f){$("pv").src=URL.createObjectURL(f);$("pv").hidden=false;$("out").textContent="";}};' +
  '$("go").onclick=function(){var f=$("file").files[0];if(!f){$("out").textContent="Choose a picture first.";return;}' +
  '$("go").disabled=true;$("out").textContent="Uploading…";var img=new Image();' +
  'img.onload=function(){var k=Math.min(1,2400/Math.max(img.naturalWidth,img.naturalHeight)),c=document.createElement("canvas");' +
  'c.width=Math.round(img.naturalWidth*k);c.height=Math.round(img.naturalHeight*k);var x=c.getContext("2d");x.fillStyle="#fff";x.fillRect(0,0,c.width,c.height);' +
  'x.drawImage(img,0,0,c.width,c.height);var data=c.toDataURL("image/jpeg",0.9).split(",")[1];' +
  'google.script.run.withSuccessHandler(function(m){$("out").textContent=m;$("go").disabled=false;$("file").value="";})' +
  '.withFailureHandler(function(e){$("out").textContent="Upload failed: "+e.message;$("go").disabled=false;}).uploadImage($("where").value,f.name,data);};' +
  'img.onerror=function(){$("out").textContent="That file isn\'t a picture this browser can open. Try a JPG or PNG.";$("go").disabled=false;};' +
  'img.src=URL.createObjectURL(f);};' +
  '</script></body></html>';

/* The preview draws the banner exactly as the booking page does: the box has the banner's shape,
   the picture covers it, then zooms around the chosen point. */
var BANNER_HTML = '<!doctype html><html><head><base target="_top"><style>' +
  'body{font:13.5px/1.45 Arial,sans-serif;margin:0;padding:10px;color:#222}' +
  '.box{position:relative;overflow:hidden;background:#7C0507;border-radius:6px;aspect-ratio:3/1}' +
  '.box img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}' +
  'label{display:flex;justify-content:space-between;font-weight:bold;margin:14px 0 2px}label span{font-weight:normal;color:#555}' +
  'input[type=range],select{width:100%}button{margin-top:16px;width:100%;padding:9px;font:inherit;font-weight:bold;border-radius:6px;cursor:pointer}' +
  '#save{background:#1a73e8;color:#fff;border:0}#reset{background:#fff;border:1px solid #bbb}#out{margin-top:10px;min-height:2.8em}.hint{color:#666;font-size:12px;margin-top:6px}' +
  '</style></head><body>' +
  '<div id="none" hidden>There is no banner yet. Use the menu: <b>Upload an image</b>, then open this again.</div>' +
  '<div id="ui"><div class="box" id="box"><img id="img" alt=""></div>' +
  '<div class="hint">This is how the top of the booking page looks. On wider screens it is the same shape, just bigger.</div>' +
  '<label for="zoom">Zoom <span id="zv"></span></label><input type="range" id="zoom" min="50" max="300" step="5">' +
  '<label for="x">Left – right <span id="xv"></span></label><input type="range" id="x" min="0" max="100" step="1">' +
  '<label for="y">Up – down <span id="yv"></span></label><input type="range" id="y" min="0" max="100" step="1">' +
  '<label for="shape">Size (shape)</label><select id="shape"><option value="0">Auto: the picture\'s own shape</option>' +
  '<option value="4">4:1 · short</option><option value="3">3:1 · standard</option><option value="2.5">5:2</option><option value="2">2:1 · tall</option>' +
  '<option value="1.7778">16:9 · taller</option><option value="1.5">3:2</option><option value="1.3333">4:3</option><option value="1">1:1 · square</option></select>' +
  '<button id="save">Save banner</button><button id="reset">Reset to normal</button><div id="out"></div></div>' +
  '<script>' +
  'var D={{DATA}},$=function(i){return document.getElementById(i)},nat=3;' +
  'if(!D.src){$("ui").hidden=true;$("none").hidden=false;}' +
  'function addShape(v){if(!v||[].some.call($("shape").options,function(o){return Math.abs(o.value-v)<0.001}))return;' +
  'var o=document.createElement("option");o.value=v;o.textContent=(Math.round(v*100)/100)+":1";$("shape").appendChild(o);}' +
  'addShape(D.shape);' +
  'function pick(v){[].forEach.call($("shape").options,function(o){if(Math.abs(o.value-v)<0.001)$("shape").value=o.value;});}' +
  'function set(z,x,y,s){$("zoom").value=z;$("x").value=x;$("y").value=y;pick(s);draw();}' +
  'function draw(){var z=+$("zoom").value,x=+$("x").value,y=+$("y").value,s=+$("shape").value;' +
  '$("zv").textContent=z+"%";$("xv").textContent=x+"%";$("yv").textContent=y+"%";' +
  '$("box").style.aspectRatio=String(s||nat);var i=$("img").style;i.objectPosition=x+"% "+y+"%";i.transformOrigin=x+"% "+y+"%";i.transform="scale("+z/100+")";$("out").textContent="";}' +
  '$("img").onload=function(){nat=this.naturalWidth/this.naturalHeight||3;draw();};$("img").src=D.src||"";' +
  '["zoom","x","y","shape"].forEach(function(k){$(k).oninput=draw;$(k).onchange=draw;});' +
  '$("reset").onclick=function(){set(100,50,50,0);};' +
  '$("save").onclick=function(){$("save").disabled=true;$("out").textContent="Saving…";' +
  'google.script.run.withSuccessHandler(function(m){$("out").textContent=m;$("save").disabled=false;})' +
  '.withFailureHandler(function(e){$("out").textContent="Couldn\'t save: "+e.message;$("save").disabled=false;})' +
  '.saveBanner(+$("zoom").value,+$("x").value,+$("y").value,+$("shape").value);};' +
  'set(D.zoom,D.x,D.y,D.shape);' +
  '</script></body></html>';

/* ---- promo codes and prices ---- */

/* Promo rows: code, discount (10% or 100 pesos), dates and limits. */
function promoRows_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(PROMOS_TAB);
  if (!sh || sh.getLastRow() < 2) return [];
  return sh.getRange(2, 1, sh.getLastRow() - 1, PROMO_HEADERS.length).getValues().map(function (r) {
    var d = String(r[1]).trim(), pct = /%$/.test(d) || (typeof r[1] === 'number' && r[1] > 0 && r[1] < 1);
    var n = typeof r[1] === 'number' ? r[1] : Number(d.replace(/[^\d.]/g, ''));
    if (typeof r[1] === 'number' && r[1] > 0 && r[1] < 1) n = r[1] * 100;   // a cell formatted as a percent holds 0.1
    return { code: String(r[0]).trim().toUpperCase(), percent: pct ? Math.min(100, n) : 0, amount: pct ? 0 : n,
      from: r[2] instanceof Date ? fmt_(r[2], 'yyyy-MM-dd') : '', until: r[3] instanceof Date ? fmt_(r[3], 'yyyy-MM-dd') : '',
      max: Number(r[4]) || 0, active: String(r[5]).trim().toLowerCase() === 'yes' };
  }).filter(function (p) { return p.code && (p.percent || p.amount); });
}

/* Whether a code can be used today: { ok, code, percent, amount } or { ok: false, error }. */
function promo_(code) {
  code = String(code || '').trim().toUpperCase();
  var p = promoRows_().filter(function (x) { return x.code === code; })[0], today = fmt_(new Date(), 'yyyy-MM-dd');
  if (!p || !p.active) return { ok: false, error: 'not_found' };
  if (p.from && today < p.from) return { ok: false, error: 'not_started' };
  if (p.until && today > p.until) return { ok: false, error: 'expired' };
  if (p.max) {
    var sh = sheet_(), last = sh.getLastRow(), used = 0;
    if (last > 1) sh.getRange(2, 1, last - 1, COL.promo).getValues().forEach(function (r) {
      if (plain_(r[COL.promo - 1]).toUpperCase() === code && String(r[COL.status - 1]).trim() !== 'Cancelled') used++;
    });
    if (used >= p.max) return { ok: false, error: 'used_up' };
  }
  return { ok: true, code: p.code, percent: p.percent, amount: p.amount };
}

/* The booking's price, worked out here so the sheet's total can be trusted:
   rental days x rate, less the promo, plus the delivery fee. */
function quote_(c, unitId, start, end, promoCode, area, delivering) {
  var u = c.units.filter(function (x) { return x.id === unitId; })[0];
  var days = Math.max(1, Math.ceil((end - start) / 36e5 / c.hoursPerDay - 1e-9));
  var rental = u && u.rate ? u.rate * days : 0, discount = 0, fee = 0, code = '';
  if (promoCode) {
    var p = promo_(promoCode);
    if (!p.ok) return { error: 'promo', reason: p.error };
    code = p.code;
    discount = p.percent ? Math.round(rental * p.percent / 100) : Math.min(p.amount, rental);
  }
  if (delivering && (c.delivery || []).length) {
    var a = c.delivery.filter(function (x) { return x.area === String(area || ''); })[0];
    if (!a) return { error: 'area' };
    fee = a.fee;
  }
  return { days: days, rental: rental, code: code, discount: discount, fee: fee, total: rental - discount + fee };
}

/* ---- bookings ---- */

function photoFolder_() {
  var name = String(settings_()['Shop name'] || 'Rental').trim() + ' booking requirements';
  var found = DriveApp.getFoldersByName(name);
  return found.hasNext() ? found.next() : DriveApp.createFolder(name);
}

function sheet_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME);
  if (!sh) { setup(); sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME); }
  return sh;
}

/** Booked slots that still block a unit: Pending or Confirmed, and not yet over. */
function busy_() {
  var sh = sheet_(), last = sh.getLastRow();
  if (last < 2) return [];
  var rows = sh.getRange(2, 1, last - 1, COL.ret).getValues(), now = Date.now(), out = [];
  rows.forEach(function (r) {
    var status = String(r[COL.status - 1]).trim(), unit = String(r[COL.unitId - 1]).trim();
    var start = r[COL.pickup - 1], end = r[COL.ret - 1];
    if (BLOCKING.indexOf(status) < 0 || !unit) return;
    if (!(start instanceof Date) || !(end instanceof Date) || end.getTime() < now) return;
    out.push({ unit: unit, start: start.toISOString(), end: end.toISOString() });
  });
  return out;
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

/* Text from the page is stored as plain text: a leading ' stops Sheets from
   turning 0917… into a number or running anything that starts with =. */
function text_(v) { return "'" + String(v == null ? '' : v).slice(0, 500); }
function digits_(v) { return String(v == null ? '' : v).replace(/\D/g, '').slice(-10); }

function doGet(e) {
  var p = (e && e.parameter) || {};
  if (p.ref) return json_(lookup_(String(p.ref).trim().toUpperCase(), p.mobile));
  if (p.promo) return json_(promo_(p.promo));
  return json_({ ok: true, busy: busy_(), config: config_() });
}

/* A renter checking their booking: only answers when the ref and mobile number match. */
function lookup_(ref, mobile) {
  var sh = sheet_(), last = sh.getLastRow();
  if (last < 2 || digits_(mobile).length < 10) return { ok: false, error: 'not_found' };
  var rows = sh.getRange(2, 1, last - 1, 13).getValues();
  for (var i = rows.length - 1; i >= 0; i--) {
    var r = rows[i];
    if (plain_(r[0]).toUpperCase() !== ref || digits_(plain_(r[12])) !== digits_(mobile)) continue;
    return { ok: true, ref: ref, status: String(r[COL.status - 1]).trim() || 'Pending', unit: plain_(r[4]),
      start: r[5] instanceof Date ? r[5].toISOString() : '', end: r[6] instanceof Date ? r[6].toISOString() : '' };
  }
  return { ok: false, error: 'not_found' };
}

function doPost(e) {
  var d;
  try { d = JSON.parse(e.postData.contents); } catch (err) { return json_({ ok: false, error: 'bad_request' }); }
  if (d.action === 'admin') return json_(admin_(d));
  if (d.website) return json_({ ok: true, ref: d.ref });   // filled-in hidden field: a bot, not a renter
  if (d.action === 'photos') return savePhotos_(d);
  var c = config_(), start = new Date(d.start), end = new Date(d.end);
  var ids = c.units.map(function (u) { return u.id; });
  if (ids.indexOf(d.unitId) < 0 || isNaN(start) || isNaN(end) || end <= start || !d.name || !d.mobile) {
    return json_({ ok: false, error: 'missing' });
  }
  if (start.getTime() < Date.now() + c.minNoticeHours * 36e5 - 15 * 6e4) return json_({ ok: false, error: 'too_soon' });
  if (c.closedDates.indexOf(fmt_(start, 'yyyy-MM-dd')) > -1 || c.closedDates.indexOf(fmt_(end, 'yyyy-MM-dd')) > -1) {
    return json_({ ok: false, error: 'closed' });
  }
  var lock = LockService.getScriptLock(), q;
  lock.waitLock(20000);
  try {
    var taken = busy_().filter(function (b) {
      return b.unit === d.unitId && start < new Date(b.end) && new Date(b.start) < end;
    })[0];
    if (taken) return json_({ ok: false, error: 'taken', until: taken.end, busy: busy_() });
    q = quote_(c, d.unitId, start, end, d.promo, d.area, !!d.delivering);   // inside the lock, so a promo's last use can't go twice
    if (q.error) return json_({ ok: false, error: q.error, reason: q.reason });
    sheet_().appendRow([text_(d.ref), new Date(), 'Pending', d.unitId, text_(d.unit), start, end, q.days,
      q.total || '', text_(d.handover), text_(d.name), text_(d.address), text_(d.mobile), text_(d.social),
      text_(d.purpose), text_(d.ecName), text_(d.ecRel), text_(d.ecMobile), text_(d.ecAddress), text_(d.notes), '', '',
      q.code ? text_(q.code) : '', q.discount || '', q.fee || '']);
  } finally {
    lock.releaseLock();
  }
  notify_(d, start, end, c, q);
  return json_({ ok: true, ref: d.ref, total: q.total, discount: q.discount, fee: q.fee, busy: busy_() });
}

/* Emails the owner about a new booking. A failed email never stops the booking. */
function notify_(d, start, end, c, q) {
  try {
    var to = String(settings_()['Notify email'] || '').trim();
    if (!to) return;
    var url = SpreadsheetApp.getActiveSpreadsheet().getUrl();
    MailApp.sendEmail(to, 'New booking ' + d.ref + ' · ' + d.unit + ' · ' + fmt_(start, 'MMM d, h:mm a'),
      'New website booking for ' + c.name + ' (Pending)\n\n' +
      'Ref: ' + d.ref + '\nUnit: ' + d.unit + '\nPick-up: ' + fmt_(start, 'EEE MMM d, h:mm a') + '\nReturn: ' + fmt_(end, 'EEE MMM d, h:mm a') +
      '\nDays: ' + q.days + (q.rental ? '\nRental: ₱' + q.rental : '') +
      (q.discount ? '\nPromo ' + q.code + ': -₱' + q.discount : '') + (q.fee ? '\nDelivery fee: ₱' + q.fee : '') +
      (q.total ? '\nTotal: ₱' + q.total : '') + '\nHand-over: ' + d.handover +
      '\n\nName: ' + d.name + '\nMobile: ' + d.mobile + '\nSocial: ' + d.social + '\nPurpose: ' + d.purpose +
      '\nEmergency: ' + d.ecName + ' (' + d.ecRel + '), ' + d.ecMobile +
      (d.notes ? '\nNotes: ' + d.notes : '') +
      '\n\nThe requirement photos upload to Google Drive in a moment; the link appears in the Photos column.\n' +
      'Open the sheet to confirm or cancel: ' + url);
  } catch (err) {}
}

/* Saves up to 3 photos (base64) for a booking that has none yet, in its own Drive folder. */
function savePhotos_(d) {
  var files = (d.files || []).slice(0, 3);
  if (!d.ref || !files.length) return json_({ ok: false, error: 'missing' });
  var sh = sheet_(), last = sh.getLastRow(), row = -1;
  if (last >= 2) {
    var refs = sh.getRange(2, 1, last - 1, 1).getValues();
    for (var i = refs.length - 1; i >= 0; i--) { if (plain_(refs[i][0]) === d.ref) { row = i + 2; break; } }
  }
  if (row < 0) return json_({ ok: false, error: 'not_found' });
  if (sh.getRange(row, COL.photos).getValue()) return json_({ ok: false, error: 'exists' });
  var folder = photoFolder_().createFolder(d.ref + ' ' + plain_(sh.getRange(row, 11).getValue()));
  files.forEach(function (f, n) {
    var bytes = Utilities.base64Decode(String(f.data || ''));
    if (!bytes.length || bytes.length > 8e6) return;
    var label = String(f.label || 'Photo ' + (n + 1)).replace(/[\\\/:*?"<>|]/g, '').slice(0, 80);
    folder.createFile(Utilities.newBlob(bytes, /^image\//.test(f.type) ? f.type : 'image/jpeg', label + '.jpg'));
  });
  sh.getRange(row, COL.photos).setValue(folder.getUrl());
  return json_({ ok: true });
}

/** Runs when the sheet is edited: owner-tab edits refresh the website; a booking set to Confirmed goes
    into its month tab (once); a status change refreshes the dashboard. */
function onEdit(e) {
  var range = e && e.range;
  if (!range) return;
  var name = range.getSheet().getName();
  if (OWNER_TABS.indexOf(name) > -1) { CacheService.getScriptCache().remove('config'); return; }
  if (name !== SHEET_NAME) return;
  if (range.getColumn() > COL.status || range.getLastColumn() < COL.status) return;
  var sh = range.getSheet();
  for (var row = Math.max(2, range.getRow()); row <= range.getLastRow(); row++) {
    var r = sh.getRange(row, 1, 1, HEADERS.length).getValues()[0];
    if (String(r[COL.status - 1]).trim() !== 'Confirmed' || r[COL.addedTo - 1]) continue;
    var tab = addToMonth_(r);
    if (tab) sh.getRange(row, COL.addedTo).setValue(tab);
  }
  try { updateDashboard(); } catch (err) {}
}

/** Adds the shop's menu to the sheet. */
function onOpen() {
  SpreadsheetApp.getUi().createMenu(String(settings_()['Shop name'] || 'Rentals').trim() || 'Rentals')
    .addItem('Upload an image (banner, rate card or poster)', 'showUpload')
    .addItem('Adjust banner (zoom, position, size)', 'showBannerEditor')
    .addItem('Update earnings dashboard', 'updateDashboard')
    .addItem('Show owner setup code', 'showSetupCode')
    .addItem('Remove all owner page accounts (forgot password)', 'resetOwnerAccounts')
    .addSeparator()
    .addItem('Add confirmed website bookings to month tabs', 'addConfirmed')
    .addItem('Make banner and poster images public', 'publishImages')
    .addToUi();
}

/** Menu item: does what onEdit does, for every Confirmed row not added yet (e.g. confirmed from the phone app). */
function addConfirmed() {
  var sh = sheet_(), last = sh.getLastRow(), added = 0;
  if (last < 2) return;
  var rows = sh.getRange(2, 1, last - 1, HEADERS.length).getValues();
  rows.forEach(function (r, i) {
    if (String(r[COL.status - 1]).trim() !== 'Confirmed' || r[COL.addedTo - 1]) return;
    var tab = addToMonth_(r);
    if (tab) { sh.getRange(i + 2, COL.addedTo).setValue(tab); added++; }
  });
  SpreadsheetApp.getActiveSpreadsheet().toast(added ? added + ' booking(s) added to the month tabs.' : 'Nothing new to add.');
}

/* ---- owner app (rent/admin/) ----
   The owner registers an admin name and password on the owner page. Registering needs the shop's
   "Owner setup code" from the Settings tab, which only people who can open the sheet can see, so a
   stranger with the link can't make an account. Staff can register too, with the same code; change
   the code to stop new sign-ups. Accounts are kept in the script's properties (not in any tab), with
   only a salted hash of each password. A sign-in gives the device a token that lasts 60 days. Too many
   wrong passwords or codes lock sign-in and registering for 15 minutes. */

var TOKEN_DAYS = 60, MAX_FAILS = 8, SETUP_CODE = 'Owner setup code';
var TEXT_FIELDS = ['handover', 'name', 'address', 'mobile', 'social', 'purpose', 'ecName', 'ecRel', 'ecMobile', 'ecAddress', 'notes'];
var FIELD_COL = { ref: 1, submitted: 2, status: 3, unitId: 4, unit: 5, start: 6, end: 7, days: 8, total: 9, handover: 10, name: 11,
  address: 12, mobile: 13, social: 14, purpose: 15, ecName: 16, ecRel: 17, ecMobile: 18, ecAddress: 19, notes: 20, addedTo: 21,
  photos: 22, promo: 23, discount: 24, fee: 25 };
var OWNER_DISCOUNT = 'Owner discount';

function newCode_() {
  var abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789', r = '';
  for (var i = 0; i < 8; i++) r += abc[Math.floor(Math.random() * abc.length)];
  return r;
}
/* The setup code from Settings; setup() makes one when it's blank. */
function setupCode_() { return String(settings_()[SETUP_CODE] || '').trim().toUpperCase(); }
function ensureSetupCode_() { if (!setupCode_()) saveSetting_(SETUP_CODE, newCode_()); }

/** Menu item: shows the code for registering on the owner page, with the option to make a new one. */
function showSetupCode() {
  var ui = SpreadsheetApp.getUi();
  ensureSetupCode_();
  var r = ui.alert('Owner setup code: ' + setupCode_(),
    'Type this code on the owner page (your booking link with /admin added) to register an admin name and password. ' +
    'Give it only to people who should see and edit your bookings.\n\nMake a new code? The old one stops working; ' +
    'accounts already made keep working.', ui.ButtonSet.YES_NO);
  if (r === ui.Button.YES) { saveSetting_(SETUP_CODE, newCode_()); ui.alert('New owner setup code: ' + setupCode_()); }
}

/** Menu item: for a forgotten password. Removes every owner page account and signs out every device. */
function resetOwnerAccounts() {
  var ui = SpreadsheetApp.getUi();
  if (ui.alert('Remove all owner page accounts?', 'Everyone is signed out, and you register again on the owner page ' +
    'with the owner setup code. Bookings are not touched.', ui.ButtonSet.YES_NO) !== ui.Button.YES) return;
  var props = PropertiesService.getScriptProperties();
  props.setProperty('adminAccounts', '{}'); props.setProperty('adminTokens', '{}');
  ui.alert('Done. Register again on the owner page.');
}

function hash_(text, salt) {
  return Utilities.base64Encode(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, salt + '|' + text, Utilities.Charset.UTF_8));
}
function prop_(props, key) { try { return JSON.parse(props.getProperty(key) || '{}'); } catch (e) { return {}; } }
function userKey_(name) { return String(name || '').trim().toLowerCase(); }

function admin_(d) {
  var props = PropertiesService.getScriptProperties(), accounts = prop_(props, 'adminAccounts');
  if (d.op === 'status') return { ok: true, hasAccounts: Object.keys(accounts).length > 0, name: config_().name };
  if (d.op === 'login' || d.op === 'register') {
    var cache = CacheService.getScriptCache(), fails = Number(cache.get('ownerFails')) || 0;
    if (fails >= MAX_FAILS) return { ok: false, error: 'locked' };
    var res = d.op === 'login' ? login_(accounts, d) : register_(props, accounts, d);
    if (res.fail) { cache.put('ownerFails', String(fails + 1), 900); return { ok: false, error: res.fail }; }
    if (res.error) return res;
    cache.remove('ownerFails');
    return { ok: true, token: newToken_(props, res.key), user: accounts[res.key].name, name: config_().name };
  }
  var tokens = prop_(props, 'adminTokens'), t = hash_(String(d.token || ''), 'token'), me = tokens[t];
  if (!d.token || !me || me.exp < Date.now() || !accounts[me.user]) return { ok: false, error: 'signed_out' };
  if (d.op === 'logout') { delete tokens[t]; props.setProperty('adminTokens', JSON.stringify(tokens)); return { ok: true }; }
  if (d.op === 'data') { var out = adminData_(); out.me = accounts[me.user].name; return out; }
  if (d.op === 'save') return adminSave_(d.booking || {});
  if (d.op === 'accounts') return accountsOut_(accounts, me.user);
  if (d.op === 'removeAccount') {
    var k = userKey_(d.username);
    if (k === me.user) return { ok: false, error: 'self' };
    delete accounts[k];
    Object.keys(tokens).forEach(function (x) { if (tokens[x].user === k) delete tokens[x]; });
    props.setProperty('adminAccounts', JSON.stringify(accounts)); props.setProperty('adminTokens', JSON.stringify(tokens));
    return accountsOut_(accounts, me.user);
  }
  if (d.op === 'changePassword') {
    var a = accounts[me.user];
    if (hash_(String(d.old || ''), a.salt) !== a.hash) return { ok: false, error: 'wrong_password' };
    if (String(d.password || '').length < 6) return { ok: false, error: 'short_password' };
    a.salt = Utilities.getUuid(); a.hash = hash_(String(d.password), a.salt);
    Object.keys(tokens).forEach(function (x) { if (tokens[x].user === me.user && x !== t) delete tokens[x]; });   // other devices sign in again
    props.setProperty('adminAccounts', JSON.stringify(accounts)); props.setProperty('adminTokens', JSON.stringify(tokens));
    return { ok: true };
  }
  return { ok: false, error: 'bad_request' };
}

function login_(accounts, d) {
  var k = userKey_(d.username), a = accounts[k];
  if (!a || hash_(String(d.password || ''), a.salt) !== a.hash) return { fail: 'wrong_login' };
  return { key: k };
}

function register_(props, accounts, d) {
  var name = String(d.username || '').trim(), k = userKey_(name), code = setupCode_();
  if (!code || String(d.code || '').trim().toUpperCase() !== code) return { fail: 'wrong_code' };
  if (!/^[A-Za-z0-9._-]{3,30}$/.test(name)) return { ok: false, error: 'bad_username' };
  if (String(d.password || '').length < 6) return { ok: false, error: 'short_password' };
  if (accounts[k]) return { ok: false, error: 'username_taken' };
  var salt = Utilities.getUuid();
  accounts[k] = { name: name, salt: salt, hash: hash_(String(d.password), salt), created: new Date().toISOString() };
  props.setProperty('adminAccounts', JSON.stringify(accounts));
  return { key: k };
}

function newToken_(props, key) {
  var tokens = prop_(props, 'adminTokens'), now = Date.now(), token = Utilities.getUuid() + Utilities.getUuid();
  Object.keys(tokens).forEach(function (x) { if (!tokens[x] || tokens[x].exp < now) delete tokens[x]; });
  var keys = Object.keys(tokens).sort(function (a, b) { return tokens[a].exp - tokens[b].exp; });
  while (keys.length >= 40) delete tokens[keys.shift()];   // keep the newest devices
  tokens[hash_(token, 'token')] = { exp: now + TOKEN_DAYS * 864e5, user: key };
  props.setProperty('adminTokens', JSON.stringify(tokens));
  return token;
}

function accountsOut_(accounts, me) {
  return { ok: true, accounts: Object.keys(accounts).map(function (k) {
    return { username: accounts[k].name, created: accounts[k].created, you: k === me };
  }) };
}

function iso_(v) { return v instanceof Date ? v.toISOString() : ''; }

/* One Website Bookings row as the owner app sees it. */
function bookingOf_(r, row) {
  var b = { row: row };
  Object.keys(FIELD_COL).forEach(function (k) {
    var v = r[FIELD_COL[k] - 1];
    b[k] = v instanceof Date ? v.toISOString() : typeof v === 'number' ? v : plain_(v);
  });
  b.status = String(b.status).trim() || 'Pending';
  return b;
}

/* Everything the owner app shows: every booking, every unit (hidden ones too) and the shop's rules. */
function adminData_() {
  var sh = sheet_(), last = sh.getLastRow();
  var rows = last > 1 ? sh.getRange(2, 1, last - 1, HEADERS.length).getValues() : [];
  var bookings = [];
  rows.forEach(function (r, i) { if (plain_(r[0]) || String(r[COL.unitId - 1]).trim()) bookings.push(bookingOf_(r, i + 2)); });
  var c = config_();
  return { ok: true, bookings: bookings,
    units: units_().map(function (u) { return { id: u.id, name: u.name, detail: u.detail, rate: u.rate, show: u.show }; }),
    shop: { name: c.name, brandColor: c.brandColor, hoursPerDay: c.hoursPerDay, deposit: c.deposit, handover: c.handover, delivery: c.delivery, sheetUrl: SpreadsheetApp.getActiveSpreadsheet().getUrl() } };
}

/* Saves an edited booking (b.row set) or adds a new one (b.row 0). Refused if another Pending or
   Confirmed booking has that unit at an overlapping time, or if the row has changed in the sheet. */
function adminSave_(b) {
  var units = units_(), u = units.filter(function (x) { return x.id === String(b.unitId || ''); })[0];
  var start = new Date(b.start), end = new Date(b.end), status = String(b.status || '');
  if (!u) return { ok: false, error: 'unit' };
  if (isNaN(start) || isNaN(end) || end <= start) return { ok: false, error: 'dates' };
  if (STATUSES.indexOf(status) < 0) return { ok: false, error: 'status' };
  if (!String(b.name || '').trim()) return { ok: false, error: 'name' };
  var money = function (v) { return v === '' || v == null ? '' : Math.max(0, Math.round(Number(v) || 0)); };
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sh = sheet_(), last = sh.getLastRow(), row = Number(b.row) || 0;
    var rows = last > 1 ? sh.getRange(2, 1, last - 1, HEADERS.length).getValues() : [];
    if (row && (!rows[row - 2] || plain_(rows[row - 2][0]) !== String(b.ref || ''))) return { ok: false, error: 'changed' };
    if (BLOCKING.indexOf(status) > -1) {
      for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        if (i + 2 === row || BLOCKING.indexOf(String(r[COL.status - 1]).trim()) < 0 || String(r[COL.unitId - 1]).trim() !== u.id) continue;
        if (r[COL.pickup - 1] instanceof Date && r[COL.ret - 1] instanceof Date && start < r[COL.ret - 1] && r[COL.pickup - 1] < end) {
          return { ok: false, error: 'taken', with: plain_(r[0]) + ' · ' + plain_(r[10]), until: iso_(r[COL.ret - 1]) };
        }
      }
    }
    var cur = row ? rows[row - 2] : [];
    var discount = money(b.discount), promo = row ? plain_(cur[COL.promo - 1]) : '';
    if (discount && !promo) promo = OWNER_DISCOUNT;
    if (!discount && promo === OWNER_DISCOUNT) promo = '';
    var text = function (k) { return text_(b[k] != null ? b[k] : plain_(cur[FIELD_COL[k] - 1])); };
    /* Status to Notes (columns 3-20), then Promo code, Discount and Delivery fee (23-25). Text is written as text. */
    var main = [status, u.id, text_(u.name + (u.detail ? ' (' + u.detail + ')' : '')), start, end,
      Math.max(1, Math.ceil((end - start) / 36e5 / hoursPerDay_() - 1e-9)), money(b.total)].concat(TEXT_FIELDS.map(text));
    var money3 = [promo ? text_(promo) : '', discount, money(b.fee)];
    if (!row) {
      sh.appendRow([text_(b.ref || newRef_()), new Date()].concat(main, ['', ''], money3));
      row = sh.getLastRow();
    } else {
      sh.getRange(row, COL.status, 1, main.length).setValues([main]);
      sh.getRange(row, COL.promo, 1, 3).setValues([money3]);
    }
    var vals = sh.getRange(row, 1, 1, HEADERS.length).getValues()[0];
    if (status === 'Confirmed' && !vals[COL.addedTo - 1]) {
      var tab = addToMonth_(vals);
      if (tab) sh.getRange(row, COL.addedTo).setValue(tab);
    }
    var saved = bookingOf_(sh.getRange(row, 1, 1, HEADERS.length).getValues()[0], row);
  } finally {
    lock.releaseLock();
  }
  try { updateDashboard(); } catch (err) {}
  return { ok: true, booking: saved };
}

function hoursPerDay_() { return Number(settings_()['Hours per rental day']) || 24; }

/* A reference for a booking added in the owner app: the shop's initials, a dash and 4 letters/digits. */
function newRef_() {
  var words = String(settings_()['Shop name'] || '').replace(/[^A-Za-z0-9 ]/g, ' ').trim().split(/\s+/).filter(Boolean);
  var p = (words.length > 1 ? words.map(function (w) { return w[0]; }).join('') : (words[0] || 'BKG')).slice(0, 3).toUpperCase();
  var abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789', r = '';
  for (var i = 0; i < 4; i++) r += abc[Math.floor(Math.random() * abc.length)];
  return p + '-' + r;
}

/* ---- earnings dashboard ---- */

/** Menu item (also runs every morning and when a booking's status changes): rebuilds the Dashboard tab.
    Earnings are Confirmed and Returned bookings in Website Bookings, by pick-up date: the rental after
    promo discounts, plus delivery fees. Deposits are not counted. */
function updateDashboard() {
  var ss = SpreadsheetApp.getActiveSpreadsheet(), src = sheet_(), last = src.getLastRow();
  var rows = last > 1 ? src.getRange(2, 1, last - 1, HEADERS.length).getValues() : [];
  var now = new Date(), month = fmt_(now, 'yyyy-MM'), year = month.slice(0, 4);
  var prev = monthKey_(month, -1), week = now.getTime() + 7 * 864e5;
  var byMonth = {}, byUnit = {}, byPromo = {}, tot = { month: [0, 0], prev: [0, 0], year: [0, 0], all: [0, 0] };
  var pending = 0, upcoming = [];
  rows.forEach(function (r) {
    var status = String(r[COL.status - 1]).trim(), start = r[COL.pickup - 1], end = r[COL.ret - 1];
    if (status === 'Pending') pending++;
    if (!(start instanceof Date)) return;
    if (BLOCKING.indexOf(status) > -1 && start.getTime() >= now.getTime() - 36e5 && start.getTime() <= week) {
      upcoming.push([start, end instanceof Date ? end : '', plain_(r[4]), plain_(r[10]), status, plain_(r[0])]);
    }
    if (EARNING.indexOf(status) < 0) return;
    var amt = Number(r[COL.estimate - 1]) || 0, m = fmt_(start, 'yyyy-MM'), unit = plain_(r[4]) || String(r[COL.unitId - 1]);
    var add = function (k) { tot[k][0]++; tot[k][1] += amt; };
    add('all');
    if (m.slice(0, 4) === year) add('year');
    if (m === month) add('month');
    if (m === prev) add('prev');
    byMonth[m] = byMonth[m] || [0, 0]; byMonth[m][0]++; byMonth[m][1] += amt;
    byUnit[unit] = byUnit[unit] || [0, 0, 0, 0]; byUnit[unit][0]++; byUnit[unit][1] += Number(r[COL.days - 1]) || 0; byUnit[unit][2] += amt;
    if (m === month) byUnit[unit][3] += amt;
    var code = plain_(r[COL.promo - 1]);
    if (code) { byPromo[code] = byPromo[code] || [0, 0]; byPromo[code][0]++; byPromo[code][1] += Number(r[COL.discount - 1]) || 0; }
  });

  var sh = ss.getSheetByName(DASH_TAB) || ss.insertSheet(DASH_TAB, 0);
  sh.clear();
  try { sh.getCharts().forEach(function (ch) { sh.removeChart(ch); }); } catch (err) {}
  var row = 1, money = [];
  var put = function (values, opts) {
    sh.getRange(row, 1, 1, values.length).setValues([values]);
    if (opts && opts.bold) sh.getRange(row, 1, 1, values.length).setFontWeight('bold');
    if (opts && opts.money) opts.money.forEach(function (c) { money.push([row, c]); });
    row++;
  };
  var title = function (t) { row++; put([t], { bold: true }); sh.getRange(row - 1, 1).setFontSize(13); };
  put([String(settings_()['Shop name'] || 'Shop').trim() + ' earnings'], { bold: true }); sh.getRange(1, 1).setFontSize(16);
  put(['Updated ' + fmt_(now, 'MMM d, yyyy h:mm a') + '. Confirmed and Returned bookings, by pick-up date: rental after discounts, plus delivery fees. Deposits not included.']);
  row++;
  put(['This month', 'Last month', 'This year', 'All time'], { bold: true });
  put([tot.month[1], tot.prev[1], tot.year[1], tot.all[1]], { money: [1, 2, 3, 4] });
  sh.getRange(row - 1, 1, 1, 4).setFontSize(15).setFontWeight('bold');
  put([count_(tot.month[0]), count_(tot.prev[0]), count_(tot.year[0]), count_(tot.all[0])]);
  row++;
  put(['Pending to review', pending], { bold: true });
  put(['Pick-ups in the next 7 days', upcoming.length], { bold: true });

  title('Earnings by month');
  put(['Month', 'Bookings', 'Earnings'], { bold: true });
  var chartFrom = row;
  for (var i = 11; i >= 0; i--) {
    var k = monthKey_(month, -i), v = byMonth[k] || [0, 0];
    put([fmt_(new Date(Number(k.slice(0, 4)), Number(k.slice(5, 7)) - 1, 15), 'MMM yyyy'), v[0], v[1]], { money: [3] });
  }
  var chartTo = row - 1;

  title('Earnings by unit');
  put(['Unit', 'Bookings', 'Days rented', 'Earnings (all time)', 'This month'], { bold: true });
  var units = Object.keys(byUnit).sort(function (a, b) { return byUnit[b][2] - byUnit[a][2]; });
  if (!units.length) put(['No confirmed bookings yet']);
  units.forEach(function (u) { put([u, byUnit[u][0], byUnit[u][1], byUnit[u][2], byUnit[u][3]], { money: [4, 5] }); });

  title('Promo codes used');
  put(['Code', 'Bookings', 'Discount given'], { bold: true });
  var codes = Object.keys(byPromo);
  if (!codes.length) put(['None yet']);
  codes.forEach(function (c) { put([c, byPromo[c][0], byPromo[c][1]], { money: [3] }); });

  title('Next 7 days');
  put(['Pick-up', 'Return', 'Unit', 'Name', 'Status', 'Ref'], { bold: true });
  upcoming.sort(function (a, b) { return a[0] - b[0]; });
  if (!upcoming.length) put(['No pick-ups in the next 7 days']);
  upcoming.forEach(function (u) {
    put([fmt_(u[0], 'EEE MMM d, h:mm a'), u[1] ? fmt_(u[1], 'EEE MMM d, h:mm a') : '', u[2], u[3], u[4], u[5]]);
  });

  money.forEach(function (rc) { sh.getRange(rc[0], rc[1]).setNumberFormat('₱#,##0'); });
  sh.setColumnWidth(1, 210);
  try {
    sh.insertChart(sh.newChart().asColumnChart().addRange(sh.getRange(chartFrom, 1, chartTo - chartFrom + 1, 1))
      .addRange(sh.getRange(chartFrom, 3, chartTo - chartFrom + 1, 1)).setPosition(4, 7, 0, 0)
      .setOption('title', 'Earnings by month').setOption('legend', { position: 'none' }).setOption('colors', ['#8E1016']).build());
  } catch (err) {}
}

function count_(n) { return n + (n === 1 ? ' booking' : ' bookings'); }

/* "2026-10" moved by n months. */
function monthKey_(key, n) {
  var y = Number(key.slice(0, 4)), m = Number(key.slice(5, 7)) - 1 + n;
  y += Math.floor(m / 12); m = ((m % 12) + 12) % 12;
  return y + '-' + (m < 9 ? '0' : '') + (m + 1);
}

/* ---- month tabs ---- */

/* Writes the short row into the pick-up month's tab and returns that tab's name. */
function addToMonth_(r) {
  var start = r[COL.pickup - 1], end = r[COL.ret - 1];
  if (!(start instanceof Date) || !(end instanceof Date)) return '';
  var tab = monthSheet_(start);
  var sameDay = fmt_(start, 'MMM d') === fmt_(end, 'MMM d');
  var duration = (sameDay ? fmt_(start, 'MMM d') : fmt_(start, 'MMM d') + ' - ' + fmt_(end, 'MMM d')) +
    ' (' + (r[7] || 1) + (Number(r[7]) === 1 || !r[7] ? ' day' : ' days') + ')';
  var id = String(r[COL.unitId - 1]).trim();
  var u = units_().filter(function (x) { return x.id === id; })[0];
  var unit = (u && u.monthName) || plain_(r[4]);
  tab.appendRow(['', fmt_(start, 'MMM d, h:mm a'), duration, plain_(r[10]), unit, MONTH_STATUS, '', Number(r[8]) || '']);
  var nameCell = tab.getRange(tab.getLastRow(), 4);
  nameCell.setNote('Website booking ' + plain_(r[0]) + '\nMobile: ' + plain_(r[12]) + '\nHand-over: ' + plain_(r[9]) +
    '\nReturn: ' + fmt_(end, 'MMM d, h:mm a') + (r[COL.photos - 1] ? '\nPhotos: ' + r[COL.photos - 1] : '') +
    (plain_(r[COL.promo - 1]) ? '\nPromo: ' + plain_(r[COL.promo - 1]) + ' (-₱' + (Number(r[COL.discount - 1]) || 0) + ')' : '') +
    (Number(r[COL.fee - 1]) ? '\nDelivery fee: ₱' + Number(r[COL.fee - 1]) : '') +
    '\nFull details: ' + SHEET_NAME + ' tab');
  return tab.getName();
}

function fmt_(d, pattern) { return Utilities.formatDate(d, TZ, pattern); }
function plain_(v) { return String(v == null ? '' : v).replace(/^'/, ''); }

/* The tab for a month, matched by name ("October", "OCT", "MAY TRACKING DATA").
   If there is none yet, the previous month's tab is copied, emptied below the header and renamed;
   with no previous month either, a new tab gets plain headers. */
function monthSheet_(date) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var found = findMonth_(ss, date);
  if (found) return found;
  var prev = new Date(date.getTime()); prev.setDate(1); prev.setMonth(prev.getMonth() - 1);
  var template = findMonth_(ss, prev);
  var name = fmt_(date, 'MMMM');
  if (!template) {
    var fresh = ss.insertSheet(name);
    fresh.getRange(1, 1, 1, MONTH_HEADERS.length).setValues([MONTH_HEADERS]).setFontWeight('bold');
    fresh.setFrozenRows(1);
    return fresh;
  }
  var copy = template.copyTo(ss).setName(name);
  if (copy.getLastRow() > 1) copy.getRange(2, 1, copy.getLastRow() - 1, copy.getLastColumn()).clearContent().clearNote();
  ss.setActiveSheet(copy); ss.moveActiveSheet(ss.getNumSheets());
  return copy;
}

function findMonth_(ss, date) {
  var full = fmt_(date, 'MMMM').toUpperCase(), short = full.slice(0, 3);
  var skip = [SHEET_NAME, DASH_TAB].concat(OWNER_TABS).map(function (s) { return s.toUpperCase(); });
  var sheets = ss.getSheets().filter(function (s) {
    var n = s.getName().toUpperCase().trim();
    return skip.indexOf(n) < 0 && (n.indexOf(full) === 0 || n === short || n.indexOf(short + ' ') === 0);
  });
  return sheets[0] || null;
}
