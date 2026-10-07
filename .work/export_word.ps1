$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskInput = Join-Path $taskRoot '랜드마크_간판_한국어앱_PRD_SRS.docx'
$taskOutput = Join-Path $PSScriptRoot 'render\requirements.pdf'
New-Item -ItemType Directory -Path (Split-Path -Parent $taskOutput) -Force | Out-Null
$taskWord = $null
$taskDocument = $null
try {
    $taskWord = New-Object -ComObject Word.Application
    $taskWord.Visible = $false
    $taskWord.DisplayAlerts = 0
    $taskDocument = $taskWord.Documents.Open($taskInput, $false, $true)
    $taskDocument.ExportAsFixedFormat($taskOutput, 17)
    Write-Output 'Exported document to internal QA PDF.'
}
finally {
    if ($null -ne $taskDocument) {
        $taskDocument.Close(0)
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskDocument)
    }
    if ($null -ne $taskWord) {
        if ($taskWord.Documents.Count -eq 0) { $taskWord.Quit(0) }
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskWord)
    }
}
