#!/bin/bash
# Updates the Homebrew tap repository with a new version of teamscale-upload.
# Self-gates on the release tag so non-release runs (branch pushes, PRs,
# manual workflow_dispatch without a tag) become no-ops.
#
# Modelled on teamscale's .gitlab/ci/ide/ts-dev-cli/update-homebrew.sh, but
# adapted to GitHub Actions: the GitHub release flow does not publish .sha256
# siblings next to the assets, so we download the zips and hash them locally.
#
# Top-level steps:
#   1. Resolve the release tag from $GITHUB_REF_NAME (or $1) and bail out
#      unless it matches vX.Y.Z.
#   2. Download the macOS aarch64 and x86_64 release zips from the GitHub
#      release and compute their SHA256 checksums.
#   3. Clone the Homebrew tap (gitlab.com/cqse/public/homebrew-teamscale) into
#      a throwaway temp dir.
#   4. Render formula.rb.template twice: once as the unversioned
#      Formula/teamscale-upload.rb (latest), once as the keg-only
#      Formula/teamscale-upload@X.Y.Z.rb (history).
#   5. Commit both files and push to the tap (push is skipped if DRY_RUN=1).
#
# Required environment variables:
#   GITHUB_REF_NAME            - Tag name (e.g. v9.1.2). Falls back to $1.
#   HOMEBREW_REPO_GITLAB_TOKEN - GitLab token (write_repository) for
#                                cqse/public/homebrew-teamscale.
#   DRY_RUN                    - Optional. If set to "1", skips the git push
#                                so the script can be exercised on a fork
#                                without publishing. The local clone, formula
#                                generation, and git commit still happen, but
#                                only inside the throwaway $WORK_DIR that is
#                                removed on exit, so nothing escapes.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_PATH="$SCRIPT_DIR/formula.rb.template"

# Generates a Homebrew formula by substituting placeholders in the Ruby template.
# Arguments: class_name, version, aarch64_sha, x86_64_sha, keg_only
generate_formula() {
  local class_name=$1
  local version=$2
  local aarch64_sha=$3
  local x86_64_sha=$4
  local keg_only=${5:-false}

  local keg_only_line=""
  if [ "$keg_only" = "true" ]; then
    keg_only_line="  keg_only :versioned_formula"
  fi

  sed \
    -e "s|__CLASS_NAME__|${class_name}|g" \
    -e "s|__VERSION__|${version}|g" \
    -e "s|__AARCH64_SHA__|${aarch64_sha}|g" \
    -e "s|__X86_64_SHA__|${x86_64_sha}|g" \
    -e "s|__KEG_ONLY_LINE__|${keg_only_line}|" \
    "$TEMPLATE_PATH"
}

# Read the tag from the GitHub Actions context, or from $1 for manual runs.
# Default to empty so the script is safe to call without a tag (set -u).
TAG="${GITHUB_REF_NAME:-${1:-}}"
VERSION="${TAG#v}"

# Only proceed for proper release tags matching X.Y.Z. Branch pushes (no tag),
# PRs, and pre-release tags become no-ops. Unlike teamscale-dev (which streams
# artifacts under a v{M.N.x}/ directory and overwrites them per release),
# teamscale-upload assets are pinned per tag and immutable, so the formula
# version equals the tag's patch version directly.
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Tag '${TAG:-<empty>}' is not a release tag matching vX.Y.Z; skipping Homebrew update."
  exit 0
fi

BASE_URL="https://github.com/cqse/teamscale-upload/releases/download/v${VERSION}"

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Updating Homebrew tap for teamscale-upload ${VERSION}"

echo "Downloading release zips and computing checksums..."
curl -sLfo "$WORK_DIR/teamscale-upload-macos-aarch64.zip" "${BASE_URL}/teamscale-upload-macos-aarch64.zip"
curl -sLfo "$WORK_DIR/teamscale-upload-macos-x86_64.zip"  "${BASE_URL}/teamscale-upload-macos-x86_64.zip"

AARCH64_SHA=$(sha256sum "$WORK_DIR/teamscale-upload-macos-aarch64.zip" | cut -d' ' -f1)
X86_64_SHA=$(sha256sum  "$WORK_DIR/teamscale-upload-macos-x86_64.zip"  | cut -d' ' -f1)

echo "  aarch64: ${AARCH64_SHA}"
echo "  x86_64:  ${X86_64_SHA}"

echo "Cloning homebrew tap..."
git clone "https://oauth2:${HOMEBREW_REPO_GITLAB_TOKEN}@gitlab.com/cqse/public/homebrew-teamscale.git" "$WORK_DIR/homebrew-teamscale"
cd "$WORK_DIR/homebrew-teamscale"
mkdir -p Formula

echo "Recreating the main formula for ${VERSION}..."
generate_formula "TeamscaleUpload" "$VERSION" "$AARCH64_SHA" "$X86_64_SHA" false > Formula/teamscale-upload.rb
git add Formula/teamscale-upload.rb

# Versioned formula keeps an installable record of every released version.
NEW_VERSIONED_FILE="Formula/teamscale-upload@${VERSION}.rb"
echo "Creating versioned formula for ${VERSION}..."
VERSION_CLASS=$(echo "$VERSION" | tr -d '.')
generate_formula "TeamscaleUploadAT${VERSION_CLASS}" "$VERSION" "$AARCH64_SHA" "$X86_64_SHA" true > "$NEW_VERSIONED_FILE"
git add "$NEW_VERSIONED_FILE"

git config user.email "ci@cqse.eu"
git config user.name "GitHub Actions"
git commit -m "Update formula for teamscale-upload to $VERSION" || echo "No changes to commit"

if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "DRY_RUN=1; skipping git push."
else
  git push
fi

echo "Homebrew tap updated successfully"
