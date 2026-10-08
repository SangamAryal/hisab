#!/usr/bin/env bash
# Uploads the debug APK to a Google Drive folder, replacing the copy uploaded
# last time so the folder always holds just the latest build (same file, same
# link). Needs GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET and GDRIVE_REFRESH_TOKEN;
# without them it skips. Setup: docs/APK-TO-DRIVE.md.
set -euo pipefail

apk="$1"
folder="${GDRIVE_FOLDER_ID:?}"
name="${GDRIVE_FILE_NAME:-hisab.apk}"

if [ -z "${GDRIVE_CLIENT_ID:-}" ] || [ -z "${GDRIVE_CLIENT_SECRET:-}" ] || [ -z "${GDRIVE_REFRESH_TOKEN:-}" ]; then
  echo "::notice::Google Drive secrets not set, skipping the APK upload (see docs/APK-TO-DRIVE.md)."
  exit 0
fi

token=$(curl -fsS https://oauth2.googleapis.com/token \
  -d client_id="$GDRIVE_CLIENT_ID" \
  -d client_secret="$GDRIVE_CLIENT_SECRET" \
  -d refresh_token="$GDRIVE_REFRESH_TOKEN" \
  -d grant_type=refresh_token | jq -r .access_token)

api=https://www.googleapis.com
desc="Hisab debug build from commit ${GITHUB_SHA:-unknown}"

# The drive.file scope only sees files this upload created, so this finds our
# previous APK and never touches anything else in the folder.
existing=$(curl -fsS -G "$api/drive/v3/files" -H "Authorization: Bearer $token" \
  --data-urlencode "q='$folder' in parents and name='$name' and trashed=false" \
  --data-urlencode "fields=files(id)" | jq -r '.files[0].id // empty')

if [ -n "$existing" ]; then
  curl -fsS -X PATCH "$api/upload/drive/v3/files/$existing?uploadType=multipart&fields=id,webViewLink" \
    -H "Authorization: Bearer $token" \
    -F "metadata={\"description\":\"$desc\"};type=application/json;charset=UTF-8" \
    -F "file=@$apk;type=application/vnd.android.package-archive" | jq -r '"Replaced " + .webViewLink'
else
  curl -fsS -X POST "$api/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink" \
    -H "Authorization: Bearer $token" \
    -F "metadata={\"name\":\"$name\",\"parents\":[\"$folder\"],\"description\":\"$desc\"};type=application/json;charset=UTF-8" \
    -F "file=@$apk;type=application/vnd.android.package-archive" | jq -r '"Uploaded " + .webViewLink'
fi
