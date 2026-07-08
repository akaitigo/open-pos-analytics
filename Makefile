# open-pos-analytics ルート Makefile — backend/frontend へ委譲
# check は全プロジェクト共通のインターフェース。順序を変更しない。

.PHONY: build test lint format check quality clean harvest

build:
	$(MAKE) -C backend build
	$(MAKE) -C frontend build

test:
	$(MAKE) -C backend test
	$(MAKE) -C frontend test

lint:
	$(MAKE) -C backend lint
	$(MAKE) -C frontend lint

format:
	$(MAKE) -C backend format
	$(MAKE) -C frontend format

check: lint test build
	@echo "All checks passed."

quality:
	@echo "=== Quality Gate ==="
	@test -f LICENSE || { echo "ERROR: LICENSE missing. Fix: add MIT LICENSE file"; exit 1; }
	@! grep -rn "TODO\|FIXME\|HACK\|console\.log\|println\|print(" backend/src frontend/src 2>/dev/null | grep -v "node_modules" || { echo "ERROR: debug output or TODO found. Fix: remove before ship"; exit 1; }
	@! grep -rn "password=\|secret=\|api_key=\|sk-\|ghp_" backend/src frontend/src 2>/dev/null | grep -v '\$${' | grep -v "node_modules" || { echo "ERROR: hardcoded secrets. Fix: use env vars"; exit 1; }
	@test ! -f CLAUDE.md || [ $$(wc -l < CLAUDE.md) -le 50 ] || { echo "ERROR: CLAUDE.md is $$(wc -l < CLAUDE.md) lines (max 50). Fix: use pointers only"; exit 1; }
	@echo "OK: automated quality checks passed"
	@echo "Manual checks required: README quickstart, demo GIF, input validation, ADR >=1"

clean:
	$(MAKE) -C backend clean
	$(MAKE) -C frontend clean

harvest:
	@mkdir -p docs
	@echo "# Harvest: open-pos-analytics" > docs/harvest.md
	@echo "| 項目 | 値 |" >> docs/harvest.md
	@echo "|------|-----|" >> docs/harvest.md
	@echo "| コミット数 | $$(git log --oneline --no-merges | wc -l) |" >> docs/harvest.md
	@echo "| ADR数 | $$(ls docs/adr/[0-9]*.md 2>/dev/null | wc -l) |" >> docs/harvest.md
	@echo "| CLAUDE.md行数 | $$(wc -l < CLAUDE.md 2>/dev/null || echo 0) |" >> docs/harvest.md
	@echo "Harvest report generated: docs/harvest.md"
