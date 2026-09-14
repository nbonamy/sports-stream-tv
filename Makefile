.DEFAULT_GOAL := build

.PHONY: build test check stream-check probe signing connect install deploy sports sports-build sports-install sports-deploy

stream-check:
	npm run test --workspace=@sports/core
	./android/gradlew -p android :core:test --console=plain

build test check probe signing connect install deploy sports sports-build sports-install sports-deploy:
	$(MAKE) -C android $@
