# Latest Android APK in Google Drive

Every time `main` changes, CI builds the Android debug APK and uploads it to
[this Drive folder](https://drive.google.com/drive/folders/1HKxoO0B9-U86aX7_eOcLBE8Ajsygkb94)
as `hisab.apk`. Each new build replaces the old file, so the folder always has
just the latest one and its link never changes. Pull requests don't upload.

Until the three secrets below exist, CI skips the upload and stays green.
Everything here is free.

## One-time setup (about 10 minutes)

Use the Google account that owns the Drive folder.

**1. Turn on the Drive API**

1. Open <https://console.cloud.google.com/apis/library/drive.googleapis.com>
   and pick your project at the top (the one with the Hisab server is fine).
2. Click **Enable**.

**2. Describe the app to Google (consent screen)**

1. Open <https://console.cloud.google.com/auth/overview> and click **Get started**.
2. App name: `Hisab CI`. Support email: yours. Audience: **External**.
   Contact email: yours. Accept and **Create**.
3. Go to **Audience** in the left menu and click **Publish app**, then **Confirm**.
   (If you skip this, Google cancels the login after 7 days and uploads stop.)

**3. Create the login keys**

1. Go to **Clients** in the left menu, then **Create client**.
2. Application type: **Web application**. Name: `Hisab CI`.
3. Under **Authorized redirect URIs** click **Add URI** and paste
   `https://developers.google.com/oauthplayground`
4. Click **Create**. Copy the **Client ID** and **Client secret** somewhere for a minute.

**4. Get the refresh token**

1. Open <https://developers.google.com/oauthplayground>.
2. Click the gear icon (top right), tick **Use your own OAuth credentials**,
   and paste the Client ID and Client secret.
3. In the box on the left that says "Input your own scopes", paste
   `https://www.googleapis.com/auth/drive.file` and click **Authorize APIs**.
4. Sign in. Google warns "Google hasn't verified this app": click
   **Advanced**, then **Go to Hisab CI (unsafe)** (it's your own app), then **Continue**.
5. Click **Exchange authorization code for tokens**. Copy the **Refresh token**.

The `drive.file` permission only lets CI see and change files it uploaded
itself, never the rest of your Drive.

**5. Give the keys to GitHub**

Open <https://github.com/SangamAryal/hisab/settings/secrets/actions> and click
**New repository secret** three times:

| Name | Value |
| --- | --- |
| `GDRIVE_CLIENT_ID` | the Client ID |
| `GDRIVE_CLIENT_SECRET` | the Client secret |
| `GDRIVE_REFRESH_TOKEN` | the Refresh token |

That's it. To try it now, open the
[CI workflow](https://github.com/SangamAryal/hisab/actions/workflows/ci.yml),
click **Run workflow** on `main`, and the APK appears in the folder when the
Android job finishes.

## Notes

- To use a different folder, add a repository *variable* (not secret) named
  `GDRIVE_FOLDER_ID` with the folder's id (the part after `/folders/` in its link).
- If you upload or rename `hisab.apk` by hand, CI can't see that copy and will
  upload a fresh one next to it. Delete the extra one and it sorts itself out.
