.DEFAULT_GOAL := build

build:
	@scripts/make.sh build

clean:
	@scripts/make-clean.sh

test:
	@scripts/make-test.sh

.PHONY: build clean test
