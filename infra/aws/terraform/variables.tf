variable "name" {
  description = "Short lowercase name used to tag resources."
  type        = string
  default     = "pawprints"
}

variable "environment" {
  description = "Deployment environment."
  type        = string
  default     = "production"
}

variable "aws_region" {
  description = "AWS region for the workload."
  type        = string
  default     = "ap-southeast-1"
}

variable "certificate_arn" {
  description = "ACM certificate ARN for the production HTTPS hostname."
  type        = string
}

variable "image_uri" {
  description = "Immutable ECR image URI, preferably pinned by digest."
  type        = string
}

variable "db_username" {
  description = "Application database user."
  type        = string
  default     = "petdonation_app"
}

variable "admin_email" {
  description = "Email address for the initial production administrator."
  type        = string
}

variable "payment_details_secret_arn" {
  description = "Optional Secrets Manager ARN containing public donation destinations and QR image URL."
  type        = string
  default     = ""
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance size; adjust after load testing."
  type        = string
  default     = "db.t4g.medium"
}

variable "db_allocated_storage_gb" {
  description = "Initial encrypted gp3 storage size."
  type        = number
  default     = 100
}

variable "db_max_storage_gb" {
  description = "Maximum autoscaled RDS storage size."
  type        = number
  default     = 500
}

variable "ecs_min_tasks" {
  type        = number
  description = "Minimum application tasks across availability zones."
  default     = 2
}

variable "ecs_max_tasks" {
  type        = number
  description = "Maximum application tasks permitted by autoscaling."
  default     = 8
}

variable "ecs_cpu" {
  type        = number
  description = "Fargate CPU units per task."
  default     = 1024
}

variable "ecs_memory" {
  type        = number
  description = "Fargate memory in MiB per task."
  default     = 2048
}

variable "alarm_email" {
  type        = string
  description = "Optional email subscription for infrastructure alarms."
  default     = ""
}

variable "nat_gateway_count" {
  description = "Use one NAT gateway per AZ for resilient private-subnet egress."
  type        = number
  default     = 2
  validation {
    condition     = var.nat_gateway_count >= 1 && var.nat_gateway_count <= 2
    error_message = "nat_gateway_count must be 1 or 2."
  }
}

variable "allowed_web_cidrs" {
  description = "IPv4 clients allowed to reach the public HTTPS load balancer."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "enable_waf" {
  description = "Attach AWS managed common and known-bad-input rules to the ALB."
  type        = bool
  default     = true
}
