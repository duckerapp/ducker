# Release preparation and channels

This guide describes the release tooling. It does not authorize a publication. APKs
are built and signed locally; CI packages APKs for validation without the production key.
Before publishing, review the outgoing source/history and the actual artifacts for secrets.

## Channels and the app's beta switch

There is one app, one signing key and one increasing `versionCode`. Version names have
no `-beta` suffix. `update.json` has independent `stable` and `beta` entries:

| App setting | Offered release |
|---|---|
| Beta versions off (default) | A compatible stable release newer than installed |
| Beta versions on | The newest compatible stable or beta newer than installed |
| Beta off after installing a beta | Keep installed version until a newer stable arrives |
| Same beta promoted to stable | Same bytes and code; no reinstall for beta users |
| Beta withdrawn | Stop offering it; already installed copies need a later fixed version |

Automatic installation is a separate opt-in and follows the selected channels. Android
may defer checks or ask for confirmation. Do not uninstall to switch channels: local
subscriptions are removed. Fix a faulty beta using a higher version/code; never replace
an APK under an existing version. Promotion has no fixed waiting period: use the actual
test results and feedback from the exact candidate.

## Prepare the exact candidate

1. Finish app changes, wallets and public documentation first. Keep both changelogs in
   sync and date the version entry; `draft`/`черновик` entries cannot be released.
2. Run JVM, lint, native/Go checks appropriate to the changes, and both strict site builds.
   Consider regenerating the baseline profile after startup/Compose/navigation changes.
3. Review and commit the source using the project identity and signing configuration.
   Check submodules as well as the root. Keep signing secrets outside Git.
4. Arrange a physical-device test window. Select that one device using `ANDROID_SERIAL`;
   preserve its subscriptions, settings and original VPN state. No generic connected tests.
5. Run `ANDROID_SERIAL=<agreed-device> ./scripts/release.sh 1.0.0` (replace version as needed).
   The script increments the code once, refreshes geo assets, packages both ARM APKs,
   checks package/version/code/minSdk/ABI, bundled licences and the pinned signing
   certificate, renders the release notes, and smokes the exact ARM64 output.

An existing output directory or tag is never overwritten. A failed build restores only
the version file it backed up. A later failure keeps the version/artifacts for diagnosis;
it does not mean the candidate passed. A successful smoke seals `release-manifest.json`:
file hashes/sizes, version/code/minSdk, certificate SHA-256 and source content identity.
The feed is excluded from source identity because publication changes it itself.

For a failed smoke, diagnose it, repeat the smoke on the exact output APK in the agreed
window, and only after it passes run:

```sh
python3 scripts/release_artifacts.py seal 1.0.0 --out <release-folder> --smoke-passed
```

This flag is an explicit attestation of that check; it does not execute a device test.
Without it live publication is refused. Do not use it to bypass a failed/unrun smoke.
If source or APK bytes change, build a new candidate instead of resealing old artifacts.
The validator binds source content, not an independently reproducible-build proof.

Inspect the notes before committing the version. The layout retains two illustrated
download buttons, plain download links, English first, Russian under “По-русски”,
install/TV/docs/community links, checksums, per-APK VirusTotal links and the exact geo
database hashes read from the APK. Image URLs use the release tag, so a later main-branch
design change does not alter old release pages. To preview without building or publishing:

```sh
./scripts/release-notes.sh 1.0.0 <folder-with-both-release-APKs>
```

Rendering writes `release-notes.md`; do it before sealing. Changing sealed notes is
rejected. Upload both exact files to VirusTotal and inspect their reports; a report link
alone does not establish a completed scan or guarantee safety. Check Play Protect with
the exact candidate, a fresh install and update from the prior tester version.
Record which scenarios passed on emulators and physical devices separately.

Commit the version file explicitly, inspect the staged diff, and leave tag creation to
the publisher. Retain the sealed output directory and signing-key backups.

## Publish and promote

Publication needs explicit authorization. Verify the project GitHub account, `origin`
and `gitlab` destinations, project Git identity and commit signing. GitLab uses a project
deploy token with package registry read/write scope; it does not need a personal API token.
Never print it, put it in tracked files or pass it in a command pasted into an issue.
The scripts use the project-specific GitHub CLI profile, not the machine's personal default.

```sh
./scripts/publish.sh 1.0.0             # beta; requires GITLAB_TOKEN in the environment
./scripts/publish.sh 1.0.0 --stable    # later, promote that exact beta
```

The initial publication validates the sealed build, requires a clean `main`, verifies or
creates the tag, uploads missing immutable files to GitHub/GitLab, makes the GitHub beta
visible, then commits/pushes the feed. Existing assets are downloaded and compared; they
are never clobbered. The metadata manifest is available alongside the APKs and SHA256SUMS.
GitHub also gets unversioned copies `ducker-arm64-v8a.apk` and `ducker-armeabi-v7a.apk`, so
`releases/latest/download/ducker-<abi>.apk` always serves the current stable APK for provider pages.
Promotion checks both hosts and the unversioned copies against those same files, removes GitHub's pre-release marker,
marks it latest and copies beta to stable without changing the version or APKs. Promotion
can use the sealed release tag even when main has later app changes.

If interrupted, preserve the output folder and local feed commit and rerun the same
command. Both pushes are retried even if the local feed is already correct. If validation
finds a different artifact/tag, stop and investigate; do not force-push, replace files,
delete the manifest or bypass the pre-push guard. Changes in a multi-host operation are
not atomic: until recovery, one host may still have the prior feed. Complete recovery
before starting another release.

To withdraw an offered release, review/revert the feed change and synchronize both hosts.
Do not lower version codes or replace signed files. Already installed copies stay until
a higher-code fixed release arrives; announce the remedy in the next release notes.

## First public release

- Review outgoing branches, tags, submodules, commit identity, screenshots, APK certificate
  metadata and artifacts for confidential material. Open the three source forks as well.
- Enable the agreed GitHub security settings/private vulnerability reporting and Pages;
  open the GitLab mirror and confirm anonymous package downloads. GitLab is a mirror;
  reports and contributions go to GitHub. Enable automatic Pages deployment only then.
- Check release rendering in a desktop browser and the GitHub mobile app. Plain download
  links remain available if a client does not display the illustrated buttons/details.
- Choose beta-only launch or immediate stable promotion after testing. A beta-only first
  release is invisible to the in-app updater with beta off; manual Releases downloads
  remain possible. For the normal stable launch, publish beta then promote that same file.
- Without authentication, download and hash both APKs from both hosts and read both feeds.
  Check the jsDelivr copy too; CDN caching can delay it. Test OTA from the prior version
  with/without beta and with/without VPN in the agreed device window.
- Recheck these steps after any further app changes. Wallet links finish the content;
  final exact-artifact/device/hosting checks still belong to release day.

## Local update fixture

`APK_BASES=http://127.0.0.1:8765/ ./scripts/publish.sh <version> --local` writes a feed
beside validated artifacts without tags, commits, hosting calls or installation. It
allows an un-smoked local fixture; this exception does not apply to live publication.
It still requires a matching sealed source/artifact set. For an x86/x86_64 emulator,
copy its APK into a separate output directory under the same filename convention,
generate its SHA256SUMS and preview notes, then seal with `--fixture`. This permits
emulator ABIs but makes the manifest ineligible for live publication, even with a
smoke attestation. Package identity and the pinned signing certificate are still checked.
Use a named disposable emulator with isolated app data. Other test-package/signing
fixtures can use the private OTA fixture tools instead of this production validator.
Never distribute a build with local update URLs or treat fixtures as live VPN proof.
