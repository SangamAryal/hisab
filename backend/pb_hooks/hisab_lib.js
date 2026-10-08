// Shared helpers for Hisab hooks. Loaded with require() inside each handler,
// because PocketBase runs every JS handler in its own isolated context.

const CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

function newInviteCode() {
  return $security.randomStringWithAlphabet(10, CODE_ALPHABET)
}

function cleanName(value, max) {
  const s = String(value == null ? "" : value).trim().replace(/\s+/g, " ")
  return s.slice(0, max || 60)
}

function readJSON(record, field) {
  const raw = record.get(field)
  if (raw == null) return null
  if (typeof raw === "string") return raw ? JSON.parse(raw) : null
  const s = toString(raw)
  return s ? JSON.parse(s) : null
}

function memberInGroup(app, memberId, groupId) {
  if (!memberId) return false
  try {
    return app.findRecordById("members", memberId).getString("group") === groupId
  } catch (_) {
    return false
  }
}

// Validates an expense or recurring bill: payer and every split member must
// belong to the record's group, and the split amounts must add up exactly.
function validateSplits(app, record) {
  const groupId = record.getString("group")
  if (!memberInGroup(app, record.getString("paid_by"), groupId)) {
    throw new BadRequestError("paid_by must be a member of this group")
  }
  const splits = readJSON(record, "splits")
  if (!Array.isArray(splits) || splits.length === 0) {
    throw new BadRequestError("splits must be a non-empty list")
  }
  const seen = {}
  let total = 0
  for (const s of splits) {
    const amount = s && s.amount
    if (!s || typeof s.member !== "string" || !Number.isInteger(amount) || amount < 0) {
      throw new BadRequestError("each split needs a member id and a whole, non-negative amount")
    }
    if (seen[s.member]) throw new BadRequestError("a member appears twice in splits")
    seen[s.member] = true
    if (!memberInGroup(app, s.member, groupId)) {
      throw new BadRequestError("split member is not in this group")
    }
    total += amount
  }
  if (total !== record.getInt("amount")) {
    throw new BadRequestError("splits must add up to the amount (" + total + " != " + record.getInt("amount") + ")")
  }
}

function validatePayment(app, record) {
  const groupId = record.getString("group")
  const from = record.getString("from")
  const to = record.getString("to")
  if (from === to) throw new BadRequestError("from and to must be different people")
  if (!memberInGroup(app, from, groupId) || !memberInGroup(app, to, groupId)) {
    throw new BadRequestError("from and to must be members of this group")
  }
}

// --- dates -----------------------------------------------------------------

function parseDate(s) {
  return new Date(String(s).replace(" ", "T"))
}

function formatDate(d) {
  return d.toISOString().replace("T", " ")
}

function daysInMonth(year, month) {
  return new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
}

// Next occurrence after `d`. Monthly bills keep their anchor day, clamped to
// the month's length (a bill on the 31st runs on Feb 28/29).
function advance(d, interval, anchorDay) {
  const next = new Date(d.getTime())
  if (interval === "weekly") {
    next.setUTCDate(next.getUTCDate() + 7)
    return next
  }
  const day = anchorDay || d.getUTCDate()
  const y = d.getUTCFullYear()
  const m = d.getUTCMonth() + 1
  next.setUTCDate(1)
  next.setUTCFullYear(y + Math.floor(m / 12), m % 12)
  next.setUTCDate(Math.min(day, daysInMonth(next.getUTCFullYear(), next.getUTCMonth())))
  return next
}

// Creates the expenses that recurring bills owe, up to now. Safe to run often.
function runRecurring(app, now) {
  now = now || new Date()
  const due = app.findRecordsByFilter(
    "recurring",
    "active = true && next_date <= {:now}",
    "next_date",
    500,
    0,
    { now: formatDate(now) },
  )
  let created = 0
  const expenses = app.findCollectionByNameOrId("expenses")
  for (const bill of due) {
    app.runInTransaction((txApp) => {
      let next = parseDate(bill.getString("next_date"))
      // Catch up at most a year of missed weekly runs.
      for (let i = 0; i < 53 && next.getTime() <= now.getTime(); i++) {
        const e = new Record(expenses)
        e.set("group", bill.getString("group"))
        e.set("description", bill.getString("description"))
        e.set("amount", bill.getInt("amount"))
        e.set("paid_by", bill.getString("paid_by"))
        e.set("splits", readJSON(bill, "splits"))
        e.set("category", bill.getString("category"))
        e.set("date", formatDate(next))
        e.set("recurring", bill.id)
        txApp.save(e)
        created++
        next = advance(next, bill.getString("interval"), bill.getInt("anchor_day"))
      }
      bill.set("next_date", formatDate(next))
      txApp.save(bill)
    })
  }
  return created
}

module.exports = {
  newInviteCode,
  cleanName,
  readJSON,
  validateSplits,
  validatePayment,
  parseDate,
  formatDate,
  advance,
  runRecurring,
}
