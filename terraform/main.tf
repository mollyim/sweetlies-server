provider "aws" {
  region     = var.aws_deploy_region
  access_key = var.aws_access_key
  secret_key = var.aws_secret_key

  skip_metadata_api_check = (var.aws_endpoint != null)

  endpoints {
    dynamodb = var.aws_endpoint
    s3       = var.aws_endpoint
    sts      = var.aws_endpoint
  }
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
