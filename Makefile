.PHONY: infra-up infra-down api-run api-test admin-install admin-run admin-test admin-build check

infra-up:
	docker compose up -d

infra-down:
	docker compose down

api-run:
	cd apps/api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

api-test:
	cd apps/api && ./mvnw verify

admin-install:
	cd apps/admin && npm install

admin-run:
	cd apps/admin && npm start

admin-test:
	cd apps/admin && npm run test:ci

admin-build:
	cd apps/admin && npm run build

check:
	./scripts/check.sh
