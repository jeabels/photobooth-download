// =============================================================================
//  download-gate — Supabase Edge Function
//
//  The sales page calls it twice:
//    { action: "request", name, contact, booth?, note? }
//        → saves the request and emails a 6-digit code to YOUR Gmail.
//          The customer gets nothing until you send them the code.
//    { action: "verify", code }
//        → if the code is right, returns a 15-minute link to the APK.
//          The code alone is enough, so it works on any device.
//
//  The booth app calls it once, from its first-run screen:
//    { action: "activate", code }
//        → returns the Supabase project the booth uploads photos to:
//          the buyer's own (cloud_ref / cloud_key on their row, if you
//          filled them in) or, by default, yours.
//
//  Secrets (Supabase → Edge Functions → Secrets):
//    RESEND_API_KEY   from resend.com (sign up with the Gmail that should get codes)
//    OWNER_EMAIL      that same Gmail address
//    SB_SECRET_KEY    only if the function reports "not set up": your sb_secret_… key
//  Optional: APK_PATH (default SnapsPhotobooth.apk), SITE_ORIGIN (default *)
//            BOOTH_PUBLIC_KEY — your sb_publishable_… key, handed to booths that
//            share your project (default: this project's anon key)
//
//  Deploy with "Verify JWT" turned OFF — the page calls it without a login.
// =============================================================================

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SB_SECRET_KEY") || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const RESEND_KEY = Deno.env.get("RESEND_API_KEY") ?? "";
const OWNER_EMAIL = Deno.env.get("OWNER_EMAIL") ?? "";
const APK_PATH = Deno.env.get("APK_PATH") || "SnapsPhotobooth.apk";
const SITE_ORIGIN = Deno.env.get("SITE_ORIGIN") || "*";

const CODE_VALID_DAYS = 7;
const MAX_WRONG_CODES_PER_HOUR = 10;
const MAX_DOWNLOADS = 3;
const MAX_REQUESTS_PER_HOUR = 3;
const LINK_MINUTES = 15;
const ACTIVATE_DAYS = 60;
const MAX_ACTIVATIONS = 5;
const BOOTH_PUBLIC_KEY = Deno.env.get("BOOTH_PUBLIC_KEY") || Deno.env.get("SUPABASE_ANON_KEY") || "";

const CORS = {
  "Access-Control-Allow-Origin": SITE_ORIGIN,
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

function keyHeaders(extra: Record<string, string> = {}): Record<string, string> {
  const h: Record<string, string> = { apikey: SERVICE_KEY, ...extra };
  if (SERVICE_KEY.startsWith("eyJ")) h.Authorization = `Bearer ${SERVICE_KEY}`;
  return h;
}

async function db(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: keyHeaders({ "Content-Type": "application/json", ...(init.headers as Record<string, string> ?? {}) }),
  });
}

/** Codes are stored only as a keyed hash, never as the digits themselves. */
async function codeHash(code: string): Promise<string> {
  return await sha256(`code:${SERVICE_KEY}:${code}`);
}

async function sha256(text: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(bytes)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function sixDigits(): string {
  // Rejection sampling keeps every code equally likely.
  const buf = new Uint32Array(1);
  let n = 0;
  do {
    crypto.getRandomValues(buf);
    n = buf[0];
  } while (n >= 4294000000);
  return String(n % 1000000).padStart(6, "0");
}

function clean(value: unknown, max: number): string {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, max);
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));
}

async function sendEmail(subject: string, html: string): Promise<boolean> {
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${RESEND_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from: "Snaps Photobooth <onboarding@resend.dev>",
      to: [OWNER_EMAIL],
      subject,
      html,
    }),
  });
  return res.ok;
}

/* ---------------------------------------------------------------- request */

async function handleRequest(body: Record<string, unknown>, ipHash: string): Promise<Response> {
  const name = clean(body.name, 80);
  const contact = clean(body.contact, 120);
  const booth = clean(body.booth, 80);
  const note = clean(body.note, 500);
  if (!name || !contact) return reply({ error: "Add your name and a way to reach you." }, 400);

  const hourAgo = new Date(Date.now() - 3600_000).toISOString();
  const recent = await db(`download_requests?select=id&ip_hash=eq.${ipHash}&created_at=gte.${encodeURIComponent(hourAgo)}`);
  if (!recent.ok) return reply({ error: "The download service is not set up yet." }, 500);
  if ((await recent.json()).length >= MAX_REQUESTS_PER_HOUR) {
    return reply({ error: "You've sent several requests already. Please wait an hour, or message the seller." }, 429);
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  let code = "", hash = "";
  for (let tries = 0; tries < 5; tries++) {
    code = sixDigits();
    hash = await codeHash(code);
    const clash = await db(`download_requests?select=id&code_hash=eq.${hash}&expires_at=gt.${encodeURIComponent(now)}`);
    if (clash.ok && (await clash.json()).length === 0) break;
  }
  const saved = await db("download_requests", {
    method: "POST",
    headers: { Prefer: "return=minimal" },
    body: JSON.stringify({
      id, name, contact, booth: booth || null, note: note || null,
      code_hash: hash,
      expires_at: new Date(Date.now() + CODE_VALID_DAYS * 86400_000).toISOString(),
      ip_hash: ipHash,
    }),
  });
  if (!saved.ok) return reply({ error: "The download service is not set up yet." }, 500);

  const row = (label: string, value: string) =>
    value ? `<tr><td style="padding:6px 16px 6px 0;color:#6b645b">${label}</td><td style="padding:6px 0"><b>${escapeHtml(value)}</b></td></tr>` : "";
  const html = `
    <div style="font-family:Arial,sans-serif;max-width:480px;color:#1b1712">
      <h2 style="margin:0 0 4px">New download request</h2>
      <p style="margin:0 0 18px;color:#6b645b">Someone wants the Snaps Photobooth app.</p>
      <table style="font-size:15px;border-collapse:collapse">
        ${row("Name", name)}${row("Contact", contact)}${row("Booth", booth)}${row("Message", note)}
      </table>
      <p style="margin:24px 0 6px;color:#6b645b">Their download code</p>
      <p style="margin:0;font-size:38px;letter-spacing:10px;font-weight:bold">${code}</p>
      <p style="margin:18px 0 0;font-size:14px;color:#6b645b">
        Send this code <b>only after payment is confirmed</b>. It works for ${CODE_VALID_DAYS} days
        and allows up to ${MAX_DOWNLOADS} downloads. The buyer enters the same code in the app
        to switch on digital copies.
      </p>
    </div>`;
  const sent = await sendEmail(`Download request from ${name} — code ${code}`, html).catch(() => false);
  if (!sent) {
    await db(`download_requests?id=eq.${id}`, { method: "DELETE" });
    return reply({ error: "The seller couldn't be reached just now. Please try again in a few minutes." }, 502);
  }
  return reply({ id });
}

/* ------------------------------------------------------- code look-up */

type Found = { req?: Record<string, any>; error?: Response };

/** Finds the request a code belongs to. Wrong guesses are logged per address,
    and after ten in an hour that address is refused. */
async function findByCode(body: Record<string, unknown>, ipHash: string): Promise<Found> {
  const code = clean(body.code, 12).replace(/\s/g, "");
  if (!/^\d{6}$/.test(code)) return { error: reply({ error: "The code is 6 digits." }, 400) };

  const hourAgo = new Date(Date.now() - 3600_000).toISOString();
  const fails = await db(`download_failures?select=at&ip_hash=eq.${ipHash}&at=gte.${encodeURIComponent(hourAgo)}`);
  if (!fails.ok) return { error: reply({ error: "The download service is not set up yet." }, 500) };
  if ((await fails.json()).length >= MAX_WRONG_CODES_PER_HOUR) {
    return { error: reply({ error: "Too many wrong codes. Please wait an hour, or message the seller." }, 429) };
  }

  const found = await db(`download_requests?select=*&code_hash=eq.${await codeHash(code)}&order=created_at.desc&limit=1`);
  if (!found.ok) return { error: reply({ error: "The download service is not set up yet." }, 500) };
  const [req] = await found.json();
  if (!req) {
    await db("download_failures", { method: "POST", headers: { Prefer: "return=minimal" }, body: JSON.stringify({ ip_hash: ipHash }) });
    return { error: reply({ error: "That code isn't right. Check the digits and try again." }, 401) };
  }
  return { req };
}

/* ----------------------------------------------------------------- verify */

async function handleVerify(body: Record<string, unknown>, ipHash: string): Promise<Response> {
  const { req, error } = await findByCode(body, ipHash);
  if (error || !req) return error!;
  if (new Date(req.expires_at).getTime() < Date.now()) {
    return reply({ error: "This code has expired. Ask the seller for a new one." }, 410);
  }
  const id = req.id;
  if (req.downloads >= MAX_DOWNLOADS) {
    return reply({ error: "This code has been used the maximum number of times. Ask the seller for a new one." }, 403);
  }

  const signed = await fetch(`${SUPABASE_URL}/storage/v1/object/sign/releases/${encodeURIComponent(APK_PATH)}`, {
    method: "POST",
    headers: keyHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ expiresIn: LINK_MINUTES * 60 }),
  });
  const signedBody = signed.ok ? await signed.json() : {};
  const path = signedBody.signedURL ?? signedBody.signedUrl;
  if (!path) return reply({ error: "The app file isn't available yet. Please tell the seller." }, 503);

  const downloads = req.downloads + 1;
  await db(`download_requests?id=eq.${id}`, {
    method: "PATCH",
    body: JSON.stringify({ downloads, last_download_at: new Date().toISOString() }),
  });
  if (downloads === 1) {
    await sendEmail(`${req.name} downloaded the app`,
      `<p style="font-family:Arial,sans-serif">${escapeHtml(req.name)} (${escapeHtml(req.contact)}) entered the code and downloaded Snaps Photobooth.</p>`)
      .catch(() => false);
  }

  const url = `${SUPABASE_URL}/storage/v1${path}${path.includes("?") ? "&" : "?"}download=SnapsPhotobooth.apk`;
  return reply({ url, left: MAX_DOWNLOADS - downloads });
}

/* --------------------------------------------------------------- activate */

async function handleActivate(body: Record<string, unknown>, ipHash: string): Promise<Response> {
  const { req, error } = await findByCode(body, ipHash);
  if (error || !req) return error!;
  if (Date.now() - new Date(req.created_at).getTime() > ACTIVATE_DAYS * 86400_000) {
    return reply({ error: "This code is too old to connect a booth. Ask the seller for a new one." }, 410);
  }
  const used = Number(req.activations) || 0;
  if (used >= MAX_ACTIVATIONS) {
    return reply({ error: "This code has connected the maximum number of booths. Ask the seller for help." }, 403);
  }

  const ownRef = new URL(SUPABASE_URL).hostname.split(".")[0];
  const ref = String(req.cloud_ref || "").trim() || ownRef;
  const key = String(req.cloud_key || "").trim() || (ref === ownRef ? BOOTH_PUBLIC_KEY : "");
  if (!ref || !key) {
    return reply({ error: "Digital copies aren't ready for this code yet. Please tell the seller." }, 503);
  }

  // Older tables may not have these columns yet; connecting still works.
  await db(`download_requests?id=eq.${req.id}`, {
    method: "PATCH",
    body: JSON.stringify({ activations: used + 1, last_activated_at: new Date().toISOString() }),
  }).catch(() => null);

  return reply({ ref, key, bucket: String(req.cloud_bucket || "").trim() || "photos", booth: req.booth || "" });
}

/* ----------------------------------------------------------------- server */

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);
  if (!SUPABASE_URL || !SERVICE_KEY || !RESEND_KEY || !OWNER_EMAIL) {
    return reply({ error: "The download service is not set up yet." }, 500);
  }

  const body = await req.json().catch(() => ({})) as Record<string, unknown>;
  const ip = (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim() || "unknown";
  const ipHash = (await sha256(`ip:${ip}`)).slice(0, 32);

  try {
    if (body.action === "request") return await handleRequest(body, ipHash);
    if (body.action === "verify") return await handleVerify(body, ipHash);
    if (body.action === "activate") return await handleActivate(body, ipHash);
    return reply({ error: "Unknown action" }, 400);
  } catch (e) {
    console.error(e);
    return reply({ error: "Something went wrong. Please try again." }, 500);
  }
});
