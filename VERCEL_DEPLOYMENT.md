# Deploying the web app to Vercel

The web application can run as a Vercel container function using the Java Dockerfile. The JavaFX admin remains a desktop application and is not deployed to Vercel; point its `PET_HAVEN_API_BASE_URL` at the deployed HTTPS web app.

## Project setup

1. Import `Gale-Carl23/petAdoption` into Vercel.
2. Set the project Root Directory to `java-pet-donation` and enable including files outside the root directory only if Vercel requests it. The Maven build and Dockerfile are self-contained in this directory.
3. Select the Container framework (also declared in `vercel.json`).
4. Set the production environment variable `PORT` to `8080`. Spring Boot reads this value from `application-production.properties`.
5. Set `SPRING_PROFILES_ACTIVE=production` and add the application's runtime variables through Vercel's Environment Variables settings. Do not add credentials to Git, this file, deployment logs, or chat.

## Required services before the first deployment

- A PostgreSQL database reachable from Vercel over TLS. The AWS Terraform design in `infra/aws` places RDS in private subnets, and Vercel container functions currently cannot use Secure Compute or static egress IPs. Do not make that database publicly reachable to work around the network boundary. Use a managed PostgreSQL service with a TLS endpoint reachable from Vercel, or keep the entire Spring Boot service on AWS.
- Provide `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as production environment variables. The database must support the Flyway migrations used by the app and JDBC-backed HTTP sessions.
- Private donation-proof storage. The app currently uses AWS S3. If retaining S3, provide a dedicated AWS identity with write/read access only to this application's proof prefix and store its credentials as encrypted Vercel environment variables. Never use root or broad administrator credentials. Alternatively, replace the storage adapter with a Vercel-compatible object store before deployment.
- Configure the application’s admin bootstrap email and password through the project's secret settings. Rotate the bootstrap password immediately after first login.

## Deployment boundary

This repository contains the Vercel container configuration, but deployment cannot complete until the Vercel project is connected and the required database and storage endpoints are configured. Container functions scale to zero and have temporary local storage, so durable state must remain in external services. The Vercel container runtime is currently documented as beta; check its plan-specific function limits before production use.

PayMongo configuration remains deferred. The app will continue to show that donation destinations are unavailable until a payment method is intentionally configured.
