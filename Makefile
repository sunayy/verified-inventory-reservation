.PHONY: verify-spec verify-lean build-native test-pbt generate-traces test-mbt test-backend test-backend-all test-frontend verify

verify-spec:
	npm run verify:spec

verify-lean:
	npm run verify:lean

build-native:
	npm run build:lean-native

test-pbt:
	./backend/gradlew -p backend testPbt --no-daemon

generate-traces:
	npm run generate:traces

test-mbt:
	./backend/gradlew -p backend testMbt --no-daemon

test-backend:
	./backend/gradlew -p backend test --no-daemon

test-backend-all:
	./backend/gradlew -p backend testMbt test --no-daemon

test-frontend:
	npm --prefix frontend run typecheck
	npm --prefix frontend run verify:features
	npm --prefix frontend test
	npm --prefix frontend run build

# `test-backend-all` depends on `buildNativeReservation`, so native build and
# smoke coverage run without a redundant standalone invocation.
verify: verify-spec verify-lean test-backend-all test-frontend
