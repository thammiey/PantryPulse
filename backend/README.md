# PantryPulse community backend

The online service behind **Giveaways** (peer-to-peer food sharing) and **shop accounts**.
Everything else in PantryPulse (pantry, shopping list, recipes) stays on the phone.

- **API:** Node.js serverless function on Vercel — project `pantrypulse-api`, region `bom1` (Mumbai)
- **Database:** Turso (libSQL/SQLite) — database **`pantrypulse`** only, in `aws-ap-south-1`
- **Admin dashboard:** `https://pantry-pulse-backend.vercel.app/admin`

## How to access the backend

| What you want | Where |
|---|---|
| See or edit raw data (users, listings, requests) | [Turso dashboard](https://app.turso.tech) → database **pantrypulse** → *Edit data* / *SQL shell* |
| Moderate giveaways, see counts, newest users | `https://pantry-pulse-backend.vercel.app/admin` (enter your `ADMIN_KEY`) |
| Deployments, logs, environment variables | [Vercel dashboard](https://vercel.com) → project **pantrypulse-api** |
| Check the API is up | `https://pantry-pulse-backend.vercel.app/health` |

## One-time setup (secrets)

Secrets are never stored in the repository. Add them in **Vercel → pantrypulse-api → Settings → Environment Variables** (Production, Preview and Development), then redeploy:

| Variable | Value |
|---|---|
| `TURSO_DATABASE_URL` | `libsql://pantrypulse-shajaurahman1.aws-ap-south-1.turso.io` (already set) |
| `TURSO_AUTH_TOKEN` | Turso dashboard → database **pantrypulse** → *Create Token* (read & write). **Create it on the database, not the group**, so it can't reach any other database in your account. |
| `ADMIN_KEY` | Any long random password of your choice; you type it on the `/admin` page. |

## How claiming stays fair

- **First come, first served:** a claim is one SQL statement —
  `UPDATE listings SET status='reserved', claimed_by_user_id=? … WHERE id=? AND status='available' …`.
  The database applies writes one at a time, so if two people tap *Claim* in the same instant exactly
  one update matches the row; the other gets `409 already_claimed`.
- **Require donor approval:** requests queue in `claim_requests`. Accepting one runs a write
  transaction that reserves the listing (only if still available), marks that request `accepted` and
  every other pending request `rejected` — all or nothing.
- **Lifecycle:** `available → reserved → picked_up`. Unclaimed listings past their pickup deadline
  become `expired`. The donor can **release** a reservation (no-show) back to `available`; requests that
  were auto-closed go back into the queue. The claimer can cancel too. Donors can withdraw (`cancelled`).
- **Privacy:** the public feed only has an area label (e.g. "Near City Library"), a distance rounded to
  0.5 km and a map point snapped to ~1 km. The exact address, coordinates, instructions and a 4-digit
  pickup code are only returned to the donor and to the person whose claim was confirmed.
- One person can hold at most 5 reservations at a time.

## API

All endpoints take/return JSON; authenticated ones need `Authorization: Bearer <token>`.

| Method & path | Purpose |
|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/connect` | Accounts (`connect` = login, or register the first time) |
| `GET /me`, `PATCH /me`, `GET /me/stats` | Profile (person or shop: name, address, opening hours, location) and donor stats |
| `POST /listings`, `POST /listings/batch` | Create one giveaway, or several at once (shops) |
| `GET /listings/nearby?lat&lon&radiusKm&sort=distance\|urgency` | Public feed within 0.5–50 km |
| `GET /listings/mine`, `GET /claims/mine`, `GET /listings/:id` | Donor's listings (with request queue), my claims, one listing |
| `POST /listings/:id/claim` | Direct claim (atomic) |
| `POST /listings/:id/requests`, `DELETE /listings/:id/requests` | Request / withdraw (approval mode) |
| `POST /listings/:id/requests/:rid/accept`, `…/reject` | Donor decides (accept closes all others) |
| `POST /listings/:id/release`, `/cancel-claim`, `/picked-up`, `/cancel` | Lifecycle |
| `GET /events?after=<id>` | Activity feed the app turns into notifications |

## Develop & test

```bash
cd backend
npm install
npm test                                   # API tests incl. 30 simultaneous claims → exactly one winner
TURSO_DATABASE_URL=file:local.db npm run dev   # local server on http://localhost:8787
```
