#Requires -Version 5.1
# ASCII-only source (Windows PowerShell 5.1). Czech names via [char] codes.
# Layout:
#   Popis_mereni_MD1/
#     Popis_mereni_MD1.xlsx
#     MD1_popis_dny/YYMMDD_N_MD1.xlsx
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Folder = ".",

    [string]$Output = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Popis_mereni_MD1 with diacritics: e caron, r caron, i acute
$eCaron = [char]0x011B
$rCaron = [char]0x0159
$iAcute = [char]0x00ED
$MAIN_FOLDER_NAME = "Popis_m" + $eCaron + $rCaron + "en" + $iAcute + "_MD1"
$DAYS_SUBFOLDER_NAME = "MD1_popis_dny"
$SUMMARY_XLSX_NAME = $MAIN_FOLDER_NAME + ".xlsx"
$MAIN_FOLDER_ASCII = "Popis_mereni_MD1"
$SUMMARY_ASCII = "Popis_mereni_MD1.xlsx"

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

function Get-ZipEntryCI([System.IO.Compression.ZipArchive]$zip, [string]$name) {
    $want = $name.Replace('\', '/').ToLowerInvariant()
    foreach ($e in $zip.Entries) {
        if ($e.FullName.Replace('\', '/').ToLowerInvariant() -eq $want) { return $e }
    }
    return $null
}

function Read-ZipEntryText([System.IO.Compression.ZipArchiveEntry]$entry) {
    if (-not $entry) { return "" }
    $sr = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8, $true)
    try { return $sr.ReadToEnd() } finally { $sr.Dispose() }
}

function Get-SharedStrings([System.IO.Compression.ZipArchive]$zip) {
    $list = New-Object System.Collections.Generic.List[string]
    $entry = Get-ZipEntryCI $zip "xl/sharedStrings.xml"
    if (-not $entry) {
        [string[]]$empty = @()
        return $empty
    }
    $xml = Read-ZipEntryText $entry
    # strip ns prefixes: <x:si> -> <si>
    $xml = [regex]::Replace($xml, '<([A-Za-z0-9._-]+):', '<')
    $xml = [regex]::Replace($xml, '</([A-Za-z0-9._-]+):', '</')
    $siMatches = [regex]::Matches($xml, '<si\b[^>]*>(.*?)</si>', 'IgnoreCase, Singleline')
    foreach ($sm in $siMatches) {
        $parts = [regex]::Matches($sm.Groups[1].Value, '<t[^>]*>(.*?)</t>', 'IgnoreCase, Singleline')
        $text = ""
        foreach ($p in $parts) { $text += (Unescape-Xml $p.Groups[1].Value) }
        [void]$list.Add($text.Trim())
    }
    [string[]]$arr = $list.ToArray()
    return $arr
}

function Get-CellText([string]$attrs, [string]$body, [string[]]$shared) {
    if ($null -eq $body) { $body = "" }
    $ctype = ""
    $tm = [regex]::Match($attrs, 't\s*=\s*"([^"]+)"', 'IgnoreCase')
    if ($tm.Success) { $ctype = $tm.Groups[1].Value.ToLowerInvariant() }

    if ($ctype -eq "s") {
        $vm = [regex]::Match($body, '<v[^>]*>(.*?)</v>', 'IgnoreCase, Singleline')
        if (-not $vm.Success) { return "" }
        $idx = 0
        if (-not [int]::TryParse($vm.Groups[1].Value.Trim(), [ref]$idx)) { return "" }
        if ($null -eq $shared) { return "" }
        if ($idx -lt 0 -or $idx -ge $shared.Length) { return "" }
        return [string]$shared[$idx]
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

    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Host ("  ! Soubor neexistuje: {0}" -f $path)
        return [object[]]@()
    }
    $len = (Get-Item -LiteralPath $path).Length
    if ($len -lt 64) {
        Write-Host ("  ! Soubor je moc maly ({0} B) — OneDrive asi nestahl obsah." -f $len)
        return [object[]]@()
    }
    $fs = [System.IO.File]::OpenRead($path)
    try {
        $b0 = $fs.ReadByte(); $b1 = $fs.ReadByte()
    } finally { $fs.Dispose() }
    if ($b0 -ne 0x50 -or $b1 -ne 0x4B) {
        Write-Host ("  ! Soubor neni platne xlsx/zip (chybi PK). OneDrive online-only?" -f $path)
        return [object[]]@()
    }

    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    } catch {
        Write-Host ("  ! Nelze otevrit zip: {0}" -f $_.Exception.Message)
        return [object[]]@()
    }

    $xml = ""
    $shared = [string[]]@()
    try {
        $shared = Get-SharedStrings $zip
        if ($null -eq $shared) { $shared = [string[]]@() }

        $entry = Get-ZipEntryCI $zip "xl/worksheets/sheet1.xml"
        if (-not $entry) {
            $entry = $zip.Entries |
                Where-Object { $_.FullName.Replace('\','/').ToLowerInvariant() -like 'xl/worksheets/sheet*.xml' } |
                Sort-Object FullName |
                Select-Object -First 1
        }
        if (-not $entry) {
            Write-Host "  ! V xlsx chybi sheet XML"
            return [object[]]@()
        }
        $xml = Read-ZipEntryText $entry
    }
    finally {
        if ($zip) { $zip.Dispose() }
    }

    # strip ns prefixes for easier regex
    $xml = [regex]::Replace($xml, '<([A-Za-z0-9._-]+):', '<')
    $xml = [regex]::Replace($xml, '</([A-Za-z0-9._-]+):', '</')

    $tCount = [regex]::Matches($xml, '<t\b').Count
    $vCount = [regex]::Matches($xml, '<v\b').Count
    Write-Host ("    (sharedStrings={0}, tagu <t>={1}, <v>={2}, size={3} B)" -f $shared.Length, $tCount, $vCount, $len)

    $rowMatches = [regex]::Matches($xml, '<row\b[^>]*>(.*?)</row>', 'IgnoreCase, Singleline')
    # also self-closing empty rows ignored — OK
    foreach ($rm in $rowMatches) {
        $cells = @{}
        $styleA = $null
        $cellMatches = [regex]::Matches($rm.Groups[1].Value, '<c\b([^>]*?)(?:/>|>(.*?)</c>)', 'IgnoreCase, Singleline')
        foreach ($cm in $cellMatches) {
            $attrs = $cm.Groups[1].Value
            $body = ""
            if ($cm.Groups.Count -gt 2 -and $cm.Groups[2].Success) { $body = $cm.Groups[2].Value }
            $refM = [regex]::Match($attrs, 'r\s*=\s*"([A-Z]+)\d+"', 'IgnoreCase')
            if (-not $refM.Success) { continue }
            $col = $refM.Groups[1].Value.ToUpperInvariant()
            $cells[$col] = Get-CellText $attrs $body $shared
            if ($col -eq "A") {
                $sm = [regex]::Match($attrs, 's\s*=\s*"(\d+)"')
                if ($sm.Success) { $styleA = [int]$sm.Groups[1].Value }
            }
        }
        $a = $(if ($cells.ContainsKey("A")) { [string]$cells["A"] } else { "" })
        $b = $(if ($cells.ContainsKey("B")) { [string]$cells["B"] } else { "" })
        $c = $(if ($cells.ContainsKey("C")) { [string]$cells["C"] } else { "" })
        $d = $(if ($cells.ContainsKey("D")) { [string]$cells["D"] } else { "" })

        if (-not ($a -or $b -or $c -or $d)) {
            $role = "BLANK"
        }
        elseif ($b -or $c -or $d) {
            # Jakykoli B/C/D = mereni (nezavisle na stylu Excelu)
            $role = "DATA"
        }
        elseif ($null -ne $styleA -and $styleA -eq $STYLE_DATE) { $role = "DATE" }
        elseif ($null -ne $styleA -and $styleA -eq $STYLE_STATION) { $role = "STATION" }
        elseif (Test-LooksLikeDate $a) { $role = "DATE" }
        elseif ($rows.Count -eq 0) { $role = "DATE" }
        else { $role = "STATION" }

        [void]$rows.Add((New-Row $role $a $b $c $d))
    }

    # Diagnostika: ukaz prvni neprázdne radky
    $shown = 0
    foreach ($r in $rows) {
        if ($r.Role -eq "BLANK") { continue }
        Write-Host ("    priklad: [{0}] A='{1}' B='{2}' C='{3}' D='{4}'" -f $r.Role, $r.A, $r.B, $r.C, $r.D)
        $shown++
        if ($shown -ge 3) { break }
    }
    if ($shown -eq 0) {
        Write-Host "    priklad: (zadne neprázdne bunky — soubor je prazdny nebo nečitelný)"
    }

    [object[]]$result = $rows.ToArray()
    return $result
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

function Write-ZipEntry([System.IO.Compression.ZipArchive]$zip, [string]$name, [string]$body) {
    $e = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $sw = New-Object System.IO.StreamWriter($e.Open(), $utf8)
    try { $sw.Write($body) } finally { $sw.Dispose() }
}

function Write-Xlsx([string]$path, $rows) {
    $dir = Split-Path -Parent $path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    $zip = [System.IO.Compression.ZipFile]::Open($path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Write-ZipEntry $zip "[Content_Types].xml" $CONTENT_TYPES
        Write-ZipEntry $zip "_rels/.rels" $RELS_ROOT
        Write-ZipEntry $zip "xl/workbook.xml" $WORKBOOK
        Write-ZipEntry $zip "xl/_rels/workbook.xml.rels" $RELS_WB
        Write-ZipEntry $zip "xl/styles.xml" $STYLES
        Write-ZipEntry $zip "xl/worksheets/sheet1.xml" (Get-SheetXml $rows)
    }
    finally { $zip.Dispose() }
}

function Test-IsDailyMd1Name([string]$name) {
    return $name -match '^\d{6}(_\d+)?_MD1\.xlsx$'
}

function Get-Md1Files([string]$dir) {
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) { return @() }
    Get-ChildItem -LiteralPath $dir -File -Filter "*.xlsx" |
        Where-Object {
            (Test-IsDailyMd1Name $_.Name) -and
            $_.Name -notlike '~$*'
        } |
        Sort-Object { $_.Name.ToLowerInvariant() }
}

function Resolve-Layout([string]$folderPath) {
    $daysSub = Join-Path $folderPath $DAYS_SUBFOLDER_NAME
    if (Test-Path -LiteralPath $daysSub -PathType Container) {
        return [pscustomobject]@{
            Source = $daysSub
            Output = (Join-Path $folderPath $SUMMARY_XLSX_NAME)
        }
    }
    $leaf = Split-Path -Leaf $folderPath
    if ($leaf -eq $DAYS_SUBFOLDER_NAME) {
        $parent = Split-Path -Parent $folderPath
        return [pscustomobject]@{
            Source = $folderPath
            Output = (Join-Path $parent $SUMMARY_XLSX_NAME)
        }
    }
    # Fallback: daily files directly in this folder
    return [pscustomobject]@{
        Source = $folderPath
        Output = (Join-Path $folderPath $SUMMARY_XLSX_NAME)
    }
}

# ---- main ----
try {
    if (-not (Test-Path -LiteralPath $Folder)) {
        Write-Host "Slozka neexistuje:"
        Write-Host "  $Folder"
        exit 2
    }
    $folderPath = (Resolve-Path -LiteralPath $Folder).Path
    if (-not (Test-Path -LiteralPath $folderPath -PathType Container)) {
        Write-Host "Cesta neni slozka:"
        Write-Host "  $folderPath"
        exit 2
    }

    # Prefer days subfolder only when it actually has daily files
    $daysSub = Join-Path $folderPath $DAYS_SUBFOLDER_NAME
    $files = @()
    $sourcePath = $folderPath

    if (Test-Path -LiteralPath $daysSub -PathType Container) {
        $inDays = @(Get-Md1Files $daysSub)
        if ($inDays.Count -gt 0) {
            $files = $inDays
            $sourcePath = $daysSub
        }
    }

    if ($files.Count -eq 0) {
        $leaf = Split-Path -Leaf $folderPath
        if ($leaf -eq $DAYS_SUBFOLDER_NAME) {
            $files = @(Get-Md1Files $folderPath)
            $sourcePath = $folderPath
            $outPath = Join-Path (Split-Path -Parent $folderPath) $SUMMARY_XLSX_NAME
        }
        else {
            $files = @(Get-Md1Files $folderPath)
            $sourcePath = $folderPath
            if ([string]::IsNullOrWhiteSpace($Output)) {
                $outPath = Join-Path $folderPath $SUMMARY_XLSX_NAME
            }
        }
    }
    else {
        if ([string]::IsNullOrWhiteSpace($Output)) {
            $outPath = Join-Path $folderPath $SUMMARY_XLSX_NAME
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($Output)) {
        if ([System.IO.Path]::IsPathRooted($Output)) {
            $outPath = $Output
        }
        else {
            $outPath = Join-Path $folderPath $Output
        }
    }

    Write-Host ("Hlavni / zadana slozka: {0}" -f $folderPath)
    Write-Host ("Denni soubory:          {0}" -f $sourcePath)
    Write-Host ("Souhrn:                 {0}" -f $outPath)
    Write-Host ("Nalezeno YYMMDD_*_MD1.xlsx: {0}" -f $files.Count)

    if ($files.Count -eq 0) {
        Write-Host ""
        Write-Host "CHYBA: nenasel jsem denni soubory (napr. 260811_1_MD1.xlsx)."
        Write-Host "Dej je bud primo do hlavni slozky, nebo do podslozky MD1_popis_dny."
        Write-Host ""
        Write-Host "Co je ve slozce (xlsx):"
        Get-ChildItem -LiteralPath $folderPath -File -Filter "*.xlsx" -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Host ("  - {0}" -f $_.Name) }
        if (Test-Path -LiteralPath $daysSub) {
            Write-Host ("Co je v {0}:" -f $DAYS_SUBFOLDER_NAME)
            Get-ChildItem -LiteralPath $daysSub -File -ErrorAction SilentlyContinue |
                ForEach-Object { Write-Host ("  - {0}" -f $_.Name) }
        }
        exit 1
    }

    $out = New-Object System.Collections.Generic.List[object]
    $currentDate = ""
    $lastStation = ""
    $ok = 0
    $skipped = 0

    foreach ($f in $files) {
        try {
            $srcRows = @(Read-XlsxRows $f.FullName)
        } catch {
            Write-Host ("  ! Chyba cteni {0}: {1}" -f $f.Name, $_.Exception.Message)
            $skipped++
            continue
        }
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
        Write-Host "CHYBA: soubory jsem nasel, ale uvnitr nejsou citelna DATA (koleje/vyhybky/...)."
        Write-Host "1) V OneDrive u souboru zrusit online-only (Keep on this device)."
        Write-Host "2) Kouknout vyse na radky sharedStrings / priklad A/B/C/D."
        Write-Host "3) Soubory musi byt primo z appky (YYMMDD_N_MD1.xlsx), ne prazdny souhrn."
        exit 1
    }

    Write-Xlsx $outPath $out.ToArray()

    Write-Host ("Soubory OK: {0}" -f $ok)
    Write-Host ("Preskoceno: {0}" -f $skipped)
    Write-Host ("Datovych radku: {0}" -f $dataOut)
    Write-Host ("Ulozeno: {0}" -f $outPath)
    Write-Host ""
    Write-Host "Oteviram Excel..."
    try {
        Invoke-Item -LiteralPath $outPath
    } catch {
        try {
            Start-Process -FilePath $outPath
        } catch {
            Write-Host ("Nepodarilo se otevrit soubor: {0}" -f $_.Exception.Message)
            Write-Host "Otevri ho rucne v Excelu."
        }
    }
    exit 0
}
catch {
    Write-Host ""
    Write-Host "CHYBA skriptu:"
    Write-Host $_.Exception.Message
    Write-Host $_.ScriptStackTrace
    exit 99
}
