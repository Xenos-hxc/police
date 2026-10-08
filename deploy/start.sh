#!/usr/bin/env bash
set -euo pipefail
sudo systemctl start railway-security-check
sudo systemctl status railway-security-check --no-pager
