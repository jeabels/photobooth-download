/**
 * Snaps MNL booking sheet — Google Apps Script for the /rent/ booking page.
 * Lives in the "Snap's MNL | Rental Information" spreadsheet (Extensions > Apps Script).
 *
 * Website Bookings tab (created by setup)
 *   Every booking from the website lands here as "Pending", with all the
 *   renter's details. A row blocks its unit on the website, from pick-up to
 *   return, while its Status is Pending or Confirmed. Set it to Cancelled (or
 *   Returned) to free the unit. Rows typed in by hand block units too, as long
 *   as Unit ID, Pick-up and Return are filled in.
 *
 * Month tabs (October, SEPTEMBER, MAY TRACKING DATA, …)
 *   When a booking's Status is changed to "Confirmed", a short row goes into the
 *   tab for its pick-up month, in the shop's own columns:
 *     B Pick Up · C Rental Duration · D Name · E Unit · F Status (RESERVED) ·
 *     H rental total. Ref, mobile and hand-over go in a note on the name.
 *   The month tab is found by name (e.g. "October" or "OCT…"). If there is none
 *   yet, the previous month's tab is copied, emptied and renamed.
 *
 * Requirement photos
 *   The renter's 3 photos are saved to a Google Drive folder, "Snaps MNL booking
 *   requirements / <ref> <name>", which only you can open. Its link goes in the
 *   booking's Photos column.
 *
 * Web app
 *   GET  -> booked time slots per unit (unit id, start, end); no renter details.
 *   POST -> a booking from the website; refused with "taken" if that unit is
 *           already booked for an overlapping time.
 *   POST {action: "photos"} -> the 3 photos for a booking just saved.
 *
 * Set up (once): paste this file into Extensions > Apps Script, run setup(),
 * then Deploy > New deployment > Web app, Execute as: Me, Who has access:
 * Anyone. Put the web app URL in SHOP.bookingSheet in rent/index.html.
 */

var SHEET_NAME = 'Website Bookings';
var TZ = 'Asia/Manila';
var HEADERS = ['Ref', 'Submitted', 'Status', 'Unit ID', 'Unit', 'Pick-up', 'Return', 'Days', 'Estimate',
  'Hand-over', 'Name', 'Address', 'Mobile', 'Social (FB/IG)', 'Purpose',
  'Emergency name', 'Emergency relationship', 'Emergency mobile', 'Emergency address', 'Notes', 'Added to',
  'Photos', 'Copy for tracker'];
var COL = { status: 3, unitId: 4, pickup: 6, ret: 7, addedTo: 21, photos: 22 };
var PHOTO_FOLDER = 'Snaps MNL booking requirements';
var STATUSES = ['Pending', 'Confirmed', 'Cancelled', 'Returned'];
var BLOCKING = ['Pending', 'Confirmed'];
/* Unit IDs must match the `id` of each unit in SHOP.models on the booking page.
   The value is how the unit is written in the month tabs' Unit column. */
var UNITS = {
  '16pro-desert': '16',
  '14pm-black': '14',
  '13pm-blue': '13pm',
  '13pm-graphite': '13pm (2)',
  '13-starlight-z1': '13 (Z1)',
  '13-starlight-j2': '13 (J2)'
};
var UNIT_IDS = Object.keys(UNITS);
/* Month-tab status for a confirmed website booking (must be one of that column's drop-down values). */
var MONTH_STATUS = 'RESERVED';

/** Run once from the Apps Script editor: adds the Website Bookings tab with its headers and drop-downs. */
function setup() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.setSpreadsheetTimeZone(TZ);
  var sh = ss.getSheetByName(SHEET_NAME) || ss.insertSheet(SHEET_NAME);
  sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
  sh.setFrozenRows(1);
  var rows = sh.getMaxRows() - 1;
  sh.getRange(2, COL.status, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(STATUSES, true).build());
  sh.getRange(2, COL.unitId, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(UNIT_IDS, true).build());
  sh.getRange(2, 2, rows, 1).setNumberFormat('mmm d, yyyy h:mm am/pm');
  sh.getRange(2, COL.pickup, rows, 2).setNumberFormat('ddd mmm d, yyyy h:mm am/pm');
  sh.getRange(2, 13, rows, 1).setNumberFormat('@');
  sh.getRange(2, 18, rows, 1).setNumberFormat('@');
  photoFolder_();   // asks for Drive access now, so photo uploads work later
}

function photoFolder_() {
  var found = DriveApp.getFoldersByName(PHOTO_FOLDER);
  return found.hasNext() ? found.next() : DriveApp.createFolder(PHOTO_FOLDER);
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

function doGet() {
  return json_({ ok: true, busy: busy_() });
}

function doPost(e) {
  var d;
  try { d = JSON.parse(e.postData.contents); } catch (err) { return json_({ ok: false, error: 'bad_request' }); }
  if (d.website) return json_({ ok: true, ref: d.ref });   // filled-in hidden field: a bot, not a renter
  if (d.action === 'photos') return savePhotos_(d);
  var start = new Date(d.start), end = new Date(d.end);
  if (UNIT_IDS.indexOf(d.unitId) < 0 || isNaN(start) || isNaN(end) || end <= start || !d.name || !d.mobile) {
    return json_({ ok: false, error: 'missing' });
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
      text_(d.purpose), text_(d.ecName), text_(d.ecRel), text_(d.ecMobile), text_(d.ecAddress), text_(d.notes), '', '',
      "'" + String(d.message || '').slice(0, 4000)]);
    return json_({ ok: true, ref: d.ref, busy: busy_() });
  } finally {
    lock.releaseLock();
  }
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

/** Runs when the sheet is edited: a Website Bookings row set to Confirmed goes into its month tab (once). */
function onEdit(e) {
  var range = e && e.range;
  if (!range || range.getSheet().getName() !== SHEET_NAME) return;
  if (range.getColumn() > COL.status || range.getLastColumn() < COL.status) return;
  var sh = range.getSheet();
  for (var row = Math.max(2, range.getRow()); row <= range.getLastRow(); row++) {
    var r = sh.getRange(row, 1, 1, HEADERS.length).getValues()[0];
    if (String(r[COL.status - 1]).trim() !== 'Confirmed' || r[COL.addedTo - 1]) continue;
    var tab = addToMonth_(r);
    if (tab) sh.getRange(row, COL.addedTo).setValue(tab);
  }
}

/** Adds a "Snaps MNL" menu to the sheet. */
function onOpen() {
  SpreadsheetApp.getUi().createMenu('Snaps MNL')
    .addItem('Add confirmed website bookings to month tabs', 'addConfirmed')
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
  var unit = UNITS[String(r[COL.unitId - 1]).trim()] || plain_(r[4]);
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
   If there is none yet, the previous month's tab is copied, emptied below the header and renamed. */
function monthSheet_(date) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var found = findMonth_(ss, date);
  if (found) return found;
  var prev = new Date(date.getTime()); prev.setDate(1); prev.setMonth(prev.getMonth() - 1);
  var template = findMonth_(ss, prev);
  var name = fmt_(date, 'MMMM');
  if (!template) return ss.insertSheet(name);
  var copy = template.copyTo(ss).setName(name);
  if (copy.getLastRow() > 1) copy.getRange(2, 1, copy.getLastRow() - 1, copy.getLastColumn()).clearContent().clearNote();
  ss.setActiveSheet(copy); ss.moveActiveSheet(ss.getNumSheets());
  return copy;
}

function findMonth_(ss, date) {
  var full = fmt_(date, 'MMMM').toUpperCase(), short = full.slice(0, 3);
  var sheets = ss.getSheets().filter(function (s) {
    var n = s.getName().toUpperCase().trim();
    return n !== SHEET_NAME.toUpperCase() && (n.indexOf(full) === 0 || n === short || n.indexOf(short + ' ') === 0);
  });
  return sheets[0] || null;
}
