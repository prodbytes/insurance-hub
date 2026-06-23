#!/usr/bin/env bash
# Upload every model unit under ih-models/ to Aletyx Decision Control and make
# each model's runtime URL available to the app as an environment variable.
#
# Each direct subdirectory of ih-models/ is a "unit": its files are zipped and
# POSTed to /api/management/upload (the zip's base name becomes the unit name),
# then the new version is enabled. Units that already exist are left untouched.
#
# For units listed in MODEL_URL_VARS, the model's runtime execution URL is
# resolved from Decision Control and exported under the named variable, which
# application.properties references (ih.quote.vehicle-price.url=${IH_QUOTE_VEHICLE_PRICE_URL:}),
# so the generated model id never has to be hard-coded there. The IH_ prefix
# namespaces the app's own variables.
#
# Meant to be sourced (e.g. from dev-start.sh) so the exports reach the app, but
# also runnable standalone.

# Unit name -> environment variable that should receive its runtime URL.
declare -A MODEL_URL_VARS=(
  ["car"]="IH_QUOTE_VEHICLE_PRICE_URL"
)

# Resolves a unit's model runtime URL from Decision Control and exports it under
# the variable bound in MODEL_URL_VARS (a no-op for units without a binding).
export_model_url() {
  local unit_name="$1" dc_base="$2" explorer_url="$3" var_name url_path
  var_name="${MODEL_URL_VARS[$unit_name]:-}"
  [ -n "$var_name" ] || return 0

  # The bare execution path is /api/runtime/0/<unit>/latest/<modelId> — pick it
  # (excluding the sibling .../latest/batch path) so the model id isn't hard-coded.
  url_path="$(curl -s -m 15 "$explorer_url" \
    | jq -r --arg u "$unit_name" '.paths | keys[]
        | select(test("^/api/runtime/0/" + $u + "/latest/[^/]+$"))
        | select(endswith("/batch") | not)' | head -n1)"

  if [ -z "$url_path" ]; then
    echo "   ↳ ⚠ could not resolve runtime URL for '$unit_name'; $var_name not set" >&2
    return 0
  fi
  export "$var_name=$dc_base$url_path"
  echo "   ↳ exported $var_name=$dc_base$url_path"
}

models_upload_main() {
  set -uo pipefail
  set +e  # explicit error handling below; don't abort a caller running under set -e

  local SCRIPT_DIR REPO_ROOT MODELS_DIR DC_BASE_URL UPLOAD_URL UNITS_URL EXPLORER_URL
  local EMOJI_OK="✅" EMOJI_FAIL="❌" EMOJI_SKIP="⏭️"
  local UNITS_JSON TMP_DIR failures=0 found=0
  local unit_dir unit_name existing_id zip_path body http_code unit_id version_id enable_code

  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  REPO_ROOT="$(dirname "$SCRIPT_DIR")"
  if [ -f "$REPO_ROOT/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$REPO_ROOT/.env"
    set +a
  fi

  MODELS_DIR="${MODELS_DIR:-$REPO_ROOT/ih-models}"
  DC_BASE_URL="${DC_BASE_URL:-http://localhost:8081}"
  UPLOAD_URL="$DC_BASE_URL/api/management/upload"
  UNITS_URL="$DC_BASE_URL/api/management/units"
  EXPLORER_URL="$DC_BASE_URL/api/explorer"

  command -v zip  >/dev/null 2>&1 || { echo "$EMOJI_FAIL zip is required"  >&2; return 1; }
  command -v curl >/dev/null 2>&1 || { echo "$EMOJI_FAIL curl is required" >&2; return 1; }
  command -v jq   >/dev/null 2>&1 || { echo "$EMOJI_FAIL jq is required"   >&2; return 1; }
  [ -d "$MODELS_DIR" ] || { echo "$EMOJI_FAIL models dir not found: $MODELS_DIR" >&2; return 1; }

  UNITS_JSON="$(curl -s -m 30 "$UNITS_URL")"
  if ! printf '%s' "$UNITS_JSON" | jq -e 'type == "array"' >/dev/null 2>&1; then
    echo "$EMOJI_FAIL could not list existing units from $UNITS_URL (is DC running?)" >&2
    return 1
  fi

  TMP_DIR="$(mktemp -d)"
  echo "Uploading model units from $MODELS_DIR to $UPLOAD_URL"

  for unit_dir in "$MODELS_DIR"/*/; do
    [ -d "$unit_dir" ] || continue
    unit_name="$(basename "${unit_dir%/}")"
    found=$((found + 1))

    existing_id="$(printf '%s' "$UNITS_JSON" | jq -r --arg n "$unit_name" \
      '.[] | select(.name == $n) | .id' | head -n1)"

    if [ -n "$existing_id" ]; then
      echo "$EMOJI_SKIP $unit_name already exists (unit $existing_id) — skipping upload"
    else
      if [ -z "$(find "$unit_dir" -type f -print -quit)" ]; then
        echo "$EMOJI_FAIL $unit_name (no model files, skipped)"
        failures=$((failures + 1)); continue
      fi

      zip_path="$TMP_DIR/$unit_name.zip"
      if ! ( cd "$unit_dir" && zip -qr "$zip_path" . ); then
        echo "$EMOJI_FAIL $unit_name (zip failed)"
        failures=$((failures + 1)); continue
      fi

      body="$(curl -s -m 60 -o - -w $'\n%{http_code}' \
        -X POST "$UPLOAD_URL" -F "file=@$zip_path;type=application/zip")"
      http_code="${body##*$'\n'}"
      body="${body%$'\n'*}"
      if [ "$http_code" != "200" ]; then
        echo "$EMOJI_FAIL $unit_name upload failed (HTTP $http_code) $body"
        failures=$((failures + 1)); continue
      fi

      unit_id="$(printf '%s' "$body" | jq -r '.unitId // empty')"
      version_id="$(printf '%s' "$body" | jq -r '.versionId // empty')"
      if [ -z "$unit_id" ] || [ -z "$version_id" ]; then
        echo "$EMOJI_FAIL $unit_name uploaded but unitId/versionId missing from response: $body"
        failures=$((failures + 1)); continue
      fi

      enable_code="$(curl -s -m 30 -o /dev/null -w '%{http_code}' \
        -X PATCH "$DC_BASE_URL/api/management/units/$unit_id/versions/$version_id/enable")"
      if [ "$enable_code" = "200" ]; then
        echo "$EMOJI_OK $unit_name uploaded and enabled (unit $unit_id, version $version_id)"
      else
        echo "$EMOJI_FAIL $unit_name uploaded (unit $unit_id, version $version_id) but enable failed (HTTP $enable_code)"
        failures=$((failures + 1)); continue
      fi
    fi

    # Whether just uploaded or already present, expose its runtime URL to the app.
    export_model_url "$unit_name" "$DC_BASE_URL" "$EXPLORER_URL"
  done

  rm -rf "$TMP_DIR"

  if [ "$found" -eq 0 ]; then
    echo "$EMOJI_FAIL no unit directories found under $MODELS_DIR" >&2
    return 1
  fi
  if [ "$failures" -gt 0 ]; then
    echo "Done with $failures failure(s)."
    return 1
  fi
  echo "Done. Processed $found unit(s)."
  return 0
}

models_upload_main "$@"
_models_upload_rc=$?
# When executed directly, propagate the status. When sourced, leave the exported
# variables in the caller's environment without exiting it.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  exit "$_models_upload_rc"
fi
