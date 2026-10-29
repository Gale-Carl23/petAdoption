data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

locals {
  azs          = slice(data.aws_availability_zones.available.names, 0, 2)
  public_cidrs = ["10.20.0.0/24", "10.20.1.0/24"]
  app_cidrs    = ["10.20.16.0/24", "10.20.17.0/24"]
  db_cidrs     = ["10.20.32.0/24", "10.20.33.0/24"]
  prefix       = "${var.name}-${var.environment}"
}

resource "aws_vpc" "app" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${local.prefix}-vpc" }
}

resource "aws_internet_gateway" "app" {
  vpc_id = aws_vpc.app.id
  tags   = { Name = "${local.prefix}-internet" }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.app.id
  cidr_block              = local.public_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.prefix}-public-${count.index + 1}" }
}

resource "aws_subnet" "app" {
  count             = 2
  vpc_id            = aws_vpc.app.id
  cidr_block        = local.app_cidrs[count.index]
  availability_zone = local.azs[count.index]
  tags              = { Name = "${local.prefix}-app-${count.index + 1}" }
}

resource "aws_subnet" "database" {
  count             = 2
  vpc_id            = aws_vpc.app.id
  cidr_block        = local.db_cidrs[count.index]
  availability_zone = local.azs[count.index]
  tags              = { Name = "${local.prefix}-database-${count.index + 1}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.app.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.app.id
  }
  tags = { Name = "${local.prefix}-public" }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  count  = var.nat_gateway_count
  domain = "vpc"
  tags   = { Name = "${local.prefix}-nat-${count.index + 1}" }
}

resource "aws_nat_gateway" "app" {
  count         = var.nat_gateway_count
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  depends_on    = [aws_internet_gateway.app]
  tags          = { Name = "${local.prefix}-nat-${count.index + 1}" }
}

resource "aws_route_table" "app" {
  count  = 2
  vpc_id = aws_vpc.app.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.app[min(count.index, var.nat_gateway_count - 1)].id
  }
  tags = { Name = "${local.prefix}-app-${count.index + 1}" }
}

resource "aws_route_table_association" "app" {
  count          = 2
  subnet_id      = aws_subnet.app[count.index].id
  route_table_id = aws_route_table.app[count.index].id
}

resource "aws_route_table" "database" {
  count  = 2
  vpc_id = aws_vpc.app.id
  tags   = { Name = "${local.prefix}-database-${count.index + 1}" }
}

resource "aws_route_table_association" "database" {
  count          = 2
  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database[count.index].id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.app.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = aws_route_table.app[*].id
  tags              = { Name = "${local.prefix}-s3-endpoint" }
}

resource "aws_kms_key" "app" {
  description             = "Encryption key for ${local.prefix} data and logs"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  tags                    = { Name = "${local.prefix}-data-key" }
}

resource "aws_kms_alias" "app" {
  name          = "alias/${local.prefix}"
  target_key_id = aws_kms_key.app.key_id
}

resource "aws_s3_bucket" "proofs" {
  bucket        = "${local.prefix}-proofs-${data.aws_caller_identity.current.account_id}"
  force_destroy = false
  tags          = { Name = "${local.prefix}-donation-proofs" }
}

resource "aws_s3_bucket_public_access_block" "proofs" {
  bucket                  = aws_s3_bucket.proofs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "proofs" {
  bucket = aws_s3_bucket.proofs.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_versioning" "proofs" {
  bucket = aws_s3_bucket.proofs.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "proofs" {
  bucket = aws_s3_bucket.proofs.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.app.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_policy" "proofs_tls_only" {
  bucket = aws_s3_bucket.proofs.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.proofs.arn, "${aws_s3_bucket.proofs.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_ecr_repository" "app" {
  name                 = local.prefix
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.app.arn
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the latest 30 tagged releases"
      selection = {
        tagStatus      = "tagged"
        tagPatternList = ["release-*"]
        countType      = "imageCountMoreThan"
        countNumber    = 30
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_security_group" "load_balancer" {
  name        = "${local.prefix}-alb"
  description = "Public HTTPS entry point"
  vpc_id      = aws_vpc.app.id
  ingress {
    description = "HTTP redirect to HTTPS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.allowed_web_cidrs
  }
  ingress {
    description = "HTTPS application traffic"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.allowed_web_cidrs
  }
  egress {
    from_port       = 0
    to_port         = 0
    protocol        = "-1"
    cidr_blocks     = ["0.0.0.0/0"]
    prefix_list_ids = []
  }
}

resource "aws_security_group" "app" {
  name        = "${local.prefix}-tasks"
  description = "Application tasks accept traffic only from the ALB"
  vpc_id      = aws_vpc.app.id
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.load_balancer.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "proxy" {
  name        = "${local.prefix}-db-proxy"
  description = "Database proxy accepts connections only from app tasks"
  vpc_id      = aws_vpc.app.id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "database" {
  name        = "${local.prefix}-database"
  description = "PostgreSQL accepts connections only from its proxy"
  vpc_id      = aws_vpc.app.id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.proxy.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_subnet_group" "app" {
  name       = "${local.prefix}-database"
  subnet_ids = aws_subnet.database[*].id
}

resource "random_password" "database" {
  length           = 40
  special          = true
  override_special = "!#$%&*()-_=+[]{}:?,."
}

resource "aws_secretsmanager_secret" "database" {
  name                    = "${local.prefix}/database/credentials"
  description             = "Application PostgreSQL credential"
  kms_key_id              = aws_kms_key.app.arn
  recovery_window_in_days = 30
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.database.result
  })
}

resource "random_password" "admin_bootstrap" {
  length           = 40
  special          = true
  override_special = "!#$%&*()-_=+[]{}:?,."
}

resource "aws_secretsmanager_secret" "admin_bootstrap" {
  name                    = "${local.prefix}/admin/bootstrap"
  description             = "Initial production administrator password; change after first login."
  kms_key_id              = aws_kms_key.app.arn
  recovery_window_in_days = 30
}

resource "aws_secretsmanager_secret_version" "admin_bootstrap" {
  secret_id     = aws_secretsmanager_secret.admin_bootstrap.id
  secret_string = jsonencode({ password = random_password.admin_bootstrap.result })
}

resource "aws_db_instance" "app" {
  identifier                            = "${local.prefix}-postgres"
  engine                                = "postgres"
  instance_class                        = var.db_instance_class
  db_name                               = "petdonation"
  username                              = var.db_username
  password                              = random_password.database.result
  port                                  = 5432
  allocated_storage                     = var.db_allocated_storage_gb
  max_allocated_storage                 = var.db_max_storage_gb
  storage_type                          = "gp3"
  storage_encrypted                     = true
  kms_key_id                            = aws_kms_key.app.arn
  db_subnet_group_name                  = aws_db_subnet_group.app.name
  vpc_security_group_ids                = [aws_security_group.database.id]
  publicly_accessible                   = false
  multi_az                              = true
  backup_retention_period               = 14
  auto_minor_version_upgrade            = true
  deletion_protection                   = true
  copy_tags_to_snapshot                 = true
  enabled_cloudwatch_logs_exports       = ["postgresql", "upgrade"]
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  maintenance_window                    = "sun:18:00-sun:19:00"
  backup_window                         = "17:00-17:30"
  skip_final_snapshot                   = false
  final_snapshot_identifier             = "${local.prefix}-postgres-final"
  apply_immediately                     = false
  tags                                  = { Name = "${local.prefix}-postgres" }
}

resource "aws_iam_role" "proxy" {
  name = "${local.prefix}-rds-proxy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "proxy_secret" {
  name = "read-database-secret"
  role = aws_iam_role.proxy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = aws_secretsmanager_secret.database.arn
      }, {
      Effect   = "Allow"
      Action   = ["kms:Decrypt"]
      Resource = aws_kms_key.app.arn
    }]
  })
}

resource "aws_db_proxy" "app" {
  name                   = "${local.prefix}-proxy"
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.proxy.arn
  vpc_subnet_ids         = aws_subnet.app[*].id
  vpc_security_group_ids = [aws_security_group.proxy.id]
  require_tls            = true
  idle_client_timeout    = 1800
  debug_logging          = false
  auth {
    auth_scheme = "SECRETS"
    secret_arn  = aws_secretsmanager_secret.database.arn
    iam_auth    = "DISABLED"
  }
  depends_on = [aws_iam_role_policy.proxy_secret]
}

resource "aws_db_proxy_default_target_group" "app" {
  db_proxy_name = aws_db_proxy.app.name
  connection_pool_config {
    connection_borrow_timeout    = 120
    max_connections_percent      = 80
    max_idle_connections_percent = 50
    session_pinning_filters      = ["EXCLUDE_VARIABLE_SETS"]
  }
}

resource "aws_db_proxy_target" "app" {
  db_instance_identifier = aws_db_instance.app.identifier
  db_proxy_name          = aws_db_proxy.app.name
  target_group_name      = aws_db_proxy_default_target_group.app.name
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.prefix}"
  retention_in_days = 90
  kms_key_id        = aws_kms_key.app.arn
}

resource "aws_iam_role" "task_execution" {
  name = "${local.prefix}-task-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "task_secret" {
  name = "read-application-secret"
  role = aws_iam_role.task_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = concat(
          [
            aws_secretsmanager_secret.database.arn,
            aws_secretsmanager_secret.admin_bootstrap.arn
          ],
          var.payment_details_secret_arn == "" ? [] : [var.payment_details_secret_arn]
        )
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = aws_kms_key.app.arn
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name = "${local.prefix}-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "task_proofs" {
  name = "access-private-donation-proofs"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject"]
      Resource = "${aws_s3_bucket.proofs.arn}/donation-proofs/*"
      }, {
      Effect   = "Allow"
      Action   = ["kms:Decrypt", "kms:GenerateDataKey"]
      Resource = aws_kms_key.app.arn
    }]
  })
}

resource "aws_ecs_cluster" "app" {
  name = local.prefix
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.prefix
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  volume { name = "tmp" }
  container_definitions = jsonencode([{
    name         = "application"
    image        = var.image_uri
    essential    = true
    portMappings = [{ containerPort = 8080, hostPort = 8080, protocol = "tcp" }]
    mountPoints  = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "production" },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_proxy.app.endpoint}:5432/petdonation?sslmode=require" },
      { name = "DB_USERNAME", value = var.db_username },
      { name = "ADMIN_BOOTSTRAP_EMAIL", value = var.admin_email },
      { name = "DB_POOL_MAX_SIZE", value = "10" },
      { name = "DB_POOL_MIN_IDLE", value = "2" },
      { name = "DONATION_PROOF_BUCKET", value = aws_s3_bucket.proofs.bucket },
      { name = "DONATION_PROOF_PREFIX", value = "donation-proofs" },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "PAYMONGO_LIVE_MODE", value = "true" },
      { name = "MANUAL_TRANSFERS_ENABLED", value = "false" }
    ]
    secrets = concat([
      {
        name      = "DB_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.database.arn}:password::"
      },
      {
        name      = "ADMIN_BOOTSTRAP_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.admin_bootstrap.arn}:password::"
      }
      ], var.payment_details_secret_arn == "" ? [] : [
      {
        name      = "GCASH_RECIPIENT_NAME"
        valueFrom = "${var.payment_details_secret_arn}:gcashRecipientName::"
      },
      {
        name      = "GCASH_RECIPIENT_NUMBER"
        valueFrom = "${var.payment_details_secret_arn}:gcashRecipientNumber::"
      },
      {
        name      = "BANK_NAME"
        valueFrom = "${var.payment_details_secret_arn}:bankName::"
      },
      {
        name      = "BANK_ACCOUNT_NAME"
        valueFrom = "${var.payment_details_secret_arn}:bankAccountName::"
      },
      {
        name      = "BANK_ACCOUNT_NUMBER"
        valueFrom = "${var.payment_details_secret_arn}:bankAccountNumber::"
      },
      {
        name      = "QRPH_IMAGE_URL"
        valueFrom = "${var.payment_details_secret_arn}:qrPhImageUrl::"
      },
      {
        name      = "PAYMONGO_SECRET_KEY"
        valueFrom = "${var.payment_details_secret_arn}:paymongoSecretKey::"
      },
      {
        name      = "PAYMONGO_WEBHOOK_SECRET"
        valueFrom = "${var.payment_details_secret_arn}:paymongoWebhookSecret::"
      }
    ])
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O - http://127.0.0.1:8080/readyz >/dev/null || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 90
    }
    stopTimeout = 45
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "application"
      }
    }
    readonlyRootFilesystem = true
    linuxParameters        = { initProcessEnabled = true }
  }])
}

resource "aws_lb" "app" {
  name                       = "${local.prefix}-alb"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.load_balancer.id]
  subnets                    = aws_subnet.public[*].id
  enable_deletion_protection = true
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "app" {
  name                 = "${local.prefix}-web"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.app.id
  deregistration_delay = 30
  health_check {
    enabled             = true
    path                = "/readyz"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_ecs_service" "app" {
  name                               = "${local.prefix}-web"
  cluster                            = aws_ecs_cluster.app.id
  task_definition                    = aws_ecs_task_definition.app.arn
  desired_count                      = var.ecs_min_tasks
  launch_type                        = "FARGATE"
  platform_version                   = "LATEST"
  availability_zone_rebalancing      = "ENABLED"
  enable_execute_command             = false
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 120
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = aws_subnet.app[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "application"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.https, aws_db_proxy_target.app]
  lifecycle { ignore_changes = [desired_count] }
}

resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = var.ecs_max_tasks
  min_capacity       = var.ecs_min_tasks
  resource_id        = "service/${aws_ecs_cluster.app.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_cpu" {
  name               = "${local.prefix}-cpu-target"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace
  target_tracking_scaling_policy_configuration {
    target_value       = 55
    scale_in_cooldown  = 180
    scale_out_cooldown = 60
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageCPUUtilization" }
  }
}

resource "aws_appautoscaling_policy" "ecs_memory" {
  name               = "${local.prefix}-memory-target"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace
  target_tracking_scaling_policy_configuration {
    target_value       = 70
    scale_in_cooldown  = 180
    scale_out_cooldown = 60
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageMemoryUtilization" }
  }
}

resource "aws_wafv2_web_acl" "app" {
  count       = var.enable_waf ? 1 : 0
  name        = "${local.prefix}-web-acl"
  description = "Managed protections plus focused per-IP rate limits for login and application traffic"
  scope       = "REGIONAL"
  default_action {
    allow {}
  }
  rule {
    name     = "RateLimitLoginByIp"
    priority = 0
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 100
        aggregate_key_type = "IP"
        scope_down_statement {
          byte_match_statement {
            search_string         = "/login"
            positional_constraint = "EXACTLY"
            field_to_match {
              uri_path {}
            }
            text_transformation {
              priority = 0
              type     = "NONE"
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.prefix}-login-rate-limit"
      sampled_requests_enabled   = true
    }
  }
  rule {
    name     = "RateLimitByIp"
    priority = 1
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.prefix}-rate-limit"
      sampled_requests_enabled   = true
    }
  }
  rule {
    name     = "AWSManagedCommonRules"
    priority = 2
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
        rule_action_override {
          name = "SizeRestrictions_BODY"
          action_to_use {
            count {}
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.prefix}-common-rules"
      sampled_requests_enabled   = true
    }
  }
  rule {
    name     = "AWSManagedKnownBadInputs"
    priority = 3
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.prefix}-bad-inputs"
      sampled_requests_enabled   = true
    }
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.prefix}-web-acl"
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "app" {
  count        = var.enable_waf ? 1 : 0
  resource_arn = aws_lb.app.arn
  web_acl_arn  = aws_wafv2_web_acl.app[0].arn
}

resource "aws_sns_topic" "alerts" {
  name              = "${local.prefix}-alerts"
  kms_master_key_id = aws_kms_key.app.arn
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  alarm_name          = "${local.prefix}-unhealthy-targets"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
    TargetGroup  = aws_lb_target_group.app.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  alarm_name          = "${local.prefix}-high-cpu"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  dimensions = {
    ClusterName = aws_ecs_cluster.app.name
    ServiceName = aws_ecs_service.app.name
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${local.prefix}-database-high-cpu"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  dimensions          = { DBInstanceIdentifier = aws_db_instance.app.identifier }
}
