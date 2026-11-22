#!/usr/bin/env bash
# merge-upstream.sh — merge upstream FeurStagram history into Unhooked.
#
# Unhooked carries upstream's source identity (com/feurstagram packages,
# resources and runtime ids) but its own user-facing brand (settings UI,
# toasts, prompts, updater endpoints) and fork-coupled ids (clone package
# com.instagram.android.unhooked, unhooked_prefs store, UNHOOKED_* env
# interface, unhooked-base registry).
#
# This script merges upstream/main into a working branch and then re-applies
# the fork's brand layer with the replacement rules in
# scripts/upstream-replacements.txt (Unhooked==>Feurstagram and friends —
# reverse of the brand tokens) restricted to files where Unhooked branding
# must show through:
#   extensions/extension/src/main/java (user-facing strings)
#   patches/src/main/kotlin (patch descriptions shown in build output)
#
# Fork-coupled ids and Gradle/CI files are never touched, so pipeline
# variables, registry URLs and output names stay Unhooked across merges.
#
# Usage:  scripts/merge-upstream.sh [base-branch]
# Result: branch `upstream-merge` ready for review; push and open an MR.

set -euo pipefail

UPSTREAM_URL="https://github.com/jean-voila/FeurStagram.git"
BRANCH="upstream-merge"
BASE="${1:-main}"

root="$(git rev-parse --show-toplevel)"
cd "$root"

if ! git remote get-url upstream >/dev/null 2>&1; then
    git remote add upstream "$UPSTREAM_URL"
    echo "added remote 'upstream' -> $UPSTREAM_URL"
fi

echo "fetching upstream/main..."
git fetch upstream main

echo "creating $BRANCH from $BASE..."
git branch -f "$BRANCH" "$BASE" 2>/dev/null || true
git checkout -q "$BRANCH"

echo "merging upstream/main..."
if ! git merge upstream/main --no-edit; then
    echo "upstream merge has conflicts — resolve them, keep fork-coupled ids, then re-run brand pass below" >&2
    exit 1
fi

# Brand pass: restore the Unhooked user-facing brand on the merged tree.
# Source identity (com.feurstagram.*) is upstream's and stays; only the
# tokens below flip, and only in the two user-facing source roots.
rules="$root/scripts/upstream-replacements.txt"
if [ ! -f "$rules" ]; then
    echo "error: missing $rules" >&2
    exit 1
fi

while IFS= read -r rule; do
    from="${rule%%==>*}"; to="${rule#*==>}"
    [ -z "$from" ] && continue
    while IFS= read -r f; do
        sed -i "s/$from/$to/g" "$f"
    done < <(grep -rl "$from" extensions/extension/src/main/java patches/src/main/kotlin 2>/dev/null || true)
done < "$rules"

git add -A
git commit -q -m "brand: re-apply Unhooked user-facing brand after upstream merge" || true

echo
echo "done: $BRANCH -> $(git rev-parse --short "$BRANCH")"
echo "verify (build + tests), then push and open an MR"
