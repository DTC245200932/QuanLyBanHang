param([int]$Port = 5500)

$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'serve_frontend.py') --port $Port
exit $LASTEXITCODE
