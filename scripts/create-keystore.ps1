param(
  [string]$Output = "$PWD\money-manager-release.jks",
  [string]$Alias = "money-manager"
)

keytool -genkeypair -v `
  -keystore $Output `
  -alias $Alias `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000

Write-Host "Created $Output"
Write-Host "BACK UP THIS FILE. Never commit it to Git."
