.DEFAULT_GOAL := build
ANDROID_TV_DEVICE ?= 192.168.1.4
ANDROID_SDK_ROOT ?= $(HOME)/Library/Android/sdk
ADB ?= $(ANDROID_SDK_ROOT)/platform-tools/adb
SPORTS_COMPONENT := fr.bonamy.sports/.MainActivity
SPORTS_APK := app/build/outputs/apk/release/app-release.apk
# adb reports network devices with an explicit port. Emulator and USB serials pass through.
DEVICE_SERIAL := $(if $(filter emulator-%,$(ANDROID_TV_DEVICE)),$(ANDROID_TV_DEVICE),$(if $(findstring :,$(ANDROID_TV_DEVICE)),$(ANDROID_TV_DEVICE),$(if $(findstring .,$(ANDROID_TV_DEVICE)),$(ANDROID_TV_DEVICE):5555,$(ANDROID_TV_DEVICE))))

.PHONY: build test check probe signing connect install deploy sports sports-build sports-install sports-deploy

signing:
	@python3 tools/ensure_signing.py

build: signing
	./gradlew :app:assembleRelease --console=plain
	@mkdir -p release
	@cp $(SPORTS_APK) release/Sports.apk

test:
	./gradlew :core:test --console=plain

check:
	./gradlew :core:test :app:lintDebug :app:assembleDebug --console=plain

probe:
	./gradlew :core:probe --console=plain

connect:
	@if printf '%s' '$(DEVICE_SERIAL)' | grep -q ':'; then $(ADB) connect '$(DEVICE_SERIAL)'; fi
	@$(ADB) -s '$(DEVICE_SERIAL)' get-state > /dev/null

install: build connect
	$(ADB) -s '$(DEVICE_SERIAL)' install -r $(SPORTS_APK)

deploy: install
	$(ADB) -s '$(DEVICE_SERIAL)' shell am force-stop fr.bonamy.sports
	$(ADB) -s '$(DEVICE_SERIAL)' shell am start -W -n $(SPORTS_COMPONENT)

sports: deploy
sports-build: build
sports-install: install
sports-deploy: deploy
