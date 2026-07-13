#!/usr/bin/env bash
# Upload every DMN model under ih-models/ to Aletyx Decision Control and make
# each model's runtime URL available to the app as an environment variable.
# BPMN models are NOT handled here — process deployment happens separately.
#
# Each .dmn file under ih-models/ is uploaded individually as a single-file
# zip named "<file>.zip" (the upload endpoint only accepts zips, and names the
# unit after the zip's base name), so the unit is named after the model file
# (e.g. car-quote.dmn) — the same name Decision Control's own "Upload DMN" /
# editor publish flow uses. That way, editing a model in the DC editor and
# publishing it adds a version to the SAME unit instead of creating a
# duplicate. Uploading a name that already exists creates a new
# version of that unit, and enabling it makes the unit's /latest runtime URL
# serve it — so re-running this script after editing a model redeploys the
# edit. Identical re-uploads still create a version, which is fine for dev:
# DC state lives in the throwaway database container, so versions never
# outlive the stack.
#
# For every uploaded unit, the model's runtime execution URL is resolved from
# Decision Control and exported under a variable derived from the model file
# name: IH_<MODEL_NAME_UPPER_SNAKE>_URL (e.g. carQuote.dmn -> IH_CAR_QUOTE_URL).
# application.properties references these (ih.quote.vehicle-price.url=${IH_CAR_QUOTE_URL:}),
# so the generated model id never has to be hard-coded there. The IH_ prefix
# namespaces the app's own variables.
#
# Meant to be sourced (e.g. from dev-start.sh) so the exports reach the app, but
# also runnable standalone.

# Reduce an HTTP error response body to a single displayable message: the JSON
# .message/.error field when present, the raw body otherwise, or a placeholder
# when the server sent nothing back.
error_detail() {
  local body="$1" msg
  msg="$(printf '%s' "$body" | jq -r '.message // .error // empty' 2>/dev/null)"
  printf '%s' "${msg:-${body:-<empty response body>}}"
}

# IH_<UPPER_SNAKE>_URL variable name for a model base name, e.g.
# carQuote -> IH_CAR_QUOTE_URL, carEstimatedValue -> IH_CAR_ESTIMATED_VALUE_URL.
model_url_var_name() {
  printf 'IH_%s_URL' "$(printf '%s' "$1" \
    | sed -E 's/([a-z0-9])([A-Z])/\1_\2/g; s/[^A-Za-z0-9]+/_/g' \
    | tr '[:lower:]' '[:upper:]')"
}

# Resolves a unit's model runtime URL from Decision Control and exports it under
# the derived IH_<MODEL_NAME>_URL variable. Prints the export on success and a
# clear failure otherwise (non-zero return so the caller counts it).
export_model_url() {
  local unit_name="$1" dc_base="$2" explorer_url="$3" var_name url_path
  var_name="$(model_url_var_name "${unit_name%.*}")"

  # The bare execution path is /api/runtime/<tenant>/<unit>/latest/<modelId> —
  # pick it (excluding the sibling .../latest/batch and .../<modelId>/{batch,schema}
  # paths) without hard-coding the tenant or the model id.
  local api_docs candidates
  api_docs="$(curl -s -m 15 "$explorer_url")"
  candidates="$(printf '%s' "$api_docs" \
    | jq -r --arg unit "$unit_name" '.paths // {} | keys[]
        | select(startswith("/api/runtime/"))
        | select((ltrimstr("/api/runtime/") | split("/"))
            | length == 4 and .[1] == $unit and .[2] == "latest" and .[3] != "batch")')"
  url_path="$(printf '%s\n' "$candidates" | head -n1)"

  # A unit zip can bundle several models, which gives the
  # unit one runtime path per model. Prefer the path whose model id segment or
  # operation entry mentions the unit's own model name.
  if [ "$(printf '%s\n' "$candidates" | grep -c .)" -gt 1 ]; then
    local named_path
    named_path="$(printf '%s' "$api_docs" \
      | jq -r --arg unit "$unit_name" --arg model "${unit_name%.*}" '.paths // {} | to_entries[]
          | select(.key | startswith("/api/runtime/"))
          | (.key | ltrimstr("/api/runtime/") | split("/")) as $seg
          | select($seg | length == 4 and .[1] == $unit and .[2] == "latest" and .[3] != "batch")
          | select(($seg[3] + (.value | tostring)) | contains($model))
          | .key' \
      | head -n1)"
    if [ -n "$named_path" ]; then
      url_path="$named_path"
    else
      echo "   ↳ ⚠ unit '$unit_name' exposes several models and none matches '${unit_name%.*}'; using $url_path" >&2
    fi
  fi

  if [ -z "$url_path" ]; then
    echo "   ↳ ❌ could not resolve runtime URL for '$unit_name' from $explorer_url; $var_name NOT exported" >&2
    echo "   ↳   response was: $(printf '%s' "$api_docs" | head -c 300)" >&2
    return 1
  fi
  export "$var_name=$dc_base$url_path"
  echo "   ↳ ✅ exported $var_name=$dc_base$url_path"
}

models_upload_main() {
  set -uo pipefail
  set +e  # explicit error handling below; don't abort a caller running under set -e

  local SCRIPT_DIR REPO_ROOT UPLOAD_URL UNITS_URL EXPLORER_URL
  local EMOJI_OK="✅" EMOJI_FAIL="❌"
  local UNITS_JSON TMP_DIR failures=0 found=0
  local model_file unit_name model_name zip_path body http_code unit_id version_id enable_code enable_body

  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  REPO_ROOT="$(dirname "$SCRIPT_DIR")"
  if [ -f "$REPO_ROOT/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$REPO_ROOT/.env"
    set +a
  fi

  # Declared with their defaults only here, after .env is sourced, so values
  # from the environment or .env win over the defaults.
  local MODELS_DIR="${MODELS_DIR:-$REPO_ROOT/ih-models}"
  local DC_BASE_URL="${DC_BASE_URL:-http://localhost:8081}"
  UPLOAD_URL="$DC_BASE_URL/api/management/upload"
  UNITS_URL="$DC_BASE_URL/api/management/units"
  EXPLORER_URL="$DC_BASE_URL/v3/api-docs"

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
  echo "Uploading models from $MODELS_DIR to $UPLOAD_URL"

  while IFS= read -r -d '' model_file; do
    unit_name="$(basename "$model_file")"
    model_name="${unit_name%.*}"
    found=$((found + 1))

    # DMN editors name new models "DMN_<uuid>"; Decision Control displays that
    # name, not the file name. Rewrite auto-generated names to the file's base
    # name — in the source tree, so the repo, the editor, and DC all agree.
    if grep -q '<definitions .*name="DMN_[0-9A-Fa-f-]\{36\}"' "$model_file"; then
      sed -i "/<definitions /s/name=\"DMN_[0-9A-Fa-f-]\{36\}\"/name=\"$model_name\"/" "$model_file"
      echo "   ↳ renamed auto-generated model name in ${model_file#"$MODELS_DIR"/} to '$model_name'"
    fi

    # Each .dmn file is uploaded individually. The upload endpoint only
    # accepts zip bodies, so the single file is wrapped in its own zip.
    zip_path="$TMP_DIR/$unit_name.zip"
    if ! zip -qj "$zip_path" "$model_file"; then
      echo "$EMOJI_FAIL $unit_name (zip failed)"
      failures=$((failures + 1)); continue
    fi

    body="$(curl -s -m 60 -o - -w $'\n%{http_code}' \
      -X POST "$UPLOAD_URL" -F "file=@$zip_path;type=application/zip")"
    http_code="${body##*$'\n'}"
    body="${body%$'\n'*}"
    if [ "$http_code" != "200" ]; then
      echo "$EMOJI_FAIL $unit_name upload failed (HTTP $http_code): $(error_detail "$body")"
      echo "   ↳ zip contained: $(zipinfo -1 "$zip_path" 2>/dev/null | tr '\n' ' ')"
      echo "   ↳ for the server-side stack trace check the decision-control process logs"
      failures=$((failures + 1)); continue
    fi

    unit_id="$(printf '%s' "$body" | jq -r '.unitId // empty')"
    version_id="$(printf '%s' "$body" | jq -r '.versionId // empty')"
    if [ -z "$unit_id" ] || [ -z "$version_id" ]; then
      echo "$EMOJI_FAIL $unit_name uploaded but unitId/versionId missing from response: $body"
      failures=$((failures + 1)); continue
    fi

    # Enabling the new version is what moves the unit's /latest alias to it.
    enable_body="$(curl -s -m 30 -o - -w $'\n%{http_code}' \
      -X PATCH "$DC_BASE_URL/api/management/units/$unit_id/versions/$version_id/enable")"
    enable_code="${enable_body##*$'\n'}"
    enable_body="${enable_body%$'\n'*}"
    if [ "$enable_code" = "200" ]; then
      echo "$EMOJI_OK $unit_name uploaded and enabled (unit $unit_id, version $version_id)"
    else
      echo "$EMOJI_FAIL $unit_name uploaded (unit $unit_id, version $version_id) but enable failed (HTTP $enable_code): $(error_detail "$enable_body")"
      failures=$((failures + 1)); continue
    fi

    # Expose the unit's version-independent /latest runtime URL to the app.
    if ! export_model_url "$unit_name" "$DC_BASE_URL" "$EXPLORER_URL"; then
      failures=$((failures + 1))
    fi
  done < <(find "$MODELS_DIR" -type f -name '*.dmn' -print0 | sort -z)

  rm -rf "$TMP_DIR"

  if [ "$found" -eq 0 ]; then
    echo "$EMOJI_FAIL no .dmn files found under $MODELS_DIR" >&2
    return 1
  fi
  if [ "$failures" -gt 0 ]; then
    echo "Done with $failures failure(s)."
    return 1
  fi
  echo "Done. Processed $found model(s)."
  return 0
}

models_upload_main "$@"
_models_upload_rc=$?
# When executed directly, propagate the status. When sourced, leave the exported
# variables in the caller's environment without exiting it.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  exit "$_models_upload_rc"
fi
