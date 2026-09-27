#!/usr/bin/env bash
# Unhooked publish-release — extracted from .gitlab-ci.yml so YAML quoting
# (e.g. `#` comment truncation) can never silently rewrite it.
# Runs under `set -euo pipefail`: any gate failure aborts the job.
# Never add `set -x`: GITLAB_API_TOKEN travels in a curl header below.
set -euo pipefail

VERSION=$(grep '^version *=' gradle.properties | cut -d= -f2 | tr -d ' ')
IG_VERSION=$(aapt dump badging unhooked-classic.apk | grep -o "versionName='[^']*'" | cut -d"'" -f2)
test -n "$VERSION" || (echo "no version in gradle.properties" >&2 && exit 1)
NOTES=$(awk -v ver="$VERSION" '$0 == "## " ver {f=1; next} /^## / {f=0} f' CHANGELOG.md)
# Both sides are normalized (colons stripped) since keytool and
# apksigner print the same digest with different separators/casing.
if [ -n "${RELEASE_CERT_SHA256:-}" ]; then
  PIN=$(echo "$RELEASE_CERT_SHA256" | tr -d ':')
  tr -d ':' < certs-classic.txt | grep -qi "$PIN" || (echo "classic APK not signed with the release key" >&2 && exit 1)
  tr -d ':' < certs-clone.txt | grep -qi "$PIN" || (echo "clone APK not signed with the release key" >&2 && exit 1)
else
  echo "WARNING: RELEASE_CERT_SHA256 unset — cert pin skipped"
fi
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$CI_API_V4_URL/projects/$CI_PROJECT_ID/releases/v$VERSION")" = "200" ]; then
  echo "Release v$VERSION already exists — build verified only."
  exit 0
fi
# New release: the CHANGELOG section is the release notes (rendered in the
# update prompt), so a version bump without one fails here, not silently.
test -n "$(echo "$NOTES" | tr -d '[:space:]')" || (echo "No CHANGELOG.md section for ## $VERSION — cut the release notes before merging" >&2 && exit 1)
test -n "${UNHOOKED_KEYSTORE_PASS:-}" || (echo "refusing to publish without the release key" >&2 && exit 1)
test -n "${GITLAB_API_TOKEN:-}" || (echo "refusing to publish without GITLAB_API_TOKEN" >&2 && exit 1)
PKG="$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages/generic/unhooked/v$VERSION"
curl --fail --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file unhooked-classic.apk "$PKG/unhooked-classic.apk"
curl --fail --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file unhooked-clone.apk "$PKG/unhooked-clone.apk"
jq -n --arg v "$VERSION" --arg ig "$IG_VERSION" --arg sha "$CI_COMMIT_SHA" --arg pkg "$PKG" --arg note "$NOTES" '{
  name: ("Unhooked " + $v),
  tag_name: ("v" + $v),
  ref: $sha,
  description: ("## Unhooked " + $v + "\nBased on Instagram " + $ig + "\n\n" + $note),
  assets: { links: [
    { name: "unhooked-classic.apk", url: ($pkg + "/unhooked-classic.apk"), link_type: "other" },
    { name: "unhooked-clone.apk", url: ($pkg + "/unhooked-clone.apk"), link_type: "other" }
  ] }
}' > release.json
curl --fail --request POST --header "PRIVATE-TOKEN: $GITLAB_API_TOKEN" --header "Content-Type: application/json" --data @release.json "$CI_API_V4_URL/projects/$CI_PROJECT_ID/releases"
