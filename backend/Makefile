.PHONY: build test lint format check clean

build:
	./gradlew build

test:
	./gradlew test

lint:
	./gradlew detekt

format:
	./gradlew formatKotlin

check: clean format lint test build
	@echo "All checks passed."

clean:
	./gradlew clean
