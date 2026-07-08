.PHONY: build test lint format typecheck check clean install

install:
	npm install

build:
	npm run build

test:
	npm run test

lint:
	npm run lint

format:
	npm run format

typecheck:
	npm run typecheck

check: format lint typecheck test build
	@echo "All checks passed."

clean:
	rm -rf dist/ coverage/ node_modules/.cache/
