/**
 * Snaps MNL booking sheet — Google Apps Script web app for the /rent/ booking page.
 *
 * What it does
 *   GET  -> the booked time slots per unit (unit id, start, end). No names or
 *           other renter details leave the sheet.
 *   POST -> a renter's booking from the booking page. It is saved as a new
 *           "Pending" row, unless that unit is already booked for an
 *           overlapping time, in which case nothing is saved and the page is
 *           told the unit is taken.
 *
 * A row blocks its unit while its Status is Pending or Confirmed and its
 * return time has not passed. Set Status to Cancelled (or Returned) to free it.
 * Rows you type in yourself (walk-ins, Messenger bookings) block units too, as
 * long as Unit ID, Pick-up and Return are filled in.
 *
 * Set up (once): paste this file into Extensions > Apps Script of a new Google
 * Sheet, run setup(), then Deploy > New deployment > Web app, Execute as: Me,
 * Who has access: Anyone. Put the web app URL in SHOP.bookingSheet in
 * rent/index.html.
 */

var SHEET_NAME = 'Bookings';
var TZ = 'Asia/Manila';
var HEADERS = ['Ref', 'Submitted', 'Status', 'Unit ID', 'Unit', 'Pick-up', 'Return', 'Days', 'Estimate',
  'Hand-over', 'Name', 'Address', 'Mobile', 'Social (FB/IG)', 'Purpose',
  'Emergency name', 'Emergency relationship', 'Emergency mobile', 'Emergency address', 'Notes'];
var STATUSES = ['Pending', 'Confirmed', 'Cancelled', 'Returned'];
var BLOCKING = ['Pending', 'Confirmed'];
/* Must match the `id` of each unit in SHOP.models on the booking page. */
var UNIT_IDS = ['16pro-desert', '14pm-black', '13pm-blue', '13pm-graphite', '13-starlight-z1', '13-starlight-j2'];

/** Run once from the Apps Script editor: sets the time zone, headers and drop-downs. */
function setup() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.setSpreadsheetTimeZone(TZ);
  var sh = ss.getSheetByName(SHEET_NAME) || ss.insertSheet(SHEET_NAME, 0);
  sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
  sh.setFrozenRows(1);
  var rows = sh.getMaxRows() - 1;
  sh.getRange(2, 3, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(STATUSES, true).build());
  sh.getRange(2, 4, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(UNIT_IDS, true).build());
  sh.getRange(2, 2, rows, 1).setNumberFormat('mmm d, yyyy h:mm am/pm');
  sh.getRange(2, 6, rows, 2).setNumberFormat('ddd mmm d, yyyy h:mm am/pm');
  sh.getRange(2, 13, rows, 1).setNumberFormat('@');
  sh.getRange(2, 18, rows, 1).setNumberFormat('@');
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
  var rows = sh.getRange(2, 1, last - 1, 7).getValues(), now = Date.now(), out = [];
  rows.forEach(function (r) {
    var status = String(r[2]).trim(), unit = String(r[3]).trim(), start = r[5], end = r[6];
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
      text_(d.purpose), text_(d.ecName), text_(d.ecRel), text_(d.ecMobile), text_(d.ecAddress), text_(d.notes)]);
    return json_({ ok: true, ref: d.ref, busy: busy_() });
  } finally {
    lock.releaseLock();
  }
}
