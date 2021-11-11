output "attachment_controller_access_key" {
  value = aws_iam_access_key.attachment_controller.id
}

output "attachment_controller_secret_key" {
  value     = aws_iam_access_key.attachment_controller.secret
  sensitive = true
}
