# Secure production signing — NEXVARY Floating Note

Development APKs are signed with temporary Android debug certificates. They
are useful for testing, but are not suitable for consistent in-place upgrades.

The manual GitHub Actions workflow "Signed Release APK" builds a properly signed
APK using a persistent keystore under your control.

## Setup using the GitHub web interface

1. Create a secure Android keystore outside the repository, preferably through
   Android Studio: Build > Generate Signed Bundle / APK > Create new.
2. Keep the .jks file, alias, passwords and an offline backup protected.
   Never commit them, even as base64.
3. On GitHub open this repository's Settings > Secrets and variables > Actions.
4. Add these four repository secrets:
   - ANDROID_KEYSTORE_BASE64 : base64-encoded binary contents of the .jks file.
   - ANDROID_KEYSTORE_PASSWORD : its store password.
   - ANDROID_KEY_ALIAS : the signing key alias.
   - ANDROID_KEY_PASSWORD : the signing key password.
5. After review, ensure the workflow file exists on the repository's DEFAULT
   branch (GitHub requires this for manual workflow_dispatch discovery). Then open
   Actions > Signed Release APK > Run workflow and select the reviewed branch.
6. Download the signed-release artifact only if the job succeeds and apksigner
   verifies its signature.

The ephemeral keystore copy is removed during job cleanup.

## Updating an installed app

Do not change signing keys between published versions. Android in-place updates
require a matching package name and signing identity, and a higher versionCode.
A production-signed APK generally cannot update a Debug-signed installation.
First export an encrypted note backup, then install the production package and restore.

## Required user action

Signing credentials have not been configured by ChatGPT, so this is only a
prepared release pipeline. It is NOT proof that a production-signed or Play
Store-approved APK has been produced.
