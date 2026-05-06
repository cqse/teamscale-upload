#!/bin/bash
# Updates the Homebrew tap repository with a new version of teamscale-upload.
# Self-gates on the release tag so non-release runs (branch pushes, PRs,
# manual workflow_dispatch without a tag) become no-ops.
#
# Modelled on teamscale's .gitlab/ci/ide/ts-dev-cli/update-homebrew.sh, but
# adapted to GitHub Actions: the GitHub release flow does not publish .sha256
# siblings next to the assets, so we download the zips and hash them locally.
#
# We publish a Homebrew cask (not a formula) because Homebrew's formula
# installer relocates dylibs and re-signs them, which corrupts the bundled
# jLink JDK on macOS 26 (Tahoe). Casks just stage and symlink the artifact.
#
# Top-level steps:
#   1. Resolve the release tag from $GITHUB_REF_NAME (or $1) and bail out
#      unless it matches vX.Y.Z.
#   2. Download the macOS aarch64 and x86_64 release zips from the GitHub
#      release and compute their SHA256 checksums.
#   3. Clone the Homebrew tap (gitlab.com/cqse/public/homebrew-teamscale) into
#      a throwaway temp dir.
#   4. Render cask.rb.template into Casks/teamscale-upload.rb.
#   5. Remove any leftover Formula/teamscale-upload*.rb files from the
#      previous formula-based publishing flow (no-op after the first run).
#   6. Commit and push to the tap (push is skipped if DRY_RUN=1).
#
# Required environment variables:
#   GITHUB_REF_NAME            - Tag name (e.g. v9.1.2). Falls back to $1.
#   HOMEBREW_REPO_GITLAB_TOKEN - GitLab token (write_repository) for
#                                cqse/public/homebrew-teamscale.
#   DRY_RUN                    - Optional. If set to "1", skips the git push
#                                so the script can be exercised on a fork
#                                without publishing. The local clone, cask
#                                generation, and git commit still happen, but
#                                only inside the throwaway $WORK_DIR that is
#                                removed on exit, so nothing escapes.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_PATH="$SCRIPT_DIR/cask.rb.template"

# Generates a Homebrew cask by substituting placeholders in the Ruby template.
# Arguments: version, aarch64_sha, x86_64_sha
generate_cask() {
  local version=$1
  local aarch64_sha=$2
  local x86_64_sha=$3

  sed \
    -e "s|__VERSION__|${version}|g" \
    -e "s|__AARCH64_SHA__|${aarch64_sha}|g" \
    -e "s|__X86_64_SHA__|${x86_64_sha}|g" \
    "$TEMPLATE_PATH"
}

# Read the tag from the GitHub Actions context, or from $1 for manual runs.
# Default to empty so the script is safe to call without a tag (set -u).
TAG="${GITHUB_REF_NAME:-${1:-}}"
VERSION="${TAG#v}"

# Only proceed for proper release tags matching X.Y.Z. Branch pushes (no tag),
# PRs, and pre-release tags become no-ops. Unlike teamscale-dev (which streams
# artifacts under a v{M.N.x}/ directory and overwrites them per release),
# teamscale-upload assets are pinned per tag and immutable, so the cask
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
mkdir -p Casks

echo "Recreating the cask for ${VERSION}..."
generate_cask "$VERSION" "$AARCH64_SHA" "$X86_64_SHA" > Casks/teamscale-upload.rb
git add Casks/teamscale-upload.rb

# Clean up any leftover formula files from the previous formula-based publishing
# flow. After the first cask release this is a no-op. We use --ignore-unmatch so
# the script keeps working once the files are gone.
git rm -f --ignore-unmatch Formula/teamscale-upload.rb 'Formula/teamscale-upload@*.rb'

git config user.email "ci@cqse.eu"
git config user.name "GitHub Actions"
git commit -m "Update cask for teamscale-upload to $VERSION" || echo "No changes to commit"

if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "DRY_RUN=1; skipping git push."
else
  git push
fi

echo "Homebrew tap updated successfully"
