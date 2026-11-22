-- PantryPulse community schema (SQLite / Turso libSQL).
CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name          TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  account_type  TEXT NOT NULL DEFAULT 'person' CHECK (account_type IN ('person','shop')),
  shop_name     TEXT,
  shop_address  TEXT,
  opening_hours TEXT,
  lat           REAL,
  lon           REAL,
  notify_radius_km REAL NOT NULL DEFAULT 0,  -- 0 = no nearby-giveaway alerts, and lat/lon then holds the person approximate location
  created_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL
);

-- One row per giveaway. status: available -> reserved -> picked_up,
-- or expired (unclaimed past its deadline) / cancelled (withdrawn by the donor).
CREATE TABLE IF NOT EXISTS listings (
  id                TEXT PRIMARY KEY,
  donor_id          TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title             TEXT NOT NULL,
  category          TEXT NOT NULL DEFAULT 'OTHER',
  quantity_text     TEXT NOT NULL DEFAULT '',
  notes             TEXT NOT NULL DEFAULT '',
  item_expiry       TEXT,              -- ISO date the food itself expires
  pickup_deadline   INTEGER NOT NULL,  -- epoch ms; unclaimed after this -> expired
  lat               REAL NOT NULL,     -- exact pickup point, never sent to the public feed
  lon               REAL NOT NULL,
  area_label        TEXT NOT NULL DEFAULT '',  -- public, e.g. "Near City Library"
  pickup_address    TEXT NOT NULL DEFAULT '',  -- private until a claim is confirmed
  pickup_instructions TEXT NOT NULL DEFAULT '', -- private until a claim is confirmed
  claim_mode        TEXT NOT NULL CHECK (claim_mode IN ('direct','approval')),
  status            TEXT NOT NULL DEFAULT 'available'
                    CHECK (status IN ('available','reserved','picked_up','expired','cancelled')),
  claimed_by_user_id TEXT REFERENCES users(id),
  claimed_at        INTEGER,
  pickup_code       TEXT,
  picked_up_at      INTEGER,
  client_product_id INTEGER,
  created_at        INTEGER NOT NULL,
  updated_at        INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS listings_status_geo ON listings(status, lat, lon);
CREATE INDEX IF NOT EXISTS listings_donor ON listings(donor_id);
CREATE INDEX IF NOT EXISTS listings_claimer ON listings(claimed_by_user_id);

-- Requests for "Require donor approval" listings. Accepting one rejects the rest.
CREATE TABLE IF NOT EXISTS claim_requests (
  id           TEXT PRIMARY KEY,
  listing_id   TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  requester_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message      TEXT NOT NULL DEFAULT '',
  status       TEXT NOT NULL DEFAULT 'pending'
               CHECK (status IN ('pending','accepted','rejected','withdrawn')),
  auto_closed  INTEGER NOT NULL DEFAULT 0,  -- 1 = closed because someone else was accepted
  created_at   INTEGER NOT NULL,
  UNIQUE (listing_id, requester_id)
);
CREATE INDEX IF NOT EXISTS requests_listing ON claim_requests(listing_id, status);

-- Per-user activity feed that the app polls to show notifications.
CREATE TABLE IF NOT EXISTS events (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type       TEXT NOT NULL,
  listing_id TEXT,
  message    TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS events_user ON events(user_id, id);
