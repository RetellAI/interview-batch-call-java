#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="$root_dir/dist"
archive_path="$out_dir/retell-interview-java.zip"

mkdir -p "$out_dir"
rm -f "$out_dir"/*.zip

git -C "$root_dir" archive --worktree-attributes --format=zip -o "$archive_path" HEAD

echo "Wrote $archive_path"
