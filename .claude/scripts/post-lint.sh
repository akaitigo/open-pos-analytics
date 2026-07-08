#!/usr/bin/env bash
set -euo pipefail

input="$(cat)"
file="$(jq -r '.tool_input.file_path // .tool_input.path // empty' <<< "$input")"

case "$file" in *.kt|*.kts) ;; *) exit 0 ;; esac

cd "$(git rev-parse --show-toplevel 2>/dev/null || dirname "$file")"

# 0. ツール存在チェック（detekt は Gradle タスクとして実行するためインストール不要）

# 1. detekt 実行（違反があれば出力）
diag="$(./gradlew detekt 2>/dev/null | tail -5 || true)"
if echo "$diag" | grep -q "BUILD FAILED"; then
  jq -Rn --arg msg "$diag" \
    '{ hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: $msg } }'
fi
