param(
    [string]$Service = ""
)

$ValidServices = @("patient-service", "gateway-service")
$UsageMsg = "Usage: .\run-dev.ps1 <service>`nServices disponibles : $($ValidServices -join ', ')"

if (-not $Service) {
    Write-Host $UsageMsg
    exit 1
}

if ($ValidServices -notcontains $Service) {
    Write-Host "Erreur : service inconnu '$Service'"
    Write-Host $UsageMsg
    exit 1
}

$EnvFile = "$Service\.env"
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

Set-Location $Service
mvn spring-boot:run