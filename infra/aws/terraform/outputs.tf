output "load_balancer_dns_name" {
  value       = aws_lb.app.dns_name
  description = "ALB endpoint; point your DNS hostname here after deployment."
}

output "ecr_repository_url" {
  value       = aws_ecr_repository.app.repository_url
  description = "Immutable container image repository."
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.app.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "proof_bucket_name" {
  value = aws_s3_bucket.proofs.bucket
}

output "database_proxy_endpoint" {
  value       = aws_db_proxy.app.endpoint
  description = "Private RDS Proxy endpoint; never publish this address."
}

output "alerts_topic_arn" {
  value       = aws_sns_topic.alerts.arn
  description = "SNS topic for alarm notifications."
}
