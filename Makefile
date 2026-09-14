.DEFAULT_GOAL := build

.PHONY: build test check probe signing connect install deploy sports sports-build sports-install sports-deploy
build test check probe signing connect install deploy sports sports-build sports-install sports-deploy:
	$(MAKE) -C android $@
