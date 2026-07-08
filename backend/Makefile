.PHONY: build test lint format check clean

build:
	./gradlew build

test:
	./gradlew test

lint:
	./gradlew detekt

format:
	@echo 'formatter未導入（detekt は静的解析のみ）。kotlinter 導入は Issue #7 で対応'

check: clean format lint test build
	@echo "All checks passed."

clean:
	./gradlew clean
