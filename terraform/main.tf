locals {
  aws_endpoint_override = (var.aws_endpoint != null)
}

provider "aws" {
  region     = var.aws_deploy_region
  access_key = var.aws_access_key
  secret_key = var.aws_secret_key

  skip_metadata_api_check = local.aws_endpoint_override
  s3_force_path_style     = local.aws_endpoint_override

  endpoints {
    dynamodb = var.aws_endpoint
    iam      = var.aws_endpoint
    s3       = var.aws_endpoint
    sts      = var.aws_endpoint
  }
}

provider "google" {
  project = "${var.app}-${var.environment}"
  region  = var.gcp_region
}

resource "aws_dynamodb_table" "messages" {
  name         = "Messages"
  hash_key     = "H"
  range_key    = "S"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_PARTITION
    name = "H"
    type = "B"
  }

  attribute { # KEY_SORT
    name = "S"
    type = "B"
  }

  attribute { # LOCAL_INDEX_MESSAGE_UUID_KEY_SORT
    name = "U"
    type = "B"
  }

  local_secondary_index {
    name            = "Message_UUID_Index"
    projection_type = "KEYS_ONLY"
    range_key       = "U"
  }

  tags = {}
}

resource "aws_dynamodb_table" "keys" {
  name         = "Keys"
  hash_key     = "U"
  range_key    = "DK"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_UUID
    name = "U"
    type = "B"
  }

  attribute { # KEY_DEVICE_ID_KEY_ID
    name = "DK"
    type = "B"
  }

  tags = {}
}

resource "aws_dynamodb_table" "accounts" {
  name         = "Accounts"
  hash_key     = "U"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_UUID
    name = "U"
    type = "B"
  }

  tags = {}
}

resource "aws_dynamodb_table" "phone_numbers" {
  name         = "PhoneNumbers"
  hash_key     = "P"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # ATTR_ACCOUNT_E164
    name = "P"
    type = "S"
  }

  tags = {}
}

resource "aws_dynamodb_table" "deleted_accounts" {
  name         = "DeleteAccounts"
  hash_key     = "P"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_E164
    name = "P"
    type = "S"
  }

  // TODO: Set TTL

  tags = {}
}

resource "aws_dynamodb_table" "deleted_accounts_lock" {
  name         = "DeleteAccountsLock"
  hash_key     = "P"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_E164
    name = "P"
    type = "S"
  }

  tags = {}
}

resource "aws_dynamodb_table" "pending_accounts" {
  name         = "PendingAccounts"
  hash_key     = "P"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_E164
    name = "P"
    type = "S"
  }

  ttl { # ATTR_TTL
    enabled        = true
    attribute_name = "E"
  }

  tags = {}
}

resource "aws_dynamodb_table" "pending_devices" {
  name         = "PendingDevices"
  hash_key     = "P"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_E164
    name = "P"
    type = "S"
  }

  ttl { # ATTR_TTL
    enabled        = true
    attribute_name = "E"
  }

  tags = {}
}

resource "aws_dynamodb_table" "push_challenges" {
  name         = "PushChallenges"
  hash_key     = "U"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_ACCOUNT_UUID
    name = "U"
    type = "B"
  }

  ttl { # ATTR_TTL
    enabled        = true
    attribute_name = "T"
  }

  tags = {}
}

resource "aws_dynamodb_table" "report_messages" {
  name         = "ReportMessages"
  hash_key     = "H"
  billing_mode = "PAY_PER_REQUEST"

  attribute { # KEY_HASH
    name = "H"
    type = "B"
  }

  ttl { # ATTR_TTL
    enabled        = true
    attribute_name = "E"
  }

  tags = {}
}

resource "google_bigtable_instance" "storage" {
  count = 0

  name = "storage"

  cluster {
    cluster_id   = "storage-cluster"
    zone         = var.gcp_zone_a
    num_nodes    = 1
  }
}

resource "google_bigtable_table" "contacts" {
  name          = "contacts"
  instance_name = "storage"

  column_family { # StorageItemsTable.FAMILY
    family = "c"
  }
}

resource "google_bigtable_table" "contact_manifests" {
  name          = "manifest"
  instance_name = "storage"

  column_family { # StorageManifestsTable.FAMILY
    family = "m"
  }
}

resource "google_bigtable_table" "groups" {
  name          = "groups"
  instance_name = "storage"

  column_family { # GroupsTable.FAMILY
    family = "g"
  }
}

resource "google_bigtable_table" "group_logs" {
  name          = "group-logs"
  instance_name = "storage"

  column_family { # GroupLogTable.FAMILY
    family = "l"
  }
}

resource "aws_iam_user" "attachment_controller" {
  name = "attachment-controller"
}

resource "aws_iam_access_key" "attachment_controller" {
  user = aws_iam_user.attachment_controller.name
}

resource "aws_s3_bucket" "attachments" {
  bucket = "attachments"

  versioning {
    enabled    = false
    mfa_delete = false
  }
}

resource "aws_s3_bucket_policy" "attachments_policy" {
  bucket = aws_s3_bucket.attachments.id
  policy = jsonencode({
    Version = "2012-10-17"
    Id = "attachments-policy"
    Statement = [
      {
        Effect = "Allow"
        Principal = "${aws_iam_user.attachment_controller.name}"
        Action = [
          "s3:GetObject",
          "s3:PutObject"
        ]
        Resource = [
          "${aws_s3_bucket.attachments.arn}/*"
        ]
      },
    ]
  })
}
