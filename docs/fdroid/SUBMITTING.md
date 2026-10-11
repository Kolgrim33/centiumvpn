# Submitting Centium to F-Droid

The repo is already set up for F-Droid:
- **License:** GPL-3.0.
- **Build variant:** an `fdroid` flavor with the self-updater removed.
- **Store listing:** in `fastlane/`.
- **Build recipe:** `org.centium.vpn.yml`, checked with `fdroid lint` and the F-Droid source scanner (0 problems).

What's left needs your own GitLab account.

## 1. Create a GitLab account
Sign up at https://gitlab.com. Your GitHub login works.

## 2. Fork fdroiddata
Open https://gitlab.com/fdroid/fdroiddata and click **Fork**. Keep the default settings.

## 3. Add the recipe
In your fork:
1. Open the `metadata/` folder.
2. Choose **+ → New file**.
3. Name it `org.centium.vpn.yml`.
4. Paste in the contents of `docs/fdroid/org.centium.vpn.yml` from this repo.
5. Commit it to a new branch named `org.centium.vpn`.

## 4. Open the merge request
GitLab will offer **Create merge request**. Use these details:
- **Target:** `fdroid/fdroiddata`, branch `master`.
- **Title:** `New app: Centium VPN`.
- **Template:** pick the "App inclusion" template and tick its checklist. Everything on it is already true:
  - the app is FOSS (GPL-3.0);
  - it has no proprietary dependencies;
  - the self-updater is removed in the F-Droid build;
  - the description and screenshots live in the repo (fastlane).
- **Extra note:** mention that the app uses tor-android from Maven Central, as Orbot-style apps do.

## 5. Wait for the pipeline and review
F-Droid's CI builds the app from source. Reviewers may ask questions in the merge request, so watch your GitLab notifications. Review usually takes from a few days to a few weeks.

After it's merged, Centium appears in the F-Droid app within a day or two. New versions are picked up automatically whenever you push a `vX.Y.Z` tag. F-Droid builds can take a few days to follow a GitHub release.

## Notes
- **Different keys:** F-Droid signs Centium with its own key, so someone with the GitHub version must uninstall it before installing from F-Droid (and the other way round).
- **Possible request:** reviewers may ask to build Tor from source instead of using the tor-android library from Maven Central. If they do, that's a follow-up change to the app.
