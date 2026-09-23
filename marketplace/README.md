# DevForge Marketplace backend

This is the first-party Marketplace API backend for DevForge.

## Stack

- Cloudflare Workers — API
- Cloudflare D1 — package catalog metadata
- Cloudflare R2 — canonical immutable .devforge package releases

The Android app is not the canonical storage location. Published packages remain in the Marketplace backend after a developer deletes the Android app or source repository.

## Deploy

1. Create a Cloudflare account.
2. Install/authenticate Wrangler.
3. Create a D1 database named devforge-marketplace.
4. Create an R2 bucket named devforge-marketplace-packages.
5. Put the returned D1 database ID into wrangler.toml.
6. Set a strong PUBLISH_TOKEN secret.
7. Apply schema.sql.
8. Deploy the Worker.
9. Put the deployed HTTPS Worker URL into DevForge → More → Marketplace → settings.

Example commands:

    wrangler d1 create devforge-marketplace
    wrangler r2 bucket create devforge-marketplace-packages
    wrangler d1 execute devforge-marketplace --file=./schema.sql
    wrangler secret put PUBLISH_TOKEN
    wrangler deploy

The current publish endpoint uses a publisher token as an initial bootstrap mechanism. A developer account/OAuth portal can replace this later without changing the catalog/package model.

## API

- GET /v1/health
- GET /v1/packages?query=&type=&limit=
- GET /v1/packages/:id
- GET /v1/packages/:id/releases/:version/download
- POST /v1/publish (Bearer publisher token)

The publish endpoint accepts multipart form data:

- manifest — manifest.json
- package — the immutable .devforge package
- releaseNotes — optional text

GitHub remains suitable for source code and SDK development, but it is not the package registry.
