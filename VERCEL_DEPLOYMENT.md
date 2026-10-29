# Deploying the web app to Vercel

The web application can run as a Vercel container function using the Java Dockerfile. The JavaFX admin remains a desktop application and is not deployed to Vercel; point its `PET_HAVEN_API_BASE_URL` at the deployed HTTPS web app.

## Project setup

1. Import `Gale-Carl23/petAdoption` into Vercel.
2. Set the project Root Directory to `java-pet-donation` and enable including files outside the root directory only if Vercel requests it. The Maven build and Dockerfile are self-contained in this directory.
3. Select the Container framework (also declared in `vercel.json`).
4. Set the production environment variable `PORT` to `8080`. Spring Boot reads this value from `application-production.properties`.
5. Set `SPRING_PROFILES_ACTIVE=production` and add the application's runtime variables through Vercel's Environment Variables settings. Do not add credentials to Git, this file, deployment logs, or chat.

## Connect the production services

The production app needs persistent Postgres and private file storage. The current repository supports Neon Postgres and private Vercel Blob, so AWS credentials are not required.

1. In the Vercel project, open **Storage** and add a Neon Postgres database. Choose a plan that fits your account. Connect it to the `Production` environment; Vercel adds `DATABASE_URL` for you. The application reads that URL directly and extracts its database credentials at startup. Do not copy its value into Git or chat.
2. In **Storage**, create a **private** Vercel Blob store and connect it to this project. Make sure the production runtime receives either `BLOB_READ_WRITE_TOKEN`, or both `VERCEL_OIDC_TOKEN` and `BLOB_STORE_ID`. Donation proofs are uploaded and served only through authenticated app endpoints; the stored Blob is not made public.
3. In **Settings → Environment Variables**, set `SPRING_PROFILES_ACTIVE` to `production` and add `ADMIN_BOOTSTRAP_EMAIL` plus a unique `ADMIN_BOOTSTRAP_PASSWORD` (16–72 bytes). Keep these values private. The first deployment seeds this administrator account if it does not already exist.
4. Save the settings, then open **Deployments** and redeploy the latest `main` deployment so the container starts with the new variables. Check its runtime logs if the deployment still reports an error; the application no longer requires AWS S3.

`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are also accepted if you use a database other than Neon. The database must allow TLS connections from Vercel and support the Flyway migrations and JDBC-backed HTTP sessions.

## Deployment boundary

The Vercel project must be connected to the database and private Blob store before the production app can start. Container functions scale to zero and have temporary local storage, so durable state must remain in external services. Review Vercel's plan-specific container function limits before production use.

PayMongo configuration remains deferred. The app will continue to show that donation destinations are unavailable until a payment method is intentionally configured.
