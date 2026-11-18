#!/usr/bin/env bash
# Unit tests of every module on every target (JVM desktop, Android host, Wasm in Node).
source "$(dirname "$0")/lib.sh"
run_gradle test allTests :core:testAndroidHostTest
