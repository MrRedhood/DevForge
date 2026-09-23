# DevForge Marketplace backend

This is the first-party Marketplace API for DevForge native `.devforge` packages.

## Architecture

- Cloudflare Workers — HTTPS catalog/download/publish API
- Cloudflare D1 — package and release metadata
- Cloudflare R2 — canonical immutable package bytes

The Android app is never the source of truth. Published packages remain available even when a developer deletes the DevForge app or deletes the source repository.

## What you need

You need a Cloudflare account for the production Marketplace.

You also need:
- One D1 database: `devforge-marketplace`
- One R2 bucket: `devforge-marketplace-packages`
- A scoped Cloudflare API token for CI deployment
- The Cloudflare account ID
- A strong Marketplace publisher token for the bootstrap publish endpoint

A custom domain is optional. A `workers.dev` URL is enough for the first production deployment; the Android app can be pointed at that HTTPS URL from More → Marketplace → settings.

## First deployment

From this directory:

```bash
npm install
npx wrangler login

npx wrangler d1 create devforge-marketplace
npx wrangler r2 bucket create devforge-marketplace-packages
```

Copy the D1 database ID returned by `wrangler d1 create` into `wrangler.toml`:

```toml
database_id = "YOUR_D1_DATABASE_ID"
```

Create the publisher token:

```bash
npx wrangler secret put PUBLISH_TOKEN
```

Apply the schema and deploy:

```bash
npx wrangler d1 execute devforge-marketplace --remote --file=./schema.sql
npx wrangler deploy
```

The resulting HTTPS Worker URL is the Marketplace API base URL.

## GitHub Actions deployment

The repository includes `.github/workflows/deploy-marketplace.yml`.

Add these GitHub repository secrets:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`
- `DEVFORGE_MARKETPLACE_D1_DATABASE_ID`

The workflow deploys the Worker whenever `marketplace/**` changes on `main`.

Cloudflare's GitHub Actions documentation recommends an API token and account ID for non-interactive CI authentication. Keep the token only in GitHub Actions secrets, never in the repository.

## Connect the Android app

Open:

`More → Marketplace → Settings`

Enter the deployed HTTPS Worker URL.

The app uses the first-party API only:

- `GET /v1/health`
- `GET /v1/packages?query=&type=&limit=`
- `GET /v1/packages/:id`
- `GET /v1/packages/:id/releases/:version/download`
- `POST /v1/publish`

There are no Acode or VS Code marketplace requests in the DevForge Marketplace client.

## Publishing

The bootstrap publisher endpoint accepts multipart form data:

- `manifest` — native DevForge `manifest.json`
- `package` — immutable `.devforge` package
- `releaseNotes` — optional text

Publish with:

```http
Authorization: Bearer <PUBLISH_TOKEN>
```

Published package versions are immutable. The Worker stores the package bytes in R2 and the catalog metadata in D1.

The bootstrap token is intentionally simple for the first deployment. A developer account/OAuth portal, publisher-scoped tokens, package signing, automated security scanning, ratings/reviews, and a public developer portal are the next Marketplace stages.
