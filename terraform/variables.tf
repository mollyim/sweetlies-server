variable "app" {
  type    = string
  default = "sweetlies"
}

variable "environment" {
  type = string
}

variable "aws_deploy_region" {
  type = string
}

variable "aws_endpoint" {
  type    = string
  default = null
}

variable "aws_access_key" {
  type    = string
  default = null
}

variable "aws_secret_key" {
  type    = string
  default = null
}

variable "gcp_region" {
  type = string
}

variable "gcp_zone_a" {
  type = string
}
