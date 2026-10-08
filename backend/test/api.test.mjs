// End-to-end API test. Starts a fresh PocketBase, then exercises every rule.
// Run: node --test test/   (from backend/)
import { test, before, after } from "node:test"
import assert from "node:assert/strict"
import { spawn, execFileSync } from "node:child_process"
import { mkdtempSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"

const PORT = 18090 + Math.floor(Math.random() * 1000)
const BASE = `http://127.0.0.1:${PORT}`
const BIN = process.env.PB_BIN || "./pocketbase"
let dir, proc

async function api(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "content-type": "application/json", ...(token ? { authorization: token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  return { status: res.status, data: text ? JSON.parse(text) : null }
}

async function guest() {
  const email = `g_${Math.random().toString(36).slice(2)}@guest.hisab.app`
  const password = Math.random().toString(36).slice(2) + "Aa1!xyz"
  let r = await api("POST", "/api/collections/users/records", { body: { email, password, passwordConfirm: password } })
  assert.equal(r.status, 200, JSON.stringify(r.data))
  r = await api("POST", "/api/collections/users/auth-with-password", { body: { identity: email, password } })
  assert.equal(r.status, 200)
  return { token: r.data.token, id: r.data.record.id }
}

before(async () => {
  dir = mkdtempSync(join(tmpdir(), "hisab-test-"))
  const args = [`--dir=${dir}`, "--hooksDir=pb_hooks", "--migrationsDir=pb_migrations"]
  execFileSync(BIN, ["superuser", "upsert", "admin@test.local", "testpassword123", ...args])
  proc = spawn(BIN, ["serve", `--http=127.0.0.1:${PORT}`, ...args], { stdio: "ignore" })
  for (let i = 0; i < 50; i++) {
    try { if ((await fetch(BASE + "/api/health")).ok) break } catch {}
    await new Promise((r) => setTimeout(r, 200))
  }
  // The tests create many users from one IP; switch the limiter off for them
  // (after checking the migration turned it on).
  const su = await api("POST", "/api/collections/_superusers/auth-with-password", { body: { identity: "admin@test.local", password: "testpassword123" } })
  assert.equal(su.status, 200, "PocketBase did not start")
  const settings = await api("GET", "/api/settings", { token: su.data.token })
  assert.equal(settings.data.rateLimits.enabled, true)
  assert.equal(settings.data.meta.appName, "Hisab")
  await api("PATCH", "/api/settings", { token: su.data.token, body: { rateLimits: { enabled: false } } })
})

after(() => { proc?.kill(); rmSync(dir, { recursive: true, force: true }) })

test("full group flow and access rules", async () => {
  const a = await guest(), b = await guest(), outsider = await guest()

  // Create group with friends by name.
  let r = await api("POST", "/api/hisab/groups", { token: a.token, body: { name: "Flat 4B", currency: "npr", myName: "Asha", others: ["Bina", "Chandra", "bina", " "] } })
  assert.equal(r.status, 200, JSON.stringify(r.data))
  const { groupId, inviteCode } = r.data
  const aMember = r.data.memberId

  // Invite preview is public and lists who is in the group.
  r = await api("GET", `/api/hisab/invite/${inviteCode}`)
  assert.equal(r.status, 200)
  assert.equal(r.data.group.currency, "NPR")
  assert.deepEqual(r.data.members.map((m) => m.name), ["Asha", "Bina", "Chandra"])
  const bina = r.data.members.find((m) => m.name === "Bina")
  const chandra = r.data.members.find((m) => m.name === "Chandra")
  assert.equal(r.data.members.find((m) => m.name === "Asha").claimed, true)

  // Bad code / claiming someone already claimed.
  assert.equal((await api("GET", "/api/hisab/invite/nope12345")).status, 404)
  assert.equal((await api("POST", "/api/hisab/join", { token: b.token, body: { code: inviteCode, memberId: aMember } })).status, 400)

  // B joins as Bina; joining again is idempotent.
  r = await api("POST", "/api/hisab/join", { token: b.token, body: { code: inviteCode, memberId: bina.id } })
  assert.equal(r.status, 200)
  assert.equal(r.data.memberId, bina.id)
  r = await api("POST", "/api/hisab/join", { token: b.token, body: { code: inviteCode, name: "Someone else" } })
  assert.equal(r.data.memberId, bina.id)

  // Invite code is hidden on the record but members can fetch it.
  r = await api("GET", `/api/collections/groups/records/${groupId}`, { token: b.token })
  assert.equal(r.status, 200)
  assert.equal(r.data.invite_code, undefined)
  assert.equal((await api("GET", `/api/hisab/groups/${groupId}/invite`, { token: b.token })).data.inviteCode, inviteCode)
  assert.equal((await api("GET", `/api/hisab/groups/${groupId}/invite`, { token: outsider.token })).status, 403)

  // Outsider sees nothing.
  r = await api("GET", `/api/collections/groups/records/${groupId}`, { token: outsider.token })
  assert.equal(r.status, 404)
  r = await api("GET", "/api/collections/members/records", { token: outsider.token })
  assert.equal(r.data.totalItems, 0)

  // Expense: 1000 split 334/333/333.
  const exp = { group: groupId, description: "Groceries", amount: 1000, paid_by: aMember, date: "2026-10-01 10:00:00.000Z",
    splits: [{ member: aMember, amount: 334 }, { member: bina.id, amount: 333 }, { member: chandra.id, amount: 333 }] }
  r = await api("POST", "/api/collections/expenses/records", { token: b.token, body: exp })
  assert.equal(r.status, 200, JSON.stringify(r.data))
  assert.equal(r.data.created_by, b.id)
  const expenseId = r.data.id

  // Splits that don't add up, duplicates, or foreign members are rejected.
  for (const splits of [
    [{ member: aMember, amount: 999 }],
    [{ member: aMember, amount: 500 }, { member: aMember, amount: 500 }],
    [{ member: aMember, amount: 1000.5 }],
    [],
  ]) {
    r = await api("POST", "/api/collections/expenses/records", { token: a.token, body: { ...exp, splits } })
    assert.equal(r.status, 400, JSON.stringify(splits))
  }
  // Outsider can't add to the group.
  r = await api("POST", "/api/collections/expenses/records", { token: outsider.token, body: exp })
  assert.equal(r.status, 400)

  // Another group's member can't be used here.
  r = await api("POST", "/api/hisab/groups", { token: outsider.token, body: { name: "Trip", currency: "USD", myName: "Olu" } })
  const otherMember = r.data.memberId
  r = await api("POST", "/api/collections/expenses/records", { token: a.token, body: { ...exp, paid_by: otherMember } })
  assert.equal(r.status, 400)
  r = await api("POST", "/api/collections/expenses/records", { token: a.token, body: { ...exp, amount: 1, splits: [{ member: otherMember, amount: 1 }] } })
  assert.equal(r.status, 400)

  // Can't move an expense to another group, or hijack a member's user link.
  r = await api("PATCH", `/api/collections/expenses/records/${expenseId}`, { token: a.token, body: { group: r.data?.id || "x" } })
  assert.notEqual(r.status, 200)
  r = await api("PATCH", `/api/collections/members/records/${chandra.id}`, { token: a.token, body: { user: a.id } })
  assert.notEqual(r.status, 200)
  r = await api("PATCH", `/api/collections/members/records/${bina.id}`, { token: b.token, body: { pay_handles: { esewa: "9800000000" } } })
  assert.equal(r.status, 200)
  // invite_code is a hidden field: PocketBase ignores it in normal writes.
  await api("PATCH", `/api/collections/groups/records/${groupId}`, { token: b.token, body: { invite_code: "hijacked123" } })
  assert.equal((await api("GET", `/api/hisab/groups/${groupId}/invite`, { token: b.token })).data.inviteCode, inviteCode)
  r = await api("PATCH", `/api/collections/groups/records/${groupId}`, { token: b.token, body: { created_by: b.id } })
  assert.notEqual(r.status, 200)

  // Payments.
  r = await api("POST", "/api/collections/payments/records", { token: b.token, body: { group: groupId, from: bina.id, to: aMember, amount: 333, date: "2026-10-02 00:00:00.000Z" } })
  assert.equal(r.status, 200, JSON.stringify(r.data))
  r = await api("POST", "/api/collections/payments/records", { token: b.token, body: { group: groupId, from: bina.id, to: bina.id, amount: 1, date: "2026-10-02 00:00:00.000Z" } })
  assert.equal(r.status, 400)

  // Reset invite: old code dies.
  r = await api("POST", `/api/hisab/groups/${groupId}/reset-invite`, { token: a.token })
  assert.equal(r.status, 200)
  assert.equal((await api("GET", `/api/hisab/invite/${inviteCode}`)).status, 404)

  // Only the creator can delete the group.
  assert.notEqual((await api("DELETE", `/api/collections/groups/records/${groupId}`, { token: b.token })).status, 204)
})

test("recurring bills create expenses and keep their day of month", async () => {
  const a = await guest()
  let r = await api("POST", "/api/hisab/groups", { token: a.token, body: { name: "Home", currency: "USD", myName: "A", others: ["B"] } })
  const { groupId, memberId } = r.data
  const members = (await api("GET", `/api/collections/members/records?filter=${encodeURIComponent(`group="${groupId}"`)}`, { token: a.token })).data.items
  const other = members.find((m) => m.id !== memberId).id

  r = await api("POST", "/api/collections/recurring/records", { token: a.token, body: {
    group: groupId, description: "Rent", amount: 200000, paid_by: memberId, interval: "monthly",
    next_date: "2026-01-31 09:00:00.000Z",
    splits: [{ member: memberId, amount: 100000 }, { member: other, amount: 100000 }] } })
  assert.equal(r.status, 200, JSON.stringify(r.data))
  assert.equal(r.data.active, true)
  assert.equal(r.data.anchor_day, 31)
  const billId = r.data.id

  const su = await api("POST", "/api/collections/_superusers/auth-with-password", { body: { identity: "admin@test.local", password: "testpassword123" } })
  r = await api("POST", "/api/hisab/admin/run-recurring", { token: su.data.token })
  assert.equal(r.status, 200)
  assert.ok(r.data.created >= 9, `created ${r.data.created}`)

  r = await api("GET", `/api/collections/expenses/records?perPage=50&sort=date&filter=${encodeURIComponent(`recurring="${billId}"`)}`, { token: a.token })
  const dates = r.data.items.map((e) => e.date.slice(0, 10))
  assert.deepEqual(dates.slice(0, 4), ["2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"])

  // Running again creates nothing new.
  r = await api("POST", "/api/hisab/admin/run-recurring", { token: su.data.token })
  assert.equal(r.data.created, 0)
})
