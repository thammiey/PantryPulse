// PantryPulse community API: accounts, shop profiles and peer-to-peer giveaways.
//
// Claiming is protected by compare-and-set updates ("UPDATE ... WHERE status =
// 'available'") and, for approval-mode listings, by a write transaction. Turso/SQLite
// executes writes one at a time, so two people tapping "Claim" at the same instant
// cannot both succeed: exactly one UPDATE matches the row, the other affects 0 rows.

import { randomBytes, randomUUID, scryptSync, timingSafeEqual, createHash } from 'node:crypto';
import { db, ensureSchema } from './db.js';
import { adminPage } from './admin.js';
import { AiError, medicineInfo } from './ai.js';

// ---------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------

let clock = () => Date.now();
export function setClockForTests(fn) { clock = fn; }

class HttpError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}
const bad = (code, message) => new HttpError(400, code, message);

const MAX_ACTIVE_RESERVATIONS = 5;
const MAX_DEADLINE_MS = 14 * 24 * 3600 * 1000;

function hashPassword(password) {
  const salt = randomBytes(16);
  const key = scryptSync(password, salt, 32);
  return `scrypt$${salt.toString('base64')}$${key.toString('base64')}`;
}

function verifyPassword(password, stored) {
  const [scheme, saltB64, keyB64] = String(stored).split('$');
  if (scheme !== 'scrypt') return false;
  const expected = Buffer.from(keyB64, 'base64');
  const actual = scryptSync(password, Buffer.from(saltB64, 'base64'), expected.length);
  return timingSafeEqual(actual, expected);
}

const sha256 = (s) => createHash('sha256').update(s).digest('hex');
const pickupCode = () => String(Math.floor(1000 + Math.random() * 9000));
const firstName = (name) => String(name || '').trim().split(/\s+/)[0] || 'Someone';

function str(v, field, { max = 200, required = false } = {}) {
  if (v === undefined || v === null) {
    if (required) throw bad('missing_field', `${field} is required`);
    return '';
  }
  const s = String(v).trim();
  if (required && !s) throw bad('missing_field', `${field} is required`);
  if (s.length > max) throw bad('too_long', `${field} must be at most ${max} characters`);
  return s;
}

function coord(v, field, min, max) {
  const n = Number(v);
  if (v === undefined || v === null || v === '' || !Number.isFinite(n) || n < min || n > max) {
    throw bad('invalid_location', `${field} must be a number between ${min} and ${max}`);
  }
  return n;
}

export function distanceKm(lat1, lon1, lat2, lon2) {
  const r = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** Public distance is bucketed to 0.5 km so exact homes can't be triangulated. */
export function publicDistance(km) {
  if (km < 1) return { distanceKm: 0.5, distanceLabel: 'under 1 km' };
  const rounded = Math.round(km * 2) / 2;
  return { distanceKm: rounded, distanceLabel: `about ${rounded} km` };
}

/** Public map point snapped to a ~1 km grid. */
const approx = (v) => Math.round(v * 100) / 100;

async function addEvent(exec, userId, type, listingId, message) {
  await exec.execute({
    sql: 'INSERT INTO events (user_id, type, listing_id, message, created_at) VALUES (?, ?, ?, ?, ?)',
    args: [userId, type, listingId, message, clock()],
  });
}

// ---------------------------------------------------------------------------------
// Users & sessions
// ---------------------------------------------------------------------------------

function userView(u) {
  return {
    id: u.id,
    email: u.email,
    name: u.name,
    accountType: u.account_type,
    shopName: u.shop_name ?? null,
    shopAddress: u.shop_address ?? null,
    openingHours: u.opening_hours ?? null,
    lat: u.lat ?? null,
    lon: u.lon ?? null,
    notifyRadiusKm: u.notify_radius_km ?? 0,
  };
}

async function newSession(userId) {
  const token = randomBytes(32).toString('base64url');
  await db().execute({
    sql: 'INSERT INTO sessions (token_hash, user_id, created_at) VALUES (?, ?, ?)',
    args: [sha256(token), userId, clock()],
  });
  return token;
}

async function authUser(req) {
  const header = req.headers['authorization'] || '';
  const m = /^Bearer\s+(.+)$/i.exec(header);
  if (!m) throw new HttpError(401, 'unauthorized', 'Sign in required');
  const rs = await db().execute({
    sql: 'SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?',
    args: [sha256(m[1])],
  });
  if (!rs.rows.length) throw new HttpError(401, 'unauthorized', 'Session expired, please sign in again');
  return rs.rows[0];
}

function validateAccount(body, { partial = false } = {}) {
  const out = {};
  if (!partial || body.name !== undefined) out.name = str(body.name, 'name', { max: 60, required: true });
  if (!partial || body.accountType !== undefined) {
    const t = body.accountType ?? 'person';
    if (!['person', 'shop'].includes(t)) throw bad('invalid_account_type', 'accountType must be person or shop');
    out.account_type = t;
  }
  for (const [k, col, max] of [['shopName', 'shop_name', 80], ['shopAddress', 'shop_address', 200], ['openingHours', 'opening_hours', 120]]) {
    if (body[k] !== undefined) out[col] = str(body[k], k, { max }) || null;
  }
  if (body.lat !== undefined || body.lon !== undefined) {
    if (body.lat === null && body.lon === null) { out.lat = null; out.lon = null; } else {
      out.lat = coord(body.lat, 'lat', -90, 90);
      out.lon = coord(body.lon, 'lon', -180, 180);
    }
  }
  if (body.notifyRadiusKm !== undefined) {
    const km = Number(body.notifyRadiusKm);
    if (!Number.isFinite(km) || km < 0 || km > MAX_ALERT_KM) throw bad('invalid_radius', `notifyRadiusKm must be between 0 and ${MAX_ALERT_KM}`);
    out.notify_radius_km = km;
  }
  if (out.account_type === 'shop' && !partial && !out.shop_name) throw bad('missing_field', 'shopName is required for shop accounts');
  return out;
}

async function register(body) {
  const email = str(body.email, 'email', { max: 120, required: true }).toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(email)) throw bad('invalid_email', 'Please enter a valid email address');
  const password = String(body.password ?? '');
  if (password.length < 6) throw bad('weak_password', 'Password must be at least 6 characters');
  const fields = validateAccount(body);
  const id = randomUUID();
  try {
    await db().execute({
      sql: `INSERT INTO users (id, email, name, password_hash, account_type, shop_name, shop_address, opening_hours, lat, lon, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      args: [id, email, fields.name, hashPassword(password), fields.account_type, fields.shop_name ?? null,
        fields.shop_address ?? null, fields.opening_hours ?? null, fields.lat ?? null, fields.lon ?? null, clock()],
    });
  } catch (e) {
    if (/UNIQUE/i.test(String(e.message))) throw new HttpError(409, 'email_taken', 'An account with this email already exists');
    throw e;
  }
  const user = (await db().execute({ sql: 'SELECT * FROM users WHERE id = ?', args: [id] })).rows[0];
  return { token: await newSession(id), user: userView(user), created: true };
}

async function login(body) {
  const email = str(body.email, 'email', { required: true }).toLowerCase();
  const rs = await db().execute({ sql: 'SELECT * FROM users WHERE email = ?', args: [email] });
  const user = rs.rows[0];
  if (!user || !verifyPassword(String(body.password ?? ''), user.password_hash)) {
    throw new HttpError(401, 'invalid_credentials', 'Incorrect email or password');
  }
  return { token: await newSession(user.id), user: userView(user), created: false };
}

/** Used by the app on sign-in: logs in, or creates the online account the first time. */
async function connect(body) {
  const email = str(body.email, 'email', { required: true }).toLowerCase();
  const exists = await db().execute({ sql: 'SELECT 1 FROM users WHERE email = ?', args: [email] });
  return exists.rows.length ? login(body) : register(body);
}

async function updateMe(user, body) {
  const fields = validateAccount(body, { partial: true });
  const merged = { ...user, ...fields };
  if (merged.account_type === 'shop' && !merged.shop_name) throw bad('missing_field', 'shopName is required for shop accounts');
  const cols = Object.keys(fields);
  if (cols.length) {
    await db().execute({
      sql: `UPDATE users SET ${cols.map((c) => `${c} = ?`).join(', ')} WHERE id = ?`,
      args: [...cols.map((c) => fields[c]), user.id],
    });
  }
  const fresh = (await db().execute({ sql: 'SELECT * FROM users WHERE id = ?', args: [user.id] })).rows[0];
  return { user: userView(fresh) };
}

// ---------------------------------------------------------------------------------
// Listings
// ---------------------------------------------------------------------------------

function validateListing(body, user) {
  const now = clock();
  const title = str(body.title, 'title', { max: 80, required: true });
  const deadline = Number(body.pickupDeadline);
  if (!Number.isFinite(deadline) || deadline <= now) throw bad('invalid_deadline', 'Pickup deadline must be in the future');
  if (deadline > now + MAX_DEADLINE_MS) throw bad('invalid_deadline', 'Pickup deadline can be at most 14 days away');
  const claimMode = body.claimMode ?? 'direct';
  if (!['direct', 'approval'].includes(claimMode)) throw bad('invalid_claim_mode', 'claimMode must be direct or approval');
  const lat = body.lat ?? user.lat;
  const lon = body.lon ?? user.lon;
  const itemExpiry = body.itemExpiry ? str(body.itemExpiry, 'itemExpiry', { max: 10 }) : null;
  if (itemExpiry && !/^\d{4}-\d{2}-\d{2}$/.test(itemExpiry)) throw bad('invalid_expiry', 'itemExpiry must be YYYY-MM-DD');
  const clientProductId = body.clientProductId == null ? null : Number(body.clientProductId);
  return {
    title,
    category: str(body.category, 'category', { max: 30 }) || 'OTHER',
    quantity_text: str(body.quantityText, 'quantityText', { max: 40 }),
    notes: str(body.notes, 'notes', { max: 300 }),
    item_expiry: itemExpiry,
    pickup_deadline: deadline,
    lat: coord(lat, 'lat', -90, 90),
    lon: coord(lon, 'lon', -180, 180),
    area_label: str(body.areaLabel, 'areaLabel', { max: 80 }),
    pickup_address: str(body.pickupAddress, 'pickupAddress', { max: 200 }) || (user.account_type === 'shop' ? user.shop_address || '' : ''),
    pickup_instructions: str(body.pickupInstructions, 'pickupInstructions', { max: 300 }),
    claim_mode: claimMode,
    client_product_id: Number.isFinite(clientProductId) ? clientProductId : null,
  };
}

function insertListingStmt(donorId, l) {
  const id = randomUUID();
  const now = clock();
  return {
    id,
    l,
    stmt: {
      sql: `INSERT INTO listings (id, donor_id, title, category, quantity_text, notes, item_expiry, pickup_deadline, lat, lon,
              area_label, pickup_address, pickup_instructions, claim_mode, status, client_product_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'available', ?, ?, ?)`,
      args: [id, donorId, l.title, l.category, l.quantity_text, l.notes, l.item_expiry, l.pickup_deadline, l.lat, l.lon,
        l.area_label, l.pickup_address, l.pickup_instructions, l.claim_mode, l.client_product_id, now, now],
    },
  };
}

/** Largest alert radius a person can choose. */
const MAX_ALERT_KM = 50;

/**
 * Tells people who opted in to nearby alerts (notify_radius_km > 0 and an approximate location)
 * about new giveaways inside their own radius. A batch from one shop sends one alert, not one per item.
 * Only the bucketed distance is shared, never the exact pickup point. Never fails the listing request.
 */
async function notifyNearby(donor, created) {
  try {
    if (!created.length) return;
    const { lat, lon } = created[0].l;
    const dLat = MAX_ALERT_KM / 111.32;
    const dLon = MAX_ALERT_KM / (111.32 * Math.max(Math.cos((lat * Math.PI) / 180), 0.01));
    const rs = await db().execute({
      sql: `SELECT id, lat, lon, notify_radius_km FROM users
            WHERE id != ? AND notify_radius_km > 0 AND lat IS NOT NULL AND lon IS NOT NULL
              AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 500`,
      args: [donor.id, lat - dLat, lat + dLat, lon - dLon, lon + dLon],
    });
    const who = donor.account_type === 'shop' && donor.shop_name ? donor.shop_name : null;
    const now = clock();
    const stmts = [];
    for (const u of rs.rows) {
      const km = distanceKm(lat, lon, u.lat, u.lon);
      if (km > u.notify_radius_km) continue;
      const { distanceLabel } = publicDistance(km);
      const message = created.length === 1
        ? `New giveaway nearby: “${created[0].l.title}” — ${distanceLabel} away.`
        : `${who ?? 'A neighbour'} has ${created.length} items to give away — ${distanceLabel} away.`;
      stmts.push({
        sql: 'INSERT INTO events (user_id, type, listing_id, message, created_at) VALUES (?, ?, ?, ?, ?)',
        args: [u.id, 'nearby', created.length === 1 ? created[0].id : null, message, now],
      });
    }
    if (stmts.length) await db().batch(stmts, 'write');
  } catch (e) {
    console.error('notifyNearby failed', e);
  }
}

async function createListing(user, body) {
  const l = validateListing(body, user);
  const { id, stmt } = insertListingStmt(user.id, l);
  await db().execute(stmt);
  await notifyNearby(user, [{ id, l }]);
  return { listing: await loadView(id, user) };
}

/** Shops post several surplus items at once; all are created atomically or none are. */
async function createBatch(user, body) {
  const items = Array.isArray(body.items) ? body.items : [];
  if (!items.length) throw bad('missing_field', 'items must contain at least one listing');
  if (items.length > 50) throw bad('too_many', 'At most 50 items per batch');
  const shared = body.shared || {};
  const prepared = items.map((item) => insertListingStmt(user.id, validateListing({ ...shared, ...item }, user)));
  await db().batch(prepared.map((p) => p.stmt), 'write');
  await notifyNearby(user, prepared);
  const listings = [];
  for (const p of prepared) listings.push(await loadView(p.id, user));
  return { listings };
}

/** Marks unclaimed listings past their deadline as expired and tells their donors. */
async function expireStale() {
  const now = clock();
  const stale = await db().execute({
    sql: "SELECT id, donor_id, title FROM listings WHERE status = 'available' AND pickup_deadline <= ? LIMIT 200",
    args: [now],
  });
  for (const row of stale.rows) {
    const upd = await db().execute({
      sql: "UPDATE listings SET status = 'expired', updated_at = ? WHERE id = ? AND status = 'available' AND pickup_deadline <= ?",
      args: [now, row.id, now],
    });
    if (upd.rowsAffected === 1) {
      await db().execute({ sql: "UPDATE claim_requests SET status = 'rejected' WHERE listing_id = ? AND status = 'pending'", args: [row.id] });
      await addEvent(db(), row.donor_id, 'expired', row.id, `Nobody collected “${row.title}” before the deadline.`);
    }
  }
}

async function getListingRow(id) {
  const rs = await db().execute({
    sql: `SELECT l.*, d.name AS donor_name, d.account_type AS donor_type, d.shop_name AS donor_shop,
                 d.opening_hours AS donor_hours, c.name AS claimer_name
          FROM listings l JOIN users d ON d.id = l.donor_id LEFT JOIN users c ON c.id = l.claimed_by_user_id
          WHERE l.id = ?`,
    args: [id],
  });
  return rs.rows[0];
}

/**
 * Shapes a listing for one viewer. The exact pickup point, address, instructions and
 * pickup code are only included for the donor and for the person whose claim was confirmed.
 */
async function view(row, viewer, extra = {}) {
  const isDonor = viewer && row.donor_id === viewer.id;
  const isClaimer = viewer && row.claimed_by_user_id === viewer.id && ['reserved', 'picked_up'].includes(row.status);
  const out = {
    id: row.id,
    title: row.title,
    category: row.category,
    quantityText: row.quantity_text,
    notes: row.notes,
    itemExpiry: row.item_expiry,
    pickupDeadline: Number(row.pickup_deadline),
    areaLabel: row.area_label,
    claimMode: row.claim_mode,
    status: row.status,
    createdAt: Number(row.created_at),
    donor: {
      name: row.donor_type === 'shop' && row.donor_shop ? row.donor_shop : firstName(row.donor_name),
      isShop: row.donor_type === 'shop',
      openingHours: row.donor_type === 'shop' ? row.donor_hours ?? null : null,
    },
    approxLat: approx(Number(row.lat)),
    approxLon: approx(Number(row.lon)),
    isMine: Boolean(isDonor),
    claimedByMe: Boolean(isClaimer),
    ...extra,
  };
  if (isDonor || isClaimer) {
    out.pickup = {
      lat: Number(row.lat),
      lon: Number(row.lon),
      address: row.pickup_address,
      instructions: row.pickup_instructions,
      code: row.status === 'reserved' ? row.pickup_code : null,
    };
  }
  if (isDonor) {
    out.clientProductId = row.client_product_id == null ? null : Number(row.client_product_id);
    out.claimedBy = row.claimed_by_user_id ? { name: firstName(row.claimer_name) } : null;
    out.claimedAt = row.claimed_at == null ? null : Number(row.claimed_at);
    out.pickedUpAt = row.picked_up_at == null ? null : Number(row.picked_up_at);
    const reqs = await db().execute({
      sql: `SELECT r.id, r.message, r.status, r.created_at, u.name FROM claim_requests r JOIN users u ON u.id = r.requester_id
            WHERE r.listing_id = ? ORDER BY r.created_at`,
      args: [row.id],
    });
    out.requests = reqs.rows.map((r) => ({
      id: r.id, name: firstName(r.name), message: r.message, status: r.status, createdAt: Number(r.created_at),
    }));
  } else if (viewer && row.claim_mode === 'approval') {
    const mine = await db().execute({
      sql: 'SELECT status FROM claim_requests WHERE listing_id = ? AND requester_id = ?',
      args: [row.id, viewer.id],
    });
    out.myRequestStatus = mine.rows[0]?.status ?? null;
  }
  return out;
}

async function loadView(id, viewer, extra) {
  const row = await getListingRow(id);
  if (!row) throw new HttpError(404, 'not_found', 'This giveaway no longer exists');
  return view(row, viewer, extra);
}

async function listingDetail(user, id, query) {
  await expireStale();
  const row = await getListingRow(id);
  if (!row) throw new HttpError(404, 'not_found', 'This giveaway no longer exists');
  const participant = row.donor_id === user.id || row.claimed_by_user_id === user.id;
  if (row.status !== 'available' && !participant) {
    const requested = await db().execute({ sql: 'SELECT 1 FROM claim_requests WHERE listing_id = ? AND requester_id = ?', args: [id, user.id] });
    if (!requested.rows.length) throw new HttpError(404, 'not_found', 'This giveaway is no longer available');
  }
  const extra = {};
  if (query.lat != null && query.lon != null) {
    Object.assign(extra, publicDistance(distanceKm(Number(query.lat), Number(query.lon), Number(row.lat), Number(row.lon))));
  }
  return { listing: await view(row, user, extra) };
}

async function nearby(user, query) {
  await expireStale();
  const lat = coord(query.lat, 'lat', -90, 90);
  const lon = coord(query.lon, 'lon', -180, 180);
  const radius = Math.min(Math.max(Number(query.radiusKm) || 5, 0.5), 50);
  const sort = query.sort === 'urgency' ? 'urgency' : 'distance';
  // Bounding-box prefilter in SQL, exact great-circle distance in code.
  const dLat = radius / 111.32;
  const dLon = radius / (111.32 * Math.max(Math.cos((lat * Math.PI) / 180), 0.01));
  const args = [lat - dLat, lat + dLat, lon - dLon, lon + dLon, user.id, clock()];
  let categoryFilter = '';
  if (query.category) { categoryFilter = 'AND l.category = ?'; args.push(String(query.category)); }
  const rs = await db().execute({
    sql: `SELECT l.*, d.name AS donor_name, d.account_type AS donor_type, d.shop_name AS donor_shop,
                 d.opening_hours AS donor_hours, NULL AS claimer_name
          FROM listings l JOIN users d ON d.id = l.donor_id
          WHERE l.status = 'available' AND l.lat BETWEEN ? AND ? AND l.lon BETWEEN ? AND ?
            AND l.donor_id <> ? AND l.pickup_deadline > ? ${categoryFilter}
          LIMIT 500`,
    args,
  });
  const urgency = (r) => {
    const expiry = r.item_expiry ? Date.parse(`${r.item_expiry}T23:59:59Z`) : Infinity;
    return Math.min(Number(r.pickup_deadline), expiry);
  };
  const withDistance = rs.rows
    .map((r) => ({ r, km: distanceKm(lat, lon, Number(r.lat), Number(r.lon)) }))
    .filter((x) => x.km <= radius)
    .sort((a, b) => (sort === 'urgency' ? urgency(a.r) - urgency(b.r) || a.km - b.km : a.km - b.km || urgency(a.r) - urgency(b.r)))
    .slice(0, Math.min(Number(query.limit) || 100, 200));
  const listings = [];
  for (const x of withDistance) listings.push(await view(x.r, user, publicDistance(x.km)));
  return { listings, radiusKm: radius, sort };
}

async function myListings(user) {
  await expireStale();
  const rs = await db().execute({
    sql: `SELECT l.*, d.name AS donor_name, d.account_type AS donor_type, d.shop_name AS donor_shop,
                 d.opening_hours AS donor_hours, c.name AS claimer_name
          FROM listings l JOIN users d ON d.id = l.donor_id LEFT JOIN users c ON c.id = l.claimed_by_user_id
          WHERE l.donor_id = ? ORDER BY l.created_at DESC LIMIT 200`,
    args: [user.id],
  });
  const listings = [];
  for (const r of rs.rows) listings.push(await view(r, user));
  return { listings };
}

async function myClaims(user) {
  await expireStale();
  const rs = await db().execute({
    sql: `SELECT DISTINCT l.*, d.name AS donor_name, d.account_type AS donor_type, d.shop_name AS donor_shop,
                 d.opening_hours AS donor_hours, NULL AS claimer_name
          FROM listings l JOIN users d ON d.id = l.donor_id
          LEFT JOIN claim_requests r ON r.listing_id = l.id AND r.requester_id = ?
          WHERE l.claimed_by_user_id = ? OR r.id IS NOT NULL
          ORDER BY l.updated_at DESC LIMIT 200`,
    args: [user.id, user.id],
  });
  const listings = [];
  for (const r of rs.rows) listings.push(await view(r, user));
  return { listings };
}

/** Explains why a conditional update matched no row. */
async function claimFailure(id, user) {
  const row = await getListingRow(id);
  if (!row) return new HttpError(404, 'not_found', 'This giveaway no longer exists');
  if (row.donor_id === user.id) return new HttpError(403, 'own_listing', 'You can’t claim your own giveaway');
  if (row.status === 'reserved' || row.status === 'picked_up') return new HttpError(409, 'already_claimed', 'Sorry — someone else just claimed this');
  if (row.status !== 'available' || Number(row.pickup_deadline) <= clock()) return new HttpError(410, 'not_available', 'This giveaway is no longer available');
  if (row.claim_mode !== 'direct') return bad('approval_required', 'This donor reviews requests — send a request instead');
  return new HttpError(429, 'too_many_reservations', `You can hold at most ${MAX_ACTIVE_RESERVATIONS} reserved items at once`);
}

/** First-come, first-served claim: a single atomic compare-and-set. */
async function claim(user, id) {
  await expireStale();
  const now = clock();
  const upd = await db().execute({
    sql: `UPDATE listings
          SET status = 'reserved', claimed_by_user_id = ?, claimed_at = ?, pickup_code = ?, updated_at = ?
          WHERE id = ? AND status = 'available' AND claim_mode = 'direct' AND donor_id <> ? AND pickup_deadline > ?
            AND (SELECT COUNT(*) FROM listings WHERE claimed_by_user_id = ? AND status = 'reserved') < ?`,
    args: [user.id, now, pickupCode(), now, id, user.id, now, user.id, MAX_ACTIVE_RESERVATIONS],
  });
  if (upd.rowsAffected !== 1) throw await claimFailure(id, user);
  const row = await getListingRow(id);
  await addEvent(db(), row.donor_id, 'claimed', id, `${firstName(user.name)} claimed “${row.title}”. Pickup code ${row.pickup_code}.`);
  return { listing: await view(row, user) };
}

async function requestClaim(user, id, body) {
  await expireStale();
  const row = await getListingRow(id);
  if (!row) throw new HttpError(404, 'not_found', 'This giveaway no longer exists');
  if (row.donor_id === user.id) throw new HttpError(403, 'own_listing', 'You can’t request your own giveaway');
  if (row.status !== 'available') throw new HttpError(409, 'not_available', 'This giveaway is no longer available');
  if (row.claim_mode !== 'approval') throw bad('direct_claim', 'This giveaway is first-come, first-served — claim it directly');
  const message = str(body.message, 'message', { max: 200 });
  const existing = await db().execute({ sql: 'SELECT status FROM claim_requests WHERE listing_id = ? AND requester_id = ?', args: [id, user.id] });
  const status = existing.rows[0]?.status;
  if (status === 'pending') return { listing: await view(row, user) };
  if (status === 'rejected') throw new HttpError(409, 'request_declined', 'The donor has already chosen someone else for this item');
  if (status === 'withdrawn') {
    await db().execute({
      sql: "UPDATE claim_requests SET status = 'pending', message = ?, created_at = ?, auto_closed = 0 WHERE listing_id = ? AND requester_id = ?",
      args: [message, clock(), id, user.id],
    });
  } else {
    await db().execute({
      sql: "INSERT INTO claim_requests (id, listing_id, requester_id, message, status, created_at) VALUES (?, ?, ?, ?, 'pending', ?)",
      args: [randomUUID(), id, user.id, message, clock()],
    });
  }
  await addEvent(db(), row.donor_id, 'request', id, `${firstName(user.name)} asked for “${row.title}”${message ? `: “${message}”` : '.'}`);
  return { listing: await view(row, user) };
}

async function withdrawRequest(user, id) {
  await db().execute({
    sql: "UPDATE claim_requests SET status = 'withdrawn' WHERE listing_id = ? AND requester_id = ? AND status = 'pending'",
    args: [id, user.id],
  });
  return { listing: await loadView(id, user) };
}

/**
 * Donor accepts one request. In one write transaction: reserve the listing (only if it
 * is still available), accept this request and close every other pending request.
 */
async function acceptRequest(user, listingId, requestId) {
  const tx = await db().transaction('write');
  let row; let accepted; let closed;
  try {
    const req = await tx.execute({
      sql: `SELECT r.requester_id, u.name FROM claim_requests r JOIN users u ON u.id = r.requester_id
            WHERE r.id = ? AND r.listing_id = ? AND r.status = 'pending'`,
      args: [requestId, listingId],
    });
    if (!req.rows.length) throw new HttpError(409, 'request_not_pending', 'This request is no longer pending');
    accepted = req.rows[0];
    const now = clock();
    const upd = await tx.execute({
      sql: `UPDATE listings SET status = 'reserved', claimed_by_user_id = ?, claimed_at = ?, pickup_code = ?, updated_at = ?
            WHERE id = ? AND donor_id = ? AND status = 'available'`,
      args: [accepted.requester_id, now, pickupCode(), now, listingId, user.id],
    });
    if (upd.rowsAffected !== 1) throw new HttpError(409, 'not_available', 'This giveaway is no longer available');
    await tx.execute({ sql: "UPDATE claim_requests SET status = 'accepted' WHERE id = ?", args: [requestId] });
    closed = (await tx.execute({
      sql: "SELECT requester_id FROM claim_requests WHERE listing_id = ? AND id <> ? AND status = 'pending'",
      args: [listingId, requestId],
    })).rows;
    await tx.execute({
      sql: "UPDATE claim_requests SET status = 'rejected', auto_closed = 1 WHERE listing_id = ? AND id <> ? AND status = 'pending'",
      args: [listingId, requestId],
    });
    row = (await tx.execute({ sql: 'SELECT title, pickup_code FROM listings WHERE id = ?', args: [listingId] })).rows[0];
    await addEvent(tx, accepted.requester_id, 'accepted', listingId, `Your request for “${row.title}” was accepted! Pickup code ${row.pickup_code}.`);
    for (const c of closed) {
      await addEvent(tx, c.requester_id, 'rejected', listingId, `“${row.title}” went to someone else this time.`);
    }
    await tx.commit();
  } catch (e) {
    await tx.rollback().catch(() => {});
    throw e;
  } finally {
    tx.close();
  }
  return { listing: await loadView(listingId, user) };
}

async function rejectRequest(user, listingId, requestId) {
  const own = await db().execute({ sql: 'SELECT title FROM listings WHERE id = ? AND donor_id = ?', args: [listingId, user.id] });
  if (!own.rows.length) throw new HttpError(404, 'not_found', 'Listing not found');
  const req = await db().execute({ sql: 'SELECT requester_id FROM claim_requests WHERE id = ? AND listing_id = ? AND status = ?', args: [requestId, listingId, 'pending'] });
  if (!req.rows.length) throw new HttpError(409, 'request_not_pending', 'This request is no longer pending');
  await db().execute({ sql: "UPDATE claim_requests SET status = 'rejected', auto_closed = 0 WHERE id = ? AND status = 'pending'", args: [requestId] });
  await addEvent(db(), req.rows[0].requester_id, 'rejected', listingId, `Your request for “${own.rows[0].title}” wasn’t accepted.`);
  return { listing: await loadView(listingId, user) };
}

/**
 * Puts a reserved listing back on the feed (claimer didn't show up, or cancelled).
 * Requests that were auto-closed when someone was accepted go back into the queue.
 */
async function reopen(listingId, row, by, reason, newDeadline) {
  const now = clock();
  const deadline = newDeadline ?? Number(row.pickup_deadline);
  if (deadline <= now) throw bad('invalid_deadline', 'The pickup deadline has passed — choose a new deadline to relist');
  if (deadline > now + MAX_DEADLINE_MS) throw bad('invalid_deadline', 'Pickup deadline can be at most 14 days away');
  const upd = await db().execute({
    sql: `UPDATE listings SET status = 'available', claimed_by_user_id = NULL, claimed_at = NULL, pickup_code = NULL,
            pickup_deadline = ?, updated_at = ? WHERE id = ? AND status = 'reserved' AND claimed_by_user_id = ?`,
    args: [deadline, now, listingId, row.claimed_by_user_id],
  });
  if (upd.rowsAffected !== 1) throw new HttpError(409, 'not_reserved', 'This giveaway is not reserved any more');
  await db().execute({
    sql: "UPDATE claim_requests SET status = ? WHERE listing_id = ? AND requester_id = ? AND status = 'accepted'",
    args: [by === 'claimer' ? 'withdrawn' : 'rejected', listingId, row.claimed_by_user_id],
  });
  const reopened = (await db().execute({
    sql: "SELECT requester_id FROM claim_requests WHERE listing_id = ? AND status = 'rejected' AND auto_closed = 1",
    args: [listingId],
  })).rows;
  await db().execute({
    sql: "UPDATE claim_requests SET status = 'pending', auto_closed = 0 WHERE listing_id = ? AND status = 'rejected' AND auto_closed = 1",
    args: [listingId],
  });
  for (const r of reopened) {
    await addEvent(db(), r.requester_id, 'requeued', listingId, `“${row.title}” is available again and your request is back in the queue.`);
  }
  return reason;
}

async function release(user, id, body) {
  const row = await getListingRow(id);
  if (!row || row.donor_id !== user.id) throw new HttpError(404, 'not_found', 'Listing not found');
  if (row.status !== 'reserved') throw new HttpError(409, 'not_reserved', 'Only reserved giveaways can be released');
  const newDeadline = body.pickupDeadline == null ? null : Number(body.pickupDeadline);
  await reopen(id, row, 'donor', 'released', newDeadline);
  await addEvent(db(), row.claimed_by_user_id, 'released', id, `Your reservation for “${row.title}” was released by the donor.`);
  return { listing: await loadView(id, user) };
}

async function cancelClaim(user, id) {
  const row = await getListingRow(id);
  if (!row || row.claimed_by_user_id !== user.id || row.status !== 'reserved') {
    throw new HttpError(409, 'not_reserved', 'You don’t have a reservation for this giveaway');
  }
  const deadline = Number(row.pickup_deadline);
  if (deadline <= clock()) {
    // Too late to relist: the giveaway simply expires.
    await db().execute({
      sql: "UPDATE listings SET status = 'expired', claimed_by_user_id = NULL, pickup_code = NULL, updated_at = ? WHERE id = ? AND status = 'reserved'",
      args: [clock(), id],
    });
  } else {
    await reopen(id, row, 'claimer', 'cancelled');
  }
  await addEvent(db(), row.donor_id, 'claim_cancelled', id, `${firstName(user.name)} can’t collect “${row.title}” after all — it’s back on the feed.`);
  return { ok: true };
}

async function markPickedUp(user, id) {
  const row = await getListingRow(id);
  if (!row || (row.donor_id !== user.id && row.claimed_by_user_id !== user.id)) throw new HttpError(404, 'not_found', 'Listing not found');
  const now = clock();
  const upd = await db().execute({
    sql: "UPDATE listings SET status = 'picked_up', picked_up_at = ?, updated_at = ? WHERE id = ? AND status = 'reserved'",
    args: [now, now, id],
  });
  if (upd.rowsAffected !== 1) throw new HttpError(409, 'not_reserved', 'Only reserved giveaways can be marked as picked up');
  const other = user.id === row.donor_id ? row.claimed_by_user_id : row.donor_id;
  await addEvent(db(), other, 'picked_up', id, `“${row.title}” was marked as picked up. Thanks for rescuing food!`);
  return { listing: await loadView(id, user) };
}

async function cancelListing(user, id) {
  const row = await getListingRow(id);
  if (!row || row.donor_id !== user.id) throw new HttpError(404, 'not_found', 'Listing not found');
  const upd = await db().execute({
    sql: "UPDATE listings SET status = 'cancelled', pickup_code = NULL, updated_at = ? WHERE id = ? AND status IN ('available', 'reserved')",
    args: [clock(), id],
  });
  if (upd.rowsAffected !== 1) throw new HttpError(409, 'not_active', 'This giveaway is already closed');
  const pending = (await db().execute({ sql: "SELECT requester_id FROM claim_requests WHERE listing_id = ? AND status = 'pending'", args: [id] })).rows;
  await db().execute({ sql: "UPDATE claim_requests SET status = 'rejected' WHERE listing_id = ? AND status = 'pending'", args: [id] });
  for (const p of pending) await addEvent(db(), p.requester_id, 'cancelled', id, `“${row.title}” was withdrawn by the donor.`);
  if (row.status === 'reserved' && row.claimed_by_user_id) {
    await addEvent(db(), row.claimed_by_user_id, 'cancelled', id, `“${row.title}” was withdrawn by the donor — sorry!`);
  }
  return { listing: await loadView(id, user) };
}

async function events(user, query) {
  await expireStale();
  const after = Number(query.after) || 0;
  const rs = await db().execute({
    sql: 'SELECT id, type, listing_id, message, created_at FROM events WHERE user_id = ? AND id > ? ORDER BY id LIMIT 50',
    args: [user.id, after],
  });
  return {
    events: rs.rows.map((e) => ({
      id: Number(e.id), type: e.type, listingId: e.listing_id, message: e.message, createdAt: Number(e.created_at),
    })),
  };
}

async function myStats(user) {
  await expireStale();
  const rs = await db().execute({ sql: 'SELECT status, COUNT(*) AS n FROM listings WHERE donor_id = ? GROUP BY status', args: [user.id] });
  const byStatus = Object.fromEntries(rs.rows.map((r) => [r.status, Number(r.n)]));
  const pending = await db().execute({
    sql: `SELECT COUNT(*) AS n FROM claim_requests r JOIN listings l ON l.id = r.listing_id
          WHERE l.donor_id = ? AND r.status = 'pending' AND l.status = 'available'`,
    args: [user.id],
  });
  return {
    available: byStatus.available ?? 0,
    reserved: byStatus.reserved ?? 0,
    pickedUp: byStatus.picked_up ?? 0,
    expired: byStatus.expired ?? 0,
    cancelled: byStatus.cancelled ?? 0,
    pendingRequests: Number(pending.rows[0].n),
  };
}

// ---------------------------------------------------------------------------------
// Admin (protected by the ADMIN_KEY environment variable)
// ---------------------------------------------------------------------------------

function requireAdmin(req) {
  const key = process.env.ADMIN_KEY;
  if (!key) throw new HttpError(503, 'admin_disabled', 'Set the ADMIN_KEY environment variable to enable the admin dashboard');
  const given = String(req.headers['x-admin-key'] || '');
  const a = Buffer.from(sha256(given));
  const b = Buffer.from(sha256(key));
  if (!timingSafeEqual(a, b)) throw new HttpError(401, 'bad_admin_key', 'Wrong admin key');
}

async function adminOverview() {
  await expireStale();
  const q = async (sql, args = []) => (await db().execute({ sql, args })).rows;
  const [users] = await q("SELECT COUNT(*) AS total, SUM(account_type = 'shop') AS shops FROM users");
  const statuses = await q('SELECT status, COUNT(*) AS n FROM listings GROUP BY status');
  const listings = await q(`SELECT l.id, l.title, l.status, l.claim_mode, l.area_label, l.pickup_deadline, l.created_at,
                                   d.email AS donor_email, d.account_type AS donor_type, c.email AS claimer_email
                            FROM listings l JOIN users d ON d.id = l.donor_id LEFT JOIN users c ON c.id = l.claimed_by_user_id
                            ORDER BY l.created_at DESC LIMIT 100`);
  const recentUsers = await q('SELECT email, name, account_type, shop_name, created_at FROM users ORDER BY created_at DESC LIMIT 50');
  return {
    users: { total: Number(users.total), shops: Number(users.shops ?? 0) },
    listingsByStatus: Object.fromEntries(statuses.map((s) => [s.status, Number(s.n)])),
    listings: listings.map((l) => ({ ...l, pickup_deadline: Number(l.pickup_deadline), created_at: Number(l.created_at) })),
    recentUsers: recentUsers.map((u) => ({ ...u, created_at: Number(u.created_at) })),
  };
}

async function adminRemove(id) {
  const upd = await db().execute({
    sql: "UPDATE listings SET status = 'cancelled', pickup_code = NULL, updated_at = ? WHERE id = ? AND status IN ('available','reserved')",
    args: [clock(), id],
  });
  if (upd.rowsAffected !== 1) throw new HttpError(409, 'not_active', 'Listing is not active');
  await db().execute({ sql: "UPDATE claim_requests SET status = 'rejected' WHERE listing_id = ? AND status = 'pending'", args: [id] });
  return { ok: true };
}

// ---------------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------------

const routes = [];
const route = (method, pattern, auth, fn) => {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '/?$');
  routes.push({ method, re, keys, auth, fn });
};

route('GET', '/health', false, async () => {
  await db().execute('SELECT 1');
  return { ok: true, service: 'pantrypulse-api', time: clock() };
});
route('POST', '/auth/register', false, (c) => register(c.body));
route('POST', '/auth/login', false, (c) => login(c.body));
route('POST', '/auth/connect', false, (c) => connect(c.body));
route('POST', '/auth/logout', true, async (c) => {
  const token = /^Bearer\s+(.+)$/i.exec(c.req.headers.authorization)[1];
  await db().execute({ sql: 'DELETE FROM sessions WHERE token_hash = ?', args: [sha256(token)] });
  return { ok: true };
});
route('GET', '/me', true, (c) => ({ user: userView(c.user) }));
route('PATCH', '/me', true, (c) => updateMe(c.user, c.body));
route('GET', '/me/stats', true, (c) => myStats(c.user));
route('GET', '/events', true, (c) => events(c.user, c.query));
route('POST', '/listings', true, (c) => createListing(c.user, c.body));
route('POST', '/listings/batch', true, (c) => createBatch(c.user, c.body));
route('GET', '/listings/nearby', true, (c) => nearby(c.user, c.query));
route('GET', '/listings/mine', true, (c) => myListings(c.user));
route('GET', '/claims/mine', true, (c) => myClaims(c.user));
route('GET', '/listings/:id', true, (c) => listingDetail(c.user, c.params.id, c.query));
route('POST', '/listings/:id/claim', true, (c) => claim(c.user, c.params.id));
route('POST', '/listings/:id/requests', true, (c) => requestClaim(c.user, c.params.id, c.body));
route('DELETE', '/listings/:id/requests', true, (c) => withdrawRequest(c.user, c.params.id));
route('POST', '/listings/:id/requests/:rid/accept', true, (c) => acceptRequest(c.user, c.params.id, c.params.rid));
route('POST', '/listings/:id/requests/:rid/reject', true, (c) => rejectRequest(c.user, c.params.id, c.params.rid));
route('POST', '/listings/:id/release', true, (c) => release(c.user, c.params.id, c.body));
route('POST', '/listings/:id/cancel-claim', true, (c) => cancelClaim(c.user, c.params.id));
route('POST', '/listings/:id/picked-up', true, (c) => markPickedUp(c.user, c.params.id));
route('POST', '/listings/:id/cancel', true, (c) => cancelListing(c.user, c.params.id));
route('POST', '/ai/medicine', false, async (c) => {
  const fwd = String(c.req.headers['x-forwarded-for'] ?? '').split(',')[0].trim();
  try {
    return await medicineInfo(c.body, { ip: fwd || c.req.socket?.remoteAddress || 'unknown' });
  } catch (e) {
    if (e instanceof AiError) throw new HttpError(e.status, e.code, e.message);
    throw e;
  }
});
route('GET', '/admin/api/overview', false, (c) => { requireAdmin(c.req); return adminOverview(); });
route('POST', '/admin/api/listings/:id/remove', false, (c) => { requireAdmin(c.req); return adminRemove(c.params.id); });

async function readBody(req) {
  if (req.body && typeof req.body === 'object' && !Buffer.isBuffer(req.body)) return req.body;
  if (typeof req.body === 'string') return req.body ? JSON.parse(req.body) : {};
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString('utf8');
  if (!raw) return {};
  try { return JSON.parse(raw); } catch { throw bad('invalid_json', 'Request body must be JSON'); }
}

function send(res, status, payload, type = 'application/json; charset=utf-8') {
  res.statusCode = status;
  res.setHeader('Content-Type', type);
  res.setHeader('Cache-Control', 'no-store');
  res.end(type.startsWith('application/json') ? JSON.stringify(payload) : payload);
}

export default async function handler(req, res) {
  const url = new URL(req.url, 'http://localhost');
  // On Vercel every path is rewritten to /api?__route=<original path> (see vercel.json).
  const forwarded = url.searchParams.get('__route');
  url.searchParams.delete('__route');
  const path = (forwarded ?? url.pathname).replace(/^\/api(?=\/|$)/, '') || '/';
  try {
    if (req.method === 'GET' && (path === '/admin' || path === '/admin/')) return send(res, 200, adminPage, 'text/html; charset=utf-8');
    if (req.method === 'GET' && path === '/') {
      return send(res, 200, { service: 'pantrypulse-api', docs: 'https://github.com/shajaurahman1/PantryPulse/blob/main/backend/README.md' });
    }
    const match = routes.find((r) => r.method === req.method && r.re.test(path));
    if (!match) throw new HttpError(404, 'not_found', `No route for ${req.method} ${path}`);
    await ensureSchema();
    const m = match.re.exec(path);
    const params = Object.fromEntries(match.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]));
    const ctx = { req, params, query: Object.fromEntries(url.searchParams), body: ['POST', 'PATCH', 'PUT', 'DELETE'].includes(req.method) ? await readBody(req) : {} };
    if (match.auth) ctx.user = await authUser(req);
    send(res, 200, await match.fn(ctx));
  } catch (e) {
    if (e instanceof HttpError) return send(res, e.status, { error: e.code, message: e.message });
    console.error(e);
    send(res, 500, { error: 'server_error', message: 'Something went wrong on our side' });
  }
}
