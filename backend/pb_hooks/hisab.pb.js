/// <reference path="../pb_data/types.d.ts" />

// ---------------------------------------------------------------------------
// Custom routes
// ---------------------------------------------------------------------------

// Create a group together with the creator's member and any friends by name.
// Body: {name, currency, emoji?, myName, others?: string[]}
routerAdd("POST", "/api/hisab/groups", (e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  const body = e.requestInfo().body || {}
  const name = lib.cleanName(body.name, 80)
  const currency = String(body.currency || "").toUpperCase()
  const myName = lib.cleanName(body.myName)
  if (!name) throw new BadRequestError("Group name is required")
  if (!/^[A-Z]{3}$/.test(currency)) throw new BadRequestError("Currency must be a 3-letter code")
  if (!myName) throw new BadRequestError("Your name is required")

  const others = (Array.isArray(body.others) ? body.others : [])
    .map((n) => lib.cleanName(n))
    .filter((n) => n && n.toLowerCase() !== myName.toLowerCase())
    .slice(0, 50)

  let result = {}
  $app.runInTransaction((txApp) => {
    const group = new Record(txApp.findCollectionByNameOrId("groups"))
    group.set("name", name)
    group.set("currency", currency)
    group.set("emoji", lib.cleanName(body.emoji, 16))
    group.set("invite_code", lib.newInviteCode())
    group.set("created_by", e.auth.id)
    txApp.save(group)

    const membersCol = txApp.findCollectionByNameOrId("members")
    const me = new Record(membersCol)
    me.set("group", group.id)
    me.set("name", myName)
    me.set("user", e.auth.id)
    txApp.save(me)

    const seen = {}
    for (const n of others) {
      if (seen[n.toLowerCase()]) continue
      seen[n.toLowerCase()] = true
      const m = new Record(membersCol)
      m.set("group", group.id)
      m.set("name", n)
      txApp.save(m)
    }

    if (!e.auth.getString("name")) {
      const user = txApp.findRecordById("users", e.auth.id)
      user.set("name", myName)
      txApp.save(user)
    }
    result = { groupId: group.id, memberId: me.id, inviteCode: group.getString("invite_code") }
  })
  return e.json(200, result)
}, $apis.requireAuth("users"))

// Public preview of an invite link: group name and who is in it, so the
// person joining can say which member they are.
routerAdd("GET", "/api/hisab/invite/{code}", (e) => {
  const code = e.request.pathValue("code")
  let group
  try {
    group = $app.findFirstRecordByData("groups", "invite_code", code)
  } catch (_) {
    throw new NotFoundError("This invite link is not valid anymore")
  }
  const members = $app.findRecordsByFilter("members", "group = {:g}", "created", 200, 0, { g: group.id })
  return e.json(200, {
    group: { id: group.id, name: group.getString("name"), currency: group.getString("currency"), emoji: group.getString("emoji") },
    members: members.map((m) => ({ id: m.id, name: m.getString("name"), claimed: m.getString("user") !== "" })),
  })
})

// Join a group from an invite code, either as an existing (unclaimed) member
// or as a new member. Body: {code, memberId?} or {code, name}
routerAdd("POST", "/api/hisab/join", (e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  const body = e.requestInfo().body || {}
  let group
  try {
    group = $app.findFirstRecordByData("groups", "invite_code", String(body.code || ""))
  } catch (_) {
    throw new NotFoundError("This invite link is not valid anymore")
  }

  const existing = $app.findRecordsByFilter("members", "group = {:g} && user = {:u}", "", 1, 0, { g: group.id, u: e.auth.id })
  if (existing.length > 0) {
    return e.json(200, { groupId: group.id, memberId: existing[0].id })
  }

  let member
  if (body.memberId) {
    try {
      member = $app.findRecordById("members", String(body.memberId))
    } catch (_) {
      throw new NotFoundError("That person is not in this group")
    }
    if (member.getString("group") !== group.id) throw new NotFoundError("That person is not in this group")
    if (member.getString("user")) throw new BadRequestError("Someone already joined as this person")
  } else {
    const name = lib.cleanName(body.name)
    if (!name) throw new BadRequestError("Your name is required")
    member = new Record($app.findCollectionByNameOrId("members"))
    member.set("group", group.id)
    member.set("name", name)
  }
  member.set("user", e.auth.id)
  $app.save(member)

  if (!e.auth.getString("name")) {
    const user = $app.findRecordById("users", e.auth.id)
    user.set("name", member.getString("name"))
    $app.save(user)
  }
  return e.json(200, { groupId: group.id, memberId: member.id })
}, $apis.requireAuth("users"))

// Make a new invite code (old links stop working).
routerAdd("POST", "/api/hisab/groups/{id}/reset-invite", (e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  const id = e.request.pathValue("id")
  const mine = $app.findRecordsByFilter("members", "group = {:g} && user = {:u}", "", 1, 0, { g: id, u: e.auth.id })
  if (mine.length === 0) throw new ForbiddenError("You are not in this group")
  const group = $app.findRecordById("groups", id)
  group.set("invite_code", lib.newInviteCode())
  $app.save(group)
  return e.json(200, { inviteCode: group.getString("invite_code") })
}, $apis.requireAuth("users"))

// Members of a group may read its invite code (it's hidden on the record).
routerAdd("GET", "/api/hisab/groups/{id}/invite", (e) => {
  const id = e.request.pathValue("id")
  const mine = $app.findRecordsByFilter("members", "group = {:g} && user = {:u}", "", 1, 0, { g: id, u: e.auth.id })
  if (mine.length === 0) throw new ForbiddenError("You are not in this group")
  return e.json(200, { inviteCode: $app.findRecordById("groups", id).getString("invite_code") })
}, $apis.requireAuth("users"))

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

onRecordCreateRequest((e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  lib.validateSplits(e.app, e.record)
  if (e.auth) e.record.set("created_by", e.auth.id)
  e.next()
}, "expenses")

onRecordUpdateRequest((e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  lib.validateSplits(e.app, e.record)
  e.next()
}, "expenses")

onRecordCreateRequest((e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  lib.validateSplits(e.app, e.record)
  const body = e.requestInfo().body || {}
  if (body.active === undefined) e.record.set("active", true)
  e.record.set("anchor_day", lib.parseDate(e.record.getString("next_date")).getUTCDate())
  e.next()
}, "recurring")

onRecordUpdateRequest((e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  lib.validateSplits(e.app, e.record)
  const body = e.requestInfo().body || {}
  if (body.next_date !== undefined) {
    e.record.set("anchor_day", lib.parseDate(e.record.getString("next_date")).getUTCDate())
  }
  e.next()
}, "recurring")

onRecordCreateRequest((e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  lib.validatePayment(e.app, e.record)
  if (e.auth) e.record.set("created_by", e.auth.id)
  e.next()
}, "payments")

onRecordUpdateRequest((e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  lib.validatePayment(e.app, e.record)
  e.next()
}, "payments")

// A member who already has expenses or payments can't be removed (it would
// silently change everyone's balances). Members are only deletable by superusers
// anyway; this guards the admin UI too.
onRecordDelete((e) => {
  const used = e.app.findRecordsByFilter(
    "expenses", "paid_by = {:m} || splits ~ {:m}", "", 1, 0, { m: e.record.id },
  ).length + e.app.findRecordsByFilter(
    "payments", "from = {:m} || to = {:m}", "", 1, 0, { m: e.record.id },
  ).length
  if (used > 0) throw new BadRequestError("This person has expenses or payments and can't be removed")
  e.next()
}, "members")

// ---------------------------------------------------------------------------
// Recurring bills
// ---------------------------------------------------------------------------

cronAdd("hisabRecurring", "*/10 * * * *", () => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  const n = lib.runRecurring($app)
  if (n > 0) $app.logger().info("Created recurring expenses", "count", n)
})

// Superuser-only: run recurring bills now (useful after downtime and in tests).
routerAdd("POST", "/api/hisab/admin/run-recurring", (e) => {
  const lib = require(`${__hooks}/hisab_lib.js`)
  return e.json(200, { created: lib.runRecurring($app) })
}, $apis.requireSuperuserAuth())

// Behind a reverse proxy (deploy/Caddyfile), every request comes from
// 127.0.0.1, so the rate limiter would treat all users as one. When
// HISAB_TRUSTED_PROXY_HEADER is set (deploy/hisab.service sets X-Real-IP),
// trust that header for the client IP. Leave it unset when PocketBase is
// reachable directly, or clients could fake their IP.
onBootstrap((e) => {
  e.next()
  const header = $os.getenv("HISAB_TRUSTED_PROXY_HEADER")
  if (!header) return
  const settings = e.app.settings()
  if (settings.trustedProxy.headers.length === 1 && settings.trustedProxy.headers[0] === header) return
  settings.trustedProxy.headers = [header]
  e.app.save(settings)
})
