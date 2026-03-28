#!/usr/bin/env bash

set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
default_out_dir="$(cd "$repo_dir/../../.." && pwd)/out"
out_dir="${1:-${OUT_DIR:-$default_out_dir}}"
system_libs_dir="$repo_dir/system_libs"

declare -A jars=(
  ["framework.jar"]="$out_dir/soong/.intermediates/frameworks/base/framework-minus-apex/android_common/combined/framework.jar"
  ["SettingsLib.jar"]="$out_dir/soong/.intermediates/frameworks/base/packages/SettingsLib/SettingsLib/android_common/turbine-combined/SettingsLib.jar"
  ["SpaLib.jar"]="$out_dir/soong/.intermediates/frameworks/base/packages/SettingsLib/Spa/spa/SpaLib/android_common/kotlin/SpaLib.jar"
)

missing=0
for name in "${!jars[@]}"; do
  if [[ ! -f "${jars[$name]}" ]]; then
    echo "Missing source jar for $name: ${jars[$name]}" >&2
    missing=1
  fi
done

if [[ "$missing" -ne 0 ]]; then
  echo "Build the required targets first, then rerun this script." >&2
  exit 1
fi

mkdir -p "$system_libs_dir"

for name in "${!jars[@]}"; do
  install -m 0644 "${jars[$name]}" "$system_libs_dir/$name"
done

echo "Populated $system_libs_dir with:"
for name in $(printf '%s\n' "${!jars[@]}" | sort); do
  echo "  $name"
done
