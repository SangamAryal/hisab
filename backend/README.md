# Hisab backend

Stock [PocketBase](https://pocketbase.io) (one binary: SQLite database, auth,
file storage, realtime and an admin dashboard) plus:

- `pb_migrations/` – the database schema and access rules.
- `pb_hooks/` – custom API routes, validation and the recurring-bill job.

There is no custom Go code, so you always run the official PocketBase binary.

## Run it locally

```bash
cd backend
./get-pocketbase.sh            # downloads PocketBase 0.40.4 for your machine
./pocketbase serve             # http://127.0.0.1:8090 , dashboard at /_/
```

On first start PocketBase applies the migrations and prints a link to create
the admin (superuser) account.

## Test

```bash
npm test                        # needs Node 20+; starts a throwaway PocketBase
```

## Data model

All money is stored as **integers in the currency's minor unit** (paisa, cents).

| Collection | What it is |
|---|---|
| `users` | App accounts. The app creates a silent guest account on first launch, so nobody has to sign up. |
| `groups` | A shared tab (flat, trip, couple). Has a currency and a hidden `invite_code`. |
| `members` | A person inside one group. Can exist before they install the app (added by name); `user` is set when they join. |
| `expenses` | Who paid, how much, and `splits`: `[{"member": id, "amount": n}]` that must add up to `amount`. |
| `payments` | "A paid B" settle-ups. |
| `recurring` | Bills that repeat weekly/monthly; a job every 10 minutes turns due ones into expenses. |

Only members of a group can read or write anything in it (enforced by
collection rules, see the migration).

## Custom API

| Method & path | Auth | Purpose |
|---|---|---|
| `POST /api/hisab/groups` | user | Create group + your member + friends by name. Body `{name, currency, emoji?, myName, others?}` |
| `GET /api/hisab/invite/{code}` | public | Preview an invite: group name and members (claimed or not) |
| `POST /api/hisab/join` | user | Join as an existing member `{code, memberId}` or new `{code, name}` |
| `GET /api/hisab/groups/{id}/invite` | member | Get the invite code |
| `POST /api/hisab/groups/{id}/reset-invite` | member | New invite code; old links stop working |
| `POST /api/hisab/admin/run-recurring` | superuser | Run due recurring bills now |
