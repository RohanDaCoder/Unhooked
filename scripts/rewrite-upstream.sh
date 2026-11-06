#!/usr/bin/env bash
# Rebuild the local `rewritten-upstream` branch from FeurStagram, the project
# Unhooked forked (github.com/jean-voila/FeurStagram).
#
# Unhooked is a rebrand of upstream: its sources live under com/unhooked/
# (upstream: com/feurstagram/) and every Feurstagram string became an
# Unhooked string. Raw upstream commits therefore do not apply to this tree.
# This script rewrites upstream history with the same rebrand rules — the
# text replacements in scripts/upstream-replacements.txt plus the three
# package-directory renames below — so upstream commits can be cherry-picked
# straight onto Unhooked branches:
#
#   git log --oneline rewritten-upstream ^dev     # what's new upstream
#   git cherry-pick -x <sha>                      # port a commit
#
# The rewrite is deterministic: re-running the script reproduces the same
# hashes, so shas referenced by earlier port work stay valid as upstream
# grows. Commits whose only change was Feurstagram casing collapse into
# their parents (empty after replacement) and are dropped — that is expected.
#
# NOTE: scripts/upstream-replacements.txt must contain only rules — no
# comments, not even stray '#' lines. git-filter-repo's --replace-text does
# not strip comments (a bare '#' would rewrite every '#' in the tree), and
# any line without '==>' is still treated as a literal to replace.
#
# Usage:  scripts/rewrite-upstream.sh
# Needs:  git-filter-repo  (pip install git-filter-repo), network access.
#
# The first run registers the `upstream` remote; later runs only re-fetch.
# The result lands on the local branch `rewritten-upstream`, which is a
# disposable mirror — never merge it, only cherry-pick from it.

set -euo pipefail

UPSTREAM_URL="https://github.com/jean-voila/FeurStagram.git"
BRANCH="rewritten-upstream"

root="$(git rev-parse --show-toplevel)"
cd "$root"

if ! command -v git-filter-repo >/dev/null 2>&1; then
    echo "error: git-filter-repo is not installed (pip install git-filter-repo)" >&2
    exit 1
fi

if ! git remote get-url upstream >/dev/null 2>&1; then
    git remote add upstream "$UPSTREAM_URL"
    echo "added remote 'upstream' -> $UPSTREAM_URL"
fi

echo "fetching upstream/main..."
git fetch upstream main

rules="$root/scripts/upstream-replacements.txt"
if [ ! -f "$rules" ]; then
    echo "error: missing $rules" >&2
    exit 1
fi

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

echo "rewriting upstream history in a scratch repo..."
git init -q --bare "$scratch/rewrite.git"
git -C "$scratch/rewrite.git" fetch -q "$root" refs/remotes/upstream/main:refs/heads/main
git -C "$scratch/rewrite.git" filter-repo --force \
    --replace-text "$rules" \
    --path-rename patches/src/main/kotlin/com/feurstagram/:patches/src/main/kotlin/com/unhooked/ \
    --path-rename extensions/extension/src/main/java/com/feurstagram/:extensions/extension/src/main/java/com/unhooked/ \
    --path-rename patches/src/main/resources/feurstagram/:patches/src/main/resources/unhooked/

git fetch -q "$scratch/rewrite.git" "+refs/heads/main:refs/heads/$BRANCH"

echo
echo "done: $BRANCH -> $(git rev-parse --short "$BRANCH") $(git log -1 --format=%s "$BRANCH")"
echo "cherry-pick from $BRANCH with:  git cherry-pick -x <sha>"
