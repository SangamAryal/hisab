# Running the Hisab server for free

The whole backend is one PocketBase process with a SQLite file, so a tiny
free VM is plenty for thousands of users.

## Quickest: Railway (no card, about 10 minutes)

Railway builds the server straight from this GitHub repo and gives it an
HTTPS address, so there's no VM, domain or SSH. The free plan needs no card:
new accounts get $5 of credit for the first 30 days, then $1 a month.
Hisab's server is small (about 30 MB of memory) and should fit in that, but
if a month's credit runs out the server pauses until the next month, so move
to a free VM (below) before real users depend on it.

1. Go to https://railway.com and sign in with GitHub.
2. **New Project → Deploy from GitHub repo → SangamAryal/hisab.** Railway
   reads `railway.json` and builds `backend/Dockerfile` on its own.
3. Open the new service. Right-click it (or use the command palette) →
   **Add Volume**, mount path **`/pb_data`**. Without this, every redeploy
   wipes the data.
4. **Variables**: add `HISAB_ADMIN_EMAIL` and `HISAB_ADMIN_PASSWORD` (10+
   characters) for the admin dashboard.
5. **Settings → Networking → Generate Domain.** You get an address like
   `hisab-production-1234.up.railway.app`.
6. Open `https://<that address>/api/health`. It should say "API is
   healthy". The dashboard is at `https://<that address>/_/`.

Then do step 4 below ("Point the app at it") with that address. Each push to
`main` redeploys it automatically.

## 1. Get a free server

**Oracle Cloud Always Free** (recommended): an Ampere (ARM) VM with up to
2 OCPU / 12 GB RAM and 200 GB disk, free forever. Sign-up asks for a card
to verify you; Always Free resources are never charged.

1. Sign up at https://www.oracle.com/cloud/free/ and pick a home region close
   to your users (e.g. Mumbai or Hyderabad for Nepal/India).
2. Compute → Instances → Create: image **Ubuntu 24.04**, shape
   **VM.Standard.A1.Flex** with 1 OCPU / 6 GB (well within the free limit).
   Add your SSH key.
3. In the instance's subnet → Security List, add ingress rules for TCP **80**
   and **443** from `0.0.0.0/0`.

Fallback: Google Cloud's free **e2-micro** in us-west1/us-central1/us-east1
(also card-verified, also free).

## 2. Give it a name

The app needs HTTPS, which needs a domain name. Free options:

- **sslip.io**: if your server's IP is `140.238.1.2`, use
  `140-238-1-2.sslip.io`. Nothing to register.
- A free subdomain from DuckDNS, or a real domain later (~$10/year).

## 3. Install

SSH into the server and run:

```bash
curl -fsSL https://raw.githubusercontent.com/SangamAryal/hisab/main/deploy/setup.sh | bash -s -- 140-238-1-2.sslip.io
sudo /opt/hisab/repo/backend/pocketbase superuser upsert you@example.com 'a-long-password' --dir=/opt/hisab/data
sudo chown -R hisab:hisab /opt/hisab/data && sudo systemctl restart hisab
```

Open `https://140-238-1-2.sslip.io/_/` to see the admin dashboard.

## 4. Point the app at it

Build with `-Phisab.apiUrl=https://140-238-1-2.sslip.io`, or set it once in
`gradle.properties`. In GitHub, set the repository variable
`HISAB_API_URL` so CI builds use it too.

## Updating

```bash
cd /opt/hisab/repo && git pull && sudo systemctl restart hisab
```

Migrations run automatically on start.

## Backups

PocketBase has built-in backups: Dashboard → Settings → Backups. Turn on a
daily schedule, and point it at free S3-compatible storage (Cloudflare R2
gives 10 GB free) so a dead VM can't take the data with it.

## Email (optional)

Not needed for v0.1, which has no email sign-in. When it's added, put any
free SMTP (Brevo, Resend) in Dashboard → Settings → Mail settings.
