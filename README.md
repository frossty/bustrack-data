# bustrack-data — Automated GTFS Database Pipeline

[![Check GTFS Feed](https://github.com/frossty/bustrack-data/actions/workflows/check-gtfs.yml/badge.svg)](https://github.com/frossty/bustrack-data/actions/workflows/check-gtfs.yml)

Automatically builds and publishes Halifax Transit schedule databases for the BusTrack Android app.

## How it works

1. **GitHub Actions** runs weekly (Monday 6 AM UTC) or on manual trigger
2. Downloads the Halifax Transit GTFS feed
3. Compares hash against last known feed — skips if unchanged
4. Builds a SQLite database from the GTFS data
5. Creates a GitHub Release with the compressed database
6. Updates `docs/version.json` (served via GitHub Pages)

The BusTrack Android app checks `version.json` on launch and downloads new databases automatically — no APK update needed.

## Manual usage

```bash
# Full pipeline (download GTFS + build DB)
./gradlew run

# Use a local GTFS file
./gradlew run --args="--local path/to/gtfs.zip"

# Specify version and output directory
./gradlew run --args="--version 29 --output ./output"
```

## Output

```
output/
  busTrack.db        # Raw SQLite database
  busTrack.db.gz     # Gzip compressed
  version.json       # Version manifest with SHA-256 hash
```

## Version manifest

The Android app fetches `version.json` from GitHub Pages:

```
https://frossty.github.io/bustrack-data/version.json
```

## Setup

1. Enable GitHub Pages: Settings > Pages > Source: main branch, `/docs` folder
2. The workflow needs default `GITHUB_TOKEN` permissions (write to releases + repo contents)
