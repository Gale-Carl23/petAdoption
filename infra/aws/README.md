# AWS deployment foundation

This stack describes an AWS production baseline for the Spring Boot web app and the JavaFX admin client. It provisions infrastructure only; it does not create resources until you run Terraform with your AWS account credentials.

## Architecture

- Public Application Load Balancer in two availability zones. HTTP redirects to HTTPS, and AWS WAF applies managed common-input rules, a focused login rate limit, and an application-wide IP rate limit.
- Spring Boot runs as non-root Fargate tasks in private subnets. ECS keeps at least two tasks, scales on CPU and memory, and rolls back failed deployments.
- Amazon RDS for PostgreSQL runs privately with Multi-AZ failover, encrypted gp3 storage, 14-day point-in-time recovery, Performance Insights, and an RDS Proxy connection pool.
- Donation receipts live in a versioned, KMS-encrypted S3 bucket with public access blocked. The task role can read/write only the donation-proof prefix.
- Donation destinations are injected from Secrets Manager at runtime; the repository and Terraform variables contain no recipient account number. GCash and bank transfers remain pending until an administrator checks the submitted reference and proof.
- Secrets Manager holds the generated database credential. CloudWatch retains application logs and sends infrastructure alarms to an optional SNS email subscription.
- Flyway creates and versions the database schema. Hibernate validates mappings rather than changing production tables at startup.
- JDBC-backed Spring sessions are shared through PostgreSQL so authentication survives requests routed to different Fargate tasks.

## Before deployment

You need an AWS account, AWS CLI credentials through IAM Identity Center or an assumed role, Docker, Terraform 1.10+, a DNS name, and an ACM certificate for that name in the selected region. Do not use root-account keys. The default region is `ap-southeast-1`.

Create a dedicated Terraform state bucket first. Enable versioning, encryption, and all four S3 Block Public Access settings. Terraform state contains the generated database secret and must be treated as sensitive. The S3 backend uses native state locking.

```powershell
aws sso login --profile pet-haven
$env:AWS_PROFILE = 'pet-haven'
aws sts get-caller-identity
```

From `infra/aws/terraform`, initialize the encrypted remote state backend. Replace the bucket name with the dedicated state bucket you created:

```powershell
terraform init `
  -backend-config="bucket=your-private-terraform-state-bucket" `
  -backend-config="key=pawprints/production/terraform.tfstate" `
  -backend-config="region=ap-southeast-1" `
  -backend-config="encrypt=true" `
  -backend-config="use_lockfile=true"
```

## Build and deploy

### Configure donation destinations

Create a Secrets Manager JSON secret outside the repository. Encrypt it with the application key alias (`alias/pawprints-production` for the default resource name), and include all keys even when a method is unused:

```json
{
  "gcashRecipientName": "RECIPIENT NAME",
  "gcashRecipientNumber": "GCASH NUMBER",
  "bankName": "",
  "bankAccountName": "",
  "bankAccountNumber": "",
  "qrPhImageUrl": ""
}
```

Set `payment_details_secret_arn` in the untracked `terraform.tfvars` to this secret's ARN. The app displays only complete methods. For QR payments, use the official QR Ph merchant-presented code issued by the merchant's bank or payment service provider; QR Ph is the BSP national standard aligned with EMVCo. Do not generate or alter a payment QR from an account number. The QR image URL may be an HTTPS image URL or a site path such as `/images/qrph-merchant.png`.

The current donation workflow records transfer details and proof for manual review. It does not initiate or automatically settle payments. Never ask donors for wallet PINs, passwords, or one-time codes.

The ECR repository must exist before the image can be pushed. First create only that repository with Terraform, supplying temporary valid-shaped values for required full-stack variables; this targeted bootstrap does not create the database or load balancer:

```powershell
terraform apply -target=aws_ecr_repository.app `
  -var="certificate_arn=arn:aws:acm:ap-southeast-1:111122223333:certificate/bootstrap" `
  -var="image_uri=public.ecr.aws/docker/library/alpine:latest" `
  -var="admin_email=admin@example.org"
```

Log in to the ECR URL shown by `terraform output ecr_repository_url`, build from the `java-pet-donation` directory, tag the image with a unique release name, and push it. Use the pushed image digest for `image_uri`; immutable digests make rollbacks reproducible.

Create an untracked `terraform.tfvars` using `terraform.tfvars.example`, replacing the certificate ARN and image URI. Restrict `allowed_web_cidrs` if the site should only be reachable from selected networks. Run `terraform plan -out=production.tfplan`, inspect the plan and estimated costs, then apply that saved plan. Never commit tfvars, plans, local Terraform state, or AWS credentials.

After apply, add a DNS alias for the load balancer output and confirm the SNS email subscription. Terraform creates a random initial administrator password in Secrets Manager; retrieve it from the `admin_bootstrap_secret_arn` output and sign in at `/login` with the configured `admin_email`. The production app creates that account once, and a matching existing admin account is left unchanged. Keep the credential in an approved secret manager. Do not use the local demo login in production. Establish an audited password change and administrator lifecycle before go-live. New blank databases apply `V1__create_pet_adoption_schema.sql` automatically. Existing databases need a reviewed backup and one-time Flyway baseline/migration plan before this application version is deployed.

Set `PET_HAVEN_API_BASE_URL` on each JavaFX admin workstation to the HTTPS site hostname (for example, `https://pets.example.org`). The desktop client keeps its own session cookie and sends review, pet-management, and proof requests to the same protected Spring APIs.

## Operational limits

This is a starting production topology, not a capacity guarantee. The defaults intentionally span two AZs and may incur costs for Fargate, NAT gateways, the ALB, RDS Multi-AZ, RDS Proxy, WAF, KMS, and CloudWatch. Run load tests with representative traffic, tune task/database sizes and pool bounds, define service-level objectives, and rehearse database restore and application rollback before launch. The Terraform state bucket, certificate, DNS zone, AWS account guardrails, and administrator identity lifecycle remain account-specific prerequisites.
