#!/usr/bin/env bash
# Convenience launcher so the QA simulator can be run without remembering
# `node simulate.mjs`, e.g. `./run.sh --players 10 --force PRIEST`.
# See README.md.
set -e
cd "$(dirname "$0")"
exec node simulate.mjs "$@"
