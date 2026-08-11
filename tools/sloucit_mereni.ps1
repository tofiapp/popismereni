#Requires -Version 5.1
<#
.SYNOPSIS
  Slouci vsechny *_MD1.xlsx ve slozce do Souhrn_mereni.xlsx.

.DESCRIPTION
  Bez instalace, bez VBA, bez Pythonu — staci Windows PowerShell.
  Stejny vzhled jako Android appka (list Mereni, 4 sloupce).

.EXAMPLE
  .\sloucit_mereni.ps1
  .\sloucit_mereni.ps1 -Folder "C:\Users\...\OneDrive\MD1_rozdeleno"
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Folder = ".",

    [string]$Output = "Souhrn_mereni.xlsx"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$STYLE_DATE = 1
$STYLE_STATION = 2
$STYLE_DATA = 3

$CONTENT_TYPES = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>
"@

$RELS_ROOT = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
"@

$RELS_WB = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"@

$WORKBOOK = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Mereni" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>
"@

$STYLES = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="14"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBBDEFB"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFE0B2"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>
"@

function Unescape-Xml([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return "" }
    return $s.Replace("&lt;", "<").Replace("&gt;", ">").Replace("&quot;", '"').Replace("&apos;", "'").Replace("&amp;", "&")
}

function Escape-Xml([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return "" }
    $t = $s.Replace("`r", " ").Replace("`n", " ")
    return [System.Security.SecurityElement]::Escape($t)
}

function New-Row([string]$Role, [string]$A = "", [string]$B = "", [string]$C = "", [string]$D = "") {
    return [pscustomobject]@{ Role = $Role; A = $A; B = $B; C = $C; D = $D }
}

function Test-LooksLikeDate([string]$s) {
    return $s -match '^\d{1,2}[./]\d{1,2}[./]\d{4}$'
}

function Get-DateFromFileName([string]$name) {
    if ($name -notmatch '^(\d{6})(?:_\d+)?_MD1\.xlsx$') { return "" }
    $yymmdd = $Matches[1]
    $yy = [int]$yymmdd.Substring(0, 2)
    $mm = [int]$yymmdd.Substring(2, 2)
    $dd = [int]$yymmdd.Substring(4, 2)
    if ($mm -lt 1 -or $mm -gt 12 -or $dd -lt 1 -or $dd -gt 31) { return "" }
    return ("{0}.{1}.{2}" -f $dd, $mm, (2000 + $yy))
}

function Get-SharedStrings([System.IO.Compression.ZipArchive]$zip) {
    $list = New-Object System.Collections.Generic.List[string]
    $entry = $zip.GetEntry("xl/sharedStrings.xml")
    if (-not $entry) { return ,$list.ToArray() }
    $sr = New-Object System.IO.StreamReader($entry.Open())
    try { $xml = $sr.ReadToEnd() } finally { $sr.Dispose() }
    $siMatches = [regex]::Matches($xml, '<si\b[^>]*>(.*?)</si>', 'IgnoreCase, Singleline')
    foreach ($sm in $siMatches) {
        $parts = [regex]::Matches($sm.Groups[1].Value, '<t[^>]*>(.*?)</t>', 'IgnoreCase, Singleline')
        $text = ""
        foreach ($p in $parts) { $text += (Unescape-Xml $p.Groups[1].Value) }
        [void]$list.Add($text.Trim())
    }
    return ,$list.ToArray()
}

function Get-CellText([string]$attrs, [string]$body, [string[]]$shared) {
    $ctype = ""
    $tm = [regex]::Match($attrs, 't="([^"]+)"', 'IgnoreCase')
    if ($tm.Success) { $ctype = $tm.Groups[1].Value.ToLowerInvariant() }

    if ($ctype -eq "s") {
        $vm = [regex]::Match($body, '<v[^>]*>(.*?)</v>', 'IgnoreCase, Singleline')
        if (-not $vm.Success) { return "" }
        $idx = 0
        if (-not [int]::TryParse($vm.Groups[1].Value.Trim(), [ref]$idx)) { return "" }
        if ($idx -lt 0 -or $idx -ge $shared.Count) { return "" }
        return $shared[$idx]
    }

    $tParts = [regex]::Matches($body, '<t[^>]*>(.*?)</t>', 'IgnoreCase, Singleline')
    if ($tParts.Count -gt 0) {
        $text = ""
        foreach ($p in $tParts) { $text += (Unescape-Xml $p.Groups[1].Value) }
        return $text.Trim()
    }

    $vm2 = [regex]::Match($body, '<v[^>]*>(.*?)</v>', 'IgnoreCase, Singleline')
    if ($vm2.Success) { return (Unescape-Xml $vm2.Groups[1].Value).Trim() }
    return ""
}

function Read-XlsxRows([string]$path) {
    $rows = New-Object System.Collections.Generic.List[object]
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        $shared = @(Get-SharedStrings $zip)
        $entry = $zip.GetEntry("xl/worksheets/sheet1.xml")
        if (-not $entry) {
            $entry = $zip.Entries | Where-Object { $_.FullName -like "xl/worksheets/sheet*.xml" } |
                Sort-Object FullName | Select-Object -First 1
        }
        if (-not $entry) { return @() }
        $sr = New-Object System.IO.StreamReader($entry.Open())
        try { $xml = $sr.ReadToEnd() } finally { $sr.Dispose() }
    }
    finally { $zip.Dispose() }

    $rowMatches = [regex]::Matches($xml, '<row\b[^>]*>(.*?)</row>', 'IgnoreCase, Singleline')
    foreach ($rm in $rowMatches) {
        $cells = @{}
        $styleA = $null
        $cellMatches = [regex]::Matches($rm.Groups[1].Value, '<c\b([^>]*?)(?:/>|>(.*?)</c>)', 'IgnoreCase, Singleline')
        foreach ($cm in $cellMatches) {
            $attrs = $cm.Groups[1].Value
            $body = $cm.Groups[2].Value
            if ($null -eq $body) { $body = "" }
            $refM = [regex]::Match($attrs, 'r="([A-Z]+)\d+"', 'IgnoreCase')
            if (-not $refM.Success) { continue }
            $col = $refM.Groups[1].Value.ToUpperInvariant()
            $cells[$col] = Get-CellText $attrs $body $shared
            if ($col -eq "A") {
                $sm = [regex]::Match($attrs, 's="(\d+)"')
                if ($sm.Success) { $styleA = [int]$sm.Groups[1].Value }
            }
        }
        $a = $(if ($cells.ContainsKey("A")) { $cells["A"] } else { "" })
        $b = $(if ($cells.ContainsKey("B")) { $cells["B"] } else { "" })
        $c = $(if ($cells.ContainsKey("C")) { $cells["C"] } else { "" })
        $d = $(if ($cells.ContainsKey("D")) { $cells["D"] } else { "" })

        if (-not ($a -or $b -or $c -or $d)) {
            $role = "BLANK"
        }
        elseif ($null -ne $styleA) {
            if ($styleA -eq $STYLE_DATE) { $role = "DATE" }
            elseif ($styleA -eq $STYLE_STATION) { $role = "STATION" }
            elseif ($a -and -not ($b -or $c -or $d)) {
                if ($rows.Count -eq 0 -or (Test-LooksLikeDate $a)) { $role = "DATE" }
                else { $role = "STATION" }
            }
            else { $role = "DATA" }
        }
        elseif ($a -and -not ($b -or $c -or $d)) {
            if ($rows.Count -eq 0 -or (Test-LooksLikeDate $a)) { $role = "DATE" }
            else { $role = "STATION" }
        }
        else { $role = "DATA" }

        [void]$rows.Add((New-Row $role $a $b $c $d))
    }
    return ,$rows.ToArray()
}

function Get-CellXml([string]$ref, [string]$value, [int]$style) {
    if ([string]::IsNullOrEmpty($value)) {
        return ('<c r="{0}" s="{1}"/>' -f $ref, $style)
    }
    return ('<c r="{0}" s="{1}" t="inlineStr"><is><t>{2}</t></is></c>' -f $ref, $style, (Escape-Xml $value))
}

function Get-SheetXml($rows) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>')
    [void]$sb.Append('<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">')
    [void]$sb.Append('<cols>')
    [void]$sb.Append('<col min="1" max="1" width="32" customWidth="1"/>')
    [void]$sb.Append('<col min="2" max="2" width="18" customWidth="1"/>')
    [void]$sb.Append('<col min="3" max="3" width="14" customWidth="1"/>')
    [void]$sb.Append('<col min="4" max="4" width="36" customWidth="1"/>')
    [void]$sb.Append('</cols><sheetData>')
    $r = 0
    foreach ($row in $rows) {
        $r++
        $ht = $(if ($row.Role -eq "BLANK") { "12" } else { "24" })
        [void]$sb.Append(('<row r="{0}" ht="{1}" customHeight="1">' -f $r, $ht))
        switch ($row.Role) {
            "BLANK" { }
            "DATE" { [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_DATE)) }
            "STATION" { [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_STATION)) }
            default {
                [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_DATA))
                [void]$sb.Append((Get-CellXml ("B$r") $row.B $STYLE_DATA))
                [void]$sb.Append((Get-CellXml ("C$r") $row.C $STYLE_DATA))
                [void]$sb.Append((Get-CellXml ("D$r") $row.D $STYLE_DATA))
            }
        }
        [void]$sb.Append('</row>')
    }
    [void]$sb.Append('</sheetData></worksheet>')
    return $sb.ToString()
}

function Write-Xlsx([string]$path, $rows) {
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    $zip = [System.IO.Compression.ZipFile]::Open($path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        function Add-Entry([string]$name, [string]$body) {
            $e = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
            $sw = New-Object System.IO.StreamWriter($e.Open())
            try { $sw.Write($body) } finally { $sw.Dispose() }
        }
        Add-Entry "[Content_Types].xml" $CONTENT_TYPES
        Add-Entry "_rels/.rels" $RELS_ROOT
        Add-Entry "xl/workbook.xml" $WORKBOOK
        Add-Entry "xl/_rels/workbook.xml.rels" $RELS_WB
        Add-Entry "xl/styles.xml" $STYLES
        Add-Entry "xl/worksheets/sheet1.xml" (Get-SheetXml $rows)
    }
    finally { $zip.Dispose() }
}

function Get-Md1Files([string]$dir) {
    Get-ChildItem -LiteralPath $dir -File -Filter "*_MD1.xlsx" |
        Where-Object {
            $_.Name -notmatch '(?i)^Souhrn_mereni\.xlsx$' -and
            $_.Name -notlike '~$*'
        } |
        Sort-Object { $_.Name.ToLowerInvariant() }
}

# ---- main ----
$folderPath = (Resolve-Path -LiteralPath $Folder).Path
if (-not (Test-Path -LiteralPath $folderPath -PathType Container)) {
    Write-Error "Slozka neexistuje: $folderPath"
    exit 2
}

$files = @(Get-Md1Files $folderPath)
Write-Host ("Slozka: {0}" -f $folderPath)
Write-Host ("Nalezeno souboru *_MD1.xlsx: {0}" -f $files.Count)
if ($files.Count -eq 0) {
    Write-Host "Ve slozce nejsou zadne *_MD1.xlsx:"
    Write-Host "  $folderPath"
    Write-Host "Soubory .xlsx ve slozce:"
    Get-ChildItem -LiteralPath $folderPath -File -Filter "*.xlsx" | ForEach-Object { Write-Host ("  - {0}" -f $_.Name) }
    exit 1
}

$out = New-Object System.Collections.Generic.List[object]
$currentDate = ""
$lastStation = ""
$ok = 0
$skipped = 0

foreach ($f in $files) {
    $srcRows = @(Read-XlsxRows $f.FullName)
    $dataN = @($srcRows | Where-Object { $_.Role -eq "DATA" -and ($_.A -or $_.B -or $_.C -or $_.D) }).Count
    Write-Host ("  - {0}: {1} radku XML, z toho {2} datovych" -f $f.Name, $srcRows.Count, $dataN)
    $wrote = $false
    $guess = Get-DateFromFileName $f.Name

    foreach ($row in $srcRows) {
        if ($row.Role -eq "BLANK" -or (-not ($row.A -or $row.B -or $row.C -or $row.D))) { continue }

        if ($row.Role -eq "DATE") {
            if ($row.A -ne $currentDate) {
                if ($out.Count -gt 0) { [void]$out.Add((New-Row "BLANK")) }
                [void]$out.Add((New-Row "DATE" $row.A))
                $currentDate = $row.A
                $lastStation = ""
                $wrote = $true
            }
            continue
        }

        if ($row.Role -eq "STATION") {
            if (-not $currentDate -and $guess) {
                if ($out.Count -gt 0) { [void]$out.Add((New-Row "BLANK")) }
                [void]$out.Add((New-Row "DATE" $guess))
                $currentDate = $guess
                $lastStation = ""
            }
            if ($row.A -ne $lastStation) {
                [void]$out.Add((New-Row "BLANK"))
                [void]$out.Add((New-Row "STATION" $row.A))
                $lastStation = $row.A
                $wrote = $true
            }
            continue
        }

        if (-not $currentDate -and $guess) {
            if ($out.Count -gt 0) { [void]$out.Add((New-Row "BLANK")) }
            [void]$out.Add((New-Row "DATE" $guess))
            $currentDate = $guess
            $lastStation = ""
        }
        [void]$out.Add((New-Row "DATA" $row.A $row.B $row.C $row.D))
        $wrote = $true
    }

    if ($wrote) { $ok++ } else { $skipped++ }
}

$dataOut = @($out | Where-Object { $_.Role -eq "DATA" }).Count
if ($dataOut -eq 0) {
    Write-Host ""
    Write-Host "CHYBA: do souhrnu se nedostala zadna data."
    Write-Host "Zkontroluj, ze ve slozce jsou originalni soubory z appky (*_MD1.xlsx)."
    exit 1
}

$outPath = Join-Path $folderPath $Output
Write-Xlsx $outPath $out.ToArray()

Write-Host ("Soubory OK: {0}" -f $ok)
Write-Host ("Preskoceno: {0}" -f $skipped)
Write-Host ("Datovych radku: {0}" -f $dataOut)
Write-Host ("Ulozeno: {0}" -f $outPath)
exit 0
