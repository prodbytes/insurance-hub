.DEFAULT_GOAL := build

build:
	@scripts/make.sh build

clean:
	@scripts/make-clean.sh

.PHONY: build clean
