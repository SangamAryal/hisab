/// <reference path="../pb_data/types.d.ts" />

// App name, and turn on PocketBase's built-in per-IP rate limiter (protects the
// public invite endpoint and guest sign-ups from abuse). The limits are a bit
// looser than PocketBase's defaults because many mobile users share one IP
// behind carrier NAT.
migrate((app) => {
  const settings = app.settings()
  settings.meta.appName = "Hisab"
  settings.rateLimits.enabled = true
  settings.rateLimits.rules = [
    { label: "*:auth", maxRequests: 10, duration: 3 },
    { label: "*:create", maxRequests: 40, duration: 5 },
    { label: "/api/hisab/", maxRequests: 60, duration: 10 },
    { label: "/api/batch", maxRequests: 3, duration: 1 },
    { label: "/api/", maxRequests: 300, duration: 10 },
  ]
  app.save(settings)
})
