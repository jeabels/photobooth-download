/**
 * Phone rental booking sheet — Google Apps Script for the /rent/ booking page.
 * Each shop has its own copy, in its own Google Sheet (Extensions > Apps Script).
 *
 * Owner tabs (created and pre-filled by setup; edit them to change the website)
 *   Settings  shop name, contact, Messenger, banner image, deposit, rental rules,
 *             hand-over options, inclusions, closed dates, booking emails.
 *   Units     one row per phone: ID, name, detail, rate, poster image, shown or
 *             hidden, and how it is written in the month tabs.
 *   Policies  the operational and financial rules renters must tick.
 *   Images can be Google Drive links; use the menu "Make banner and poster
 *   images public" so the website can show them.
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
 *   POST              -> a booking; refused if the unit is already booked for an
 *                        overlapping time, the pick-up is too soon, or a date is closed.
 *   POST {action: "photos"} -> the 3 photos for a booking just saved.
 *
 * Set up (once): paste this file into Extensions > Apps Script, run setup(),
 * then Deploy > New deployment > Web app, Execute as: Me, Who has access:
 * Anyone. The shop's booking link is the /rent/ page with ?shop=<deployment id>.
 */

var SHEET_NAME = 'Website Bookings';
var SETTINGS_TAB = 'Settings', UNITS_TAB = 'Units', POLICIES_TAB = 'Policies';
var TZ = 'Asia/Manila';
var HEADERS = ['Ref', 'Submitted', 'Status', 'Unit ID', 'Unit', 'Pick-up', 'Return', 'Days', 'Estimate',
  'Hand-over', 'Name', 'Address', 'Mobile', 'Social (FB/IG)', 'Purpose',
  'Emergency name', 'Emergency relationship', 'Emergency mobile', 'Emergency address', 'Notes', 'Added to', 'Photos'];
var COL = { status: 3, unitId: 4, pickup: 6, ret: 7, addedTo: 21, photos: 22 };
var STATUSES = ['Pending', 'Confirmed', 'Cancelled', 'Returned'];
var BLOCKING = ['Pending', 'Confirmed'];
/* Month-tab status for a confirmed website booking (must be one of that column's drop-down values). */
var MONTH_STATUS = 'RESERVED';
var MONTH_HEADERS = ['', 'Pick Up', 'Rental Duration', 'Name', 'Unit', 'Status', 'Rental Fee', 'Total', 'Deposit'];

/* What setup writes into new Settings, Units and Policies tabs. Existing tabs are never overwritten. */
var DEFAULT_SETTINGS = [
  ['Shop name', 'Snaps MNL', 'Shown on the booking page, in emails and in the Drive folder name'],
  ['Area', 'Sampaloc, Manila', 'Shown in the page footer'],
  ['Pick-up place', 'Zukiya Residences, Sampaloc, Manila', 'Where renters pick up and return'],
  ['Mobile', '09562086962', 'Text or call number shown to renters'],
  ['Messenger page', '100095276233681', 'Facebook page username or page ID (the part after facebook.com/), for the Messenger button'],
  ['Facebook page link', 'https://www.facebook.com/profile.php?id=100095276233681', 'Link in the page footer'],
  ['Banner image', 'https://jeabels.github.io/photobooth-download/rent/units/banner.webp', 'Google Drive link or image link, about 3:1 (e.g. 1920 x 640). Blank shows the shop name instead'],
  ['Brand colour', '#8E1016', 'Colour of buttons and the header, as a hex code like #8E1016 (blank keeps the standard red)'],
  ['Deposit', 500, 'Refundable security deposit in pesos (0 for none)'],
  ['Hours per rental day', 25, 'How many hours one rental day lasts'],
  ['Late fee per hour', 100, 'In pesos'],
  ['Hand-over options', 'Pick up at Zukiya Residences, Delivery', 'Comma-separated. The first is pick-up at your place; the rest are deliveries'],
  ['Included with every rental', 'iPhone with case, Unit pouch, 64GB OTG file transfer, Cleaning wipes, Power bank, Fast charger', 'Comma-separated'],
  ['Minimum notice (hours)', 2, 'How long before pick-up a renter can still book online'],
  ['Book up to (days ahead)', 180, 'How far ahead renters can book'],
  ['Closed dates', '', 'Days with no pick-ups or returns, comma-separated, e.g. 2026-12-25, 2027-01-01'],
  ['Notify email', '', 'Each new booking is emailed here. Blank turns the emails off']
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

/** Run once from the Apps Script editor: adds the owner tabs and the Website Bookings tab, and asks for Drive and email access. */
function setup() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.setSpreadsheetTimeZone(TZ);
  addTab_(ss, SETTINGS_TAB, ['Setting', 'Value', 'Notes'], DEFAULT_SETTINGS.map(function (r) {
    return r[0] === 'Notify email' ? [r[0], Session.getEffectiveUser().getEmail(), r[2]] : r;
  }));
  addTab_(ss, UNITS_TAB, UNIT_HEADERS, DEFAULT_UNITS);
  addTab_(ss, POLICIES_TAB, ['Section', 'Policy'], DEFAULT_POLICIES);
  var pol = ss.getSheetByName(POLICIES_TAB);
  pol.getRange(2, 1, pol.getMaxRows() - 1, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(['Operational', 'Financial'], true).build());
  var units = ss.getSheetByName(UNITS_TAB);
  units.getRange(2, 6, units.getMaxRows() - 1, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(['Yes', 'No'], true).build());

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
  CacheService.getScriptCache().remove('config');
}

function addTab_(ss, name, headers, rows) {
  if (ss.getSheetByName(name)) return;
  var sh = ss.insertSheet(name);
  sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
  if (rows.length) sh.getRange(2, 1, rows.length, rows[0].length).setValues(rows);
  sh.setFrozenRows(1);
  sh.autoResizeColumns(1, headers.length);
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

function list_(v) { return String(v == null ? '' : v).split(',').map(function (s) { return s.trim(); }).filter(Boolean); }

/* Google Drive links become direct image links; other links are used as they are. */
function imageUrl_(v) {
  var s = String(v == null ? '' : v).trim(), m = s.match(/\/d\/([\w-]{20,})/) || s.match(/[?&]id=([\w-]{20,})/);
  if (m && /google\.com/.test(s)) return 'https://lh3.googleusercontent.com/d/' + m[1] + '=w2400';
  return /^https:\/\//.test(s) ? s : '';
}

/* Units from the Units tab (all of them, including hidden ones). */
function units_() {
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(UNITS_TAB);
  var rows = sh && sh.getLastRow() > 1 ? sh.getRange(2, 1, sh.getLastRow() - 1, UNIT_HEADERS.length).getValues() : DEFAULT_UNITS;
  return rows.filter(function (r) { return String(r[0]).trim() && String(r[1]).trim(); }).map(function (r) {
    return { id: String(r[0]).trim(), name: String(r[1]).trim(), detail: String(r[2]).trim(), rate: Number(r[3]) || null,
      poster: imageUrl_(r[4]), show: String(r[5]).trim().toLowerCase() !== 'no', monthName: String(r[6]).trim() };
  });
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

/* What the booking page needs, kept for a minute so the page loads quickly. No private details. */
function config_() {
  var cache = CacheService.getScriptCache(), hit = cache.get('config');
  if (hit) return JSON.parse(hit);
  var s = settings_(), pol = policies_();
  var c = {
    name: String(s['Shop name']).trim(), area: String(s['Area']).trim(), pickupPlace: String(s['Pick-up place']).trim(),
    mobile: String(s['Mobile']).replace(/[^\d+]/g, ''), messenger: String(s['Messenger page']).trim(),
    facebookUrl: String(s['Facebook page link']).trim(), banner: imageUrl_(s['Banner image']),
    brandColor: /^#[0-9a-f]{6}$/i.test(String(s['Brand colour']).trim()) ? String(s['Brand colour']).trim() : '',
    deposit: Number(s['Deposit']) || 0, hoursPerDay: Number(s['Hours per rental day']) || 24,
    lateFeePerHour: Number(s['Late fee per hour']) || 0, handover: list_(s['Hand-over options']),
    inclusions: list_(s['Included with every rental']), minNoticeHours: Number(s['Minimum notice (hours)']) || 0,
    maxDaysAhead: Number(s['Book up to (days ahead)']) || 365, closedDates: list_(s['Closed dates']).filter(function (d) { return /^\d{4}-\d{2}-\d{2}$/.test(d); }),
    units: units_().filter(function (u) { return u.show; }).map(function (u) { return { id: u.id, name: u.name, detail: u.detail, rate: u.rate, poster: u.poster }; }),
    policies: pol.operational, liabilities: pol.financial
  };
  if (!c.handover.length) c.handover = ['Pick up'];
  cache.put('config', JSON.stringify(c), 60);
  return c;
}

/** Menu item: lets anyone with the link view the Drive images used for the banner and posters. */
function publishImages() {
  var links = [settings_()['Banner image']].concat(units_().map(function (u) { return u.poster; }));
  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(UNITS_TAB);
  if (sh && sh.getLastRow() > 1) links = links.concat(sh.getRange(2, 5, sh.getLastRow() - 1, 1).getValues().map(function (r) { return r[0]; }));
  var done = 0, failed = 0, seen = {};
  links.forEach(function (v) {
    var m = String(v || '').match(/\/d\/([\w-]{20,})/) || String(v || '').match(/[?&]id=([\w-]{20,})/);
    if (!m || seen[m[1]]) return; seen[m[1]] = 1;
    try { DriveApp.getFileById(m[1]).setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW); done++; } catch (e) { failed++; }
  });
  CacheService.getScriptCache().remove('config');
  SpreadsheetApp.getActiveSpreadsheet().toast(done + ' image(s) made public' + (failed ? ', ' + failed + ' could not be changed (check you own them)' : '') + '.');
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
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var taken = busy_().filter(function (b) {
      return b.unit === d.unitId && start < new Date(b.end) && new Date(b.start) < end;
    })[0];
    if (taken) return json_({ ok: false, error: 'taken', until: taken.end, busy: busy_() });
    sheet_().appendRow([text_(d.ref), new Date(), 'Pending', d.unitId, text_(d.unit), start, end, Number(d.days) || '',
      Number(d.estimate) || '', text_(d.handover), text_(d.name), text_(d.address), text_(d.mobile), text_(d.social),
      text_(d.purpose), text_(d.ecName), text_(d.ecRel), text_(d.ecMobile), text_(d.ecAddress), text_(d.notes), '', '']);
  } finally {
    lock.releaseLock();
  }
  notify_(d, start, end, c);
  return json_({ ok: true, ref: d.ref, busy: busy_() });
}

/* Emails the owner about a new booking. A failed email never stops the booking. */
function notify_(d, start, end, c) {
  try {
    var to = String(settings_()['Notify email'] || '').trim();
    if (!to) return;
    var url = SpreadsheetApp.getActiveSpreadsheet().getUrl();
    MailApp.sendEmail(to, 'New booking ' + d.ref + ' · ' + d.unit + ' · ' + fmt_(start, 'MMM d, h:mm a'),
      'New website booking for ' + c.name + ' (Pending)\n\n' +
      'Ref: ' + d.ref + '\nUnit: ' + d.unit + '\nPick-up: ' + fmt_(start, 'EEE MMM d, h:mm a') + '\nReturn: ' + fmt_(end, 'EEE MMM d, h:mm a') +
      '\nDays: ' + d.days + (d.estimate ? '\nEstimate: ₱' + d.estimate : '') + '\nHand-over: ' + d.handover +
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

/** Runs when the sheet is edited: owner-tab edits refresh the website; a booking set to Confirmed goes into its month tab (once). */
function onEdit(e) {
  var range = e && e.range;
  if (!range) return;
  var name = range.getSheet().getName();
  if (name === SETTINGS_TAB || name === UNITS_TAB || name === POLICIES_TAB) { CacheService.getScriptCache().remove('config'); return; }
  if (name !== SHEET_NAME) return;
  if (range.getColumn() > COL.status || range.getLastColumn() < COL.status) return;
  var sh = range.getSheet();
  for (var row = Math.max(2, range.getRow()); row <= range.getLastRow(); row++) {
    var r = sh.getRange(row, 1, 1, HEADERS.length).getValues()[0];
    if (String(r[COL.status - 1]).trim() !== 'Confirmed' || r[COL.addedTo - 1]) continue;
    var tab = addToMonth_(r);
    if (tab) sh.getRange(row, COL.addedTo).setValue(tab);
  }
}

/** Adds the shop's menu to the sheet. */
function onOpen() {
  SpreadsheetApp.getUi().createMenu(String(settings_()['Shop name'] || 'Rentals').trim() || 'Rentals')
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
  var skip = [SHEET_NAME, SETTINGS_TAB, UNITS_TAB, POLICIES_TAB].map(function (s) { return s.toUpperCase(); });
  var sheets = ss.getSheets().filter(function (s) {
    var n = s.getName().toUpperCase().trim();
    return skip.indexOf(n) < 0 && (n.indexOf(full) === 0 || n === short || n.indexOf(short + ' ') === 0);
  });
  return sheets[0] || null;
}
