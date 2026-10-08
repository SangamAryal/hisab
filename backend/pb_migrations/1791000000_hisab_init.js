/// <reference path="../pb_data/types.d.ts" />

// Hisab schema: groups, members, expenses, payments, recurring bills.
// Money is always stored as integers in the currency's minor unit
// (paisa, cents...). A "member" is a person inside one group; it may or
// may not be linked to an app user (friends can be added by name first).
migrate((app) => {
  const users = app.findCollectionByNameOrId("users")

  // Anyone who is a member of the record's group.
  const MEMBER = "group.members_via_group.user ?= @request.auth.id"

  const groups = new Collection({
    type: "base",
    name: "groups",
    fields: [
      { type: "text", name: "name", required: true, max: 80 },
      { type: "text", name: "currency", required: true, min: 3, max: 3, pattern: "^[A-Z]{3}$" },
      { type: "text", name: "emoji", max: 16 },
      { type: "text", name: "invite_code", required: true, min: 8, max: 32, hidden: true },
      { type: "relation", name: "created_by", collectionId: users.id, maxSelect: 1 },
      { type: "autodate", name: "created", onCreate: true },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_groups_invite ON groups (invite_code)"],
  })
  app.save(groups)

  const members = new Collection({
    type: "base",
    name: "members",
    fields: [
      { type: "relation", name: "group", collectionId: groups.id, maxSelect: 1, required: true, cascadeDelete: true },
      { type: "text", name: "name", required: true, max: 60 },
      { type: "relation", name: "user", collectionId: users.id, maxSelect: 1 },
      // e.g. {"esewa":"98XXXXXXXX","khalti":"...","upi":"name@bank","paypal":"handle","venmo":"handle"}
      { type: "json", name: "pay_handles", maxSize: 2000 },
      { type: "autodate", name: "created", onCreate: true },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: [
      "CREATE UNIQUE INDEX idx_members_group_user ON members (`group`, user) WHERE user != ''",
    ],
  })
  app.save(members)

  groups.listRule = "members_via_group.user ?= @request.auth.id"
  groups.viewRule = "members_via_group.user ?= @request.auth.id"
  groups.createRule = null // created through POST /api/hisab/groups
  groups.updateRule = "members_via_group.user ?= @request.auth.id && @request.body.invite_code:isset = false && @request.body.created_by:isset = false"
  groups.deleteRule = "created_by = @request.auth.id"
  app.save(groups)

  members.listRule = MEMBER
  members.viewRule = MEMBER
  // Members can add friends by name; linking a user only happens via /api/hisab/join.
  members.createRule = MEMBER + " && @request.body.user:isset = false"
  members.updateRule = MEMBER + " && @request.body.user:isset = false && @request.body.group:isset = false"
  members.deleteRule = null
  app.save(members)

  const splitFields = [
    { type: "relation", name: "group", collectionId: groups.id, maxSelect: 1, required: true, cascadeDelete: true },
    { type: "text", name: "description", required: true, max: 120 },
    { type: "number", name: "amount", required: true, onlyInt: true, min: 1 },
    { type: "relation", name: "paid_by", collectionId: members.id, maxSelect: 1, required: true },
    // [{"member":"<id>","amount":123}, ...] — must add up to amount.
    { type: "json", name: "splits", required: true, maxSize: 20000 },
    { type: "text", name: "category", max: 30 },
  ]
  const ownRules = {
    listRule: MEMBER,
    viewRule: MEMBER,
    createRule: MEMBER,
    updateRule: MEMBER + " && @request.body.group:isset = false",
    deleteRule: MEMBER,
  }

  const recurring = new Collection({
    type: "base",
    name: "recurring",
    ...ownRules,
    fields: [
      ...splitFields,
      { type: "select", name: "interval", required: true, maxSelect: 1, values: ["weekly", "monthly"] },
      { type: "date", name: "next_date", required: true },
      { type: "number", name: "anchor_day", onlyInt: true, min: 0, max: 31 },
      { type: "bool", name: "active" },
      { type: "autodate", name: "created", onCreate: true },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
  })
  app.save(recurring)

  const expenses = new Collection({
    type: "base",
    name: "expenses",
    ...ownRules,
    fields: [
      ...splitFields,
      { type: "date", name: "date", required: true },
      { type: "text", name: "note", max: 500 },
      { type: "file", name: "receipt", maxSelect: 1, maxSize: 5242880, mimeTypes: ["image/jpeg", "image/png", "image/webp", "image/heic"] },
      { type: "relation", name: "recurring", collectionId: recurring.id, maxSelect: 1 },
      { type: "relation", name: "created_by", collectionId: users.id, maxSelect: 1 },
      { type: "autodate", name: "created", onCreate: true },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: ["CREATE INDEX idx_expenses_group_date ON expenses (`group`, date)"],
  })
  app.save(expenses)

  const payments = new Collection({
    type: "base",
    name: "payments",
    ...ownRules,
    fields: [
      { type: "relation", name: "group", collectionId: groups.id, maxSelect: 1, required: true, cascadeDelete: true },
      { type: "relation", name: "from", collectionId: members.id, maxSelect: 1, required: true },
      { type: "relation", name: "to", collectionId: members.id, maxSelect: 1, required: true },
      { type: "number", name: "amount", required: true, onlyInt: true, min: 1 },
      { type: "date", name: "date", required: true },
      { type: "text", name: "method", max: 30 },
      { type: "relation", name: "created_by", collectionId: users.id, maxSelect: 1 },
      { type: "autodate", name: "created", onCreate: true },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: ["CREATE INDEX idx_payments_group_date ON payments (`group`, date)"],
  })
  app.save(payments)
}, (app) => {
  for (const name of ["payments", "expenses", "recurring", "members", "groups"]) {
    app.delete(app.findCollectionByNameOrId(name))
  }
})
