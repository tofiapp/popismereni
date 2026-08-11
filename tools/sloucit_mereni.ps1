#Requires -Version 5.1
# sloucit_mereni.ps1 — verze 2026-08-11i
# ASCII-only source (Windows PowerShell 5.1). Czech names via [char] codes.
# Layout:
#   Popis_mereni_MD1/
#     Popis_mereni_MD1.xlsx
#     Dny/YYMMDD_N_MD1.xlsx (+ Dny/slouceno/)
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
$DAYS_SUBFOLDER_NAME = "Dny"
$DAYS_SUBFOLDER_LEGACY = "MD1_popis_dny"
$SUMMARY_XLSX_NAME = $MAIN_FOLDER_NAME + ".xlsx"
$cCaron = [char]0x010D
$ARCHIVE_FOLDER_NAME = "slou" + $cCaron + "eno"
$MAIN_FOLDER_ASCII = "Popis_mereni_MD1"
$SUMMARY_ASCII = "Popis_mereni_MD1.xlsx"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$STYLE_DATE = 1
$STYLE_STATION = 2
$STYLE_DATA = 3
$STYLE_UPDATED = 4
$UPDATE_PREFIX = "Naposledy aktualizovano:"

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

$STYLE_BUTTON = 5
$BUTTON_LABEL = "Aktualizovat"
$UPDATE_BAT_NAME = "SloucitMereni.bat"
# ASCII nazev — Excel/odkazy nemaji problem s diakritikou v JMENU souboru
$UPDATE_CMD_NAME = "Aktualizovat.cmd"

function Get-ShortPathName([string]$path) {
    $full = [System.IO.Path]::GetFullPath($path)
    if (-not (Test-Path -LiteralPath $full)) { return $full }
    try {
        if (-not ("Win32ShortPath" -as [type])) {
            Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Win32ShortPath {
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern int GetShortPathName(string path, StringBuilder shortPath, int bufferSize);
}
"@
        }
        $sb = New-Object System.Text.StringBuilder 1024
        $n = [Win32ShortPath]::GetShortPathName($full, $sb, $sb.Capacity)
        if ($n -gt 0 -and $sb.Length -gt 0) { return $sb.ToString() }
    } catch { }
    return $full
}

function Get-FileUri([string]$path) {
    # file:/// + ideálně 8.3 cesta (OneDrive diakritika v Excelu jinak rozbije odkaz)
    $full = Get-ShortPathName $path
    return ([uri]$full).AbsoluteUri
}

function Get-SheetRelsXml([string]$batPath) {
    $target = Escape-Xml (Get-FileUri $batPath)
    return @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="$target" TargetMode="External"/>
</Relationships>
"@
}

function Write-UpdateCmd([string]$dir) {
    # ASCII-only launcher vedle souhrnu (volany z Excel tlacitka)
    # POZOR: obsah CMD musi byt v single-quoted here-string — jinak PS rozbije uvozovky.
    $cmdPath = Join-Path $dir $UPDATE_CMD_NAME
    $body = @'
@echo off
setlocal
chcp 65001 >nul
REM Launcher pro Excel tlacitko Aktualizovat (ASCII nazev souboru)
set "DIR=%~dp0"
if not exist "%DIR%sloucit_mereni.ps1" (
  echo CHYBA: chybi sloucit_mereni.ps1 vedle tohoto CMD
  pause
  exit /b 2
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%DIR%sloucit_mereni.ps1" -Folder "%DIR%."
set "ERR=%ERRORLEVEL%"
if not "%ERR%"=="0" (
  echo.
  echo Chyba %ERR%
  pause
)
endlocal & exit /b %ERR%
'@
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($cmdPath, ($body -replace "`n", "`r`n"), $utf8)
    return $cmdPath
}

function Resolve-UpdateBatPath([string]$summaryPath) {
    $dir = Split-Path -Parent $summaryPath
    $here = $PSScriptRoot
    if (-not $here) { $here = Split-Path -Parent $MyInvocation.MyCommand.Path }

    # Dopln bat+ps1 k souhrnu, pokud chybi
    $batDst = Join-Path $dir $UPDATE_BAT_NAME
    $ps1Dst = Join-Path $dir "sloucit_mereni.ps1"
    $batSrc = Join-Path $here $UPDATE_BAT_NAME
    $ps1Src = Join-Path $here "sloucit_mereni.ps1"
    try {
        if ((Test-Path -LiteralPath $batSrc) -and -not (Test-Path -LiteralPath $batDst)) {
            Copy-Item -LiteralPath $batSrc -Destination $batDst -Force
        }
        if ((Test-Path -LiteralPath $ps1Src)) {
            # vzdy aktualizuj ps1 vedle souhrnu pri zapisu xlsx? ne — jen kdyz chybi
            if (-not (Test-Path -LiteralPath $ps1Dst)) {
                Copy-Item -LiteralPath $ps1Src -Destination $ps1Dst -Force
            }
        }
    } catch { }

    # Pro Excel hyperlink pouzij ASCII Aktualizovat.cmd (ne SloucitMereni.bat v Unicode ceste zobrazeni)
    try {
        return (Write-UpdateCmd $dir)
    } catch {
        return $batDst
    }
}

$STYLES = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="5">
    <font><sz val="14"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="12"/><i/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="12"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBBDEFB"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFE0B2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE8F5E9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1565C0"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="6">
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
    <xf numFmtId="0" fontId="3" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="4" fillId="5" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
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


function Test-IsUpdateText([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return $false }
    $t = $s.Trim().ToLowerInvariant()
    return ($t.StartsWith("naposledy aktualizov"))
}

function New-Row([string]$RoleName, [string]$A = "", [string]$B = "", [string]$C = "", [string]$D = "") {
    return [pscustomobject]@{ Role = $RoleName; A = $A; B = $B; C = $C; D = $D }
}

function New-UpdateRow {
    $stamp = Get-Date -Format "dd.MM.yyyy HH:mm"
    $prefix = "Naposledy aktualizov" + [char]0x00E1 + "no:"
    return (New-Row "UPDATED" ("{0} {1}" -f $prefix, $stamp))
}

function Test-IsRowObject($obj) {
    if ($null -eq $obj) { return $false }
    if ($obj -is [string]) { return $false }
    try {
        return ($null -ne $obj.PSObject.Properties['Role'])
    } catch {
        return $false
    }
}

function Add-UpdateStamp {
    # PS 5.1: NIKDY nepouzivat @($List[object]) — hodi "Typy argumentu neodpovidaji".
    # Predavat pole (ToArray) nebo indexovat pres Count / [$i].
    param($Rows)

    $list = New-Object System.Collections.Generic.List[object]
    [void]$list.Add((New-UpdateRow))
    [void]$list.Add((New-Row "BLANK"))

    if ($null -ne $Rows) {
        $arr = $null
        try {
            if ($Rows -is [System.Array]) {
                $arr = [object[]]$Rows
            } elseif ($Rows.PSObject.Methods['ToArray']) {
                $arr = [object[]]$Rows.ToArray()
            }
        } catch {
            $arr = $null
        }

        if ($null -ne $arr) {
            $skippingHead = $true
            foreach ($r in $arr) {
                if (-not (Test-IsRowObject $r)) { continue }
                $role = [string]$r.Role
                if ($skippingHead) {
                    $aVal = ""
                    try { $aVal = [string]$r.A } catch { $aVal = "" }
                    if (($role -eq "UPDATED") -or (Test-IsUpdateText $aVal)) { continue }
                    if ($role -eq "BLANK") { continue }
                    $skippingHead = $false
                }
                [void]$list.Add($r)
            }
        }
    }

    # Unary comma = vratit List jako 1 objekt (ne rozbalit na radky)
    return ,$list
}

function Add-UpdateStampToList {
    param($Rows)
    return (Add-UpdateStamp -Rows $Rows)
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
        elseif ((Test-IsUpdateText $a) -or ($null -ne $styleA -and $styleA -eq $STYLE_UPDATED)) {
            $role = "UPDATED"
        }
        elseif ($b -or $c -or $d) {
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
    [void]$sb.Append('<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">')
    # Zmrazeny 1. radek (razitko + tlacitko) — zustane viditelny pri scrollovani
    [void]$sb.Append('<sheetViews><sheetView workbookViewId="0">')
    [void]$sb.Append('<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>')
    [void]$sb.Append('<selection pane="bottomLeft" activeCell="A2" sqref="A2"/>')
    [void]$sb.Append('</sheetView></sheetViews>')
    [void]$sb.Append('<cols>')
    [void]$sb.Append('<col min="1" max="1" width="36" customWidth="1"/>')
    [void]$sb.Append('<col min="2" max="2" width="16" customWidth="1"/>')
    [void]$sb.Append('<col min="3" max="3" width="14" customWidth="1"/>')
    [void]$sb.Append('<col min="4" max="4" width="36" customWidth="1"/>')
    [void]$sb.Append('</cols><sheetData>')
    $r = 0
    $buttonRow = 0
    foreach ($row in $rows) {
        if (-not (Test-IsRowObject $row)) { continue }
        $r++
        $ht = $(if ($row.Role -eq "BLANK") { "12" } else { "24" })
        [void]$sb.Append(('<row r="{0}" ht="{1}" customHeight="1">' -f $r, $ht))
        switch ($row.Role) {
            "BLANK" { }
            "DATE" { [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_DATE)) }
            "STATION" { [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_STATION)) }
            "UPDATED" {
                [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_UPDATED))
                # Modre "tlacitko" — file:/// hyperlink na lokalni SloucitMereni.bat (bez VBA; ne https)
                [void]$sb.Append((Get-CellXml ("B$r") $BUTTON_LABEL $STYLE_BUTTON))
                if ($buttonRow -eq 0) { $buttonRow = $r }
            }
            default {
                [void]$sb.Append((Get-CellXml ("A$r") $row.A $STYLE_DATA))
                [void]$sb.Append((Get-CellXml ("B$r") $row.B $STYLE_DATA))
                [void]$sb.Append((Get-CellXml ("C$r") $row.C $STYLE_DATA))
                [void]$sb.Append((Get-CellXml ("D$r") $row.D $STYLE_DATA))
            }
        }
        [void]$sb.Append('</row>')
    }
    [void]$sb.Append('</sheetData>')
    if ($buttonRow -gt 0) {
        [void]$sb.Append(('<hyperlinks><hyperlink ref="B{0}" r:id="rId1" display="{1}"/></hyperlinks>' -f $buttonRow, (Escape-Xml $BUTTON_LABEL)))
    }
    [void]$sb.Append('</worksheet>')
    return $sb.ToString()
}

function Close-WorkbookIfOpen([string]$path) {
    # Aby slo prepsat souhrn po kliku na tlacitko v Excelu
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $false }
    $full = $null
    try { $full = [System.IO.Path]::GetFullPath($path) } catch { return $false }
    $excel = $null
    try {
        $excel = [System.Runtime.InteropServices.Marshal]::GetActiveObject("Excel.Application")
    } catch {
        return $false
    }
    $closed = $false
    try {
        foreach ($wb in @($excel.Workbooks)) {
            try {
                $wbPath = [string]$wb.FullName
                if ([string]::IsNullOrEmpty($wbPath)) { continue }
                $wbFull = [System.IO.Path]::GetFullPath($wbPath)
                if ($wbFull.Equals($full, [System.StringComparison]::OrdinalIgnoreCase)) {
                    $wb.Close($false) | Out-Null
                    $closed = $true
                    Write-Host "Zaviram otevreny souhrn v Excelu (kvuli prepsani)..."
                }
            } catch { }
        }
    } catch { }
    return $closed
}

function Open-SummaryExcel([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Host ("Souhrn neexistuje: {0}" -f $path)
        return
    }
    Write-Host ""
    Write-Host "Oteviram Excel..."
    try {
        Invoke-Item -LiteralPath $path
    } catch {
        try {
            Start-Process -FilePath $path
        } catch {
            Write-Host ("Nepodarilo se otevrit soubor: {0}" -f $_.Exception.Message)
            Write-Host "Otevri ho rucne v Excelu."
        }
    }
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
    Close-WorkbookIfOpen $path | Out-Null
    # Kratka pauza po Close, aby Windows uvolnil zamek
    Start-Sleep -Milliseconds 400
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    $batPath = Resolve-UpdateBatPath $path
    $sheetRels = Get-SheetRelsXml $batPath
    $zip = [System.IO.Compression.ZipFile]::Open($path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Write-ZipEntry $zip "[Content_Types].xml" $CONTENT_TYPES
        Write-ZipEntry $zip "_rels/.rels" $RELS_ROOT
        Write-ZipEntry $zip "xl/workbook.xml" $WORKBOOK
        Write-ZipEntry $zip "xl/_rels/workbook.xml.rels" $RELS_WB
        Write-ZipEntry $zip "xl/styles.xml" $STYLES
        Write-ZipEntry $zip "xl/worksheets/sheet1.xml" (Get-SheetXml $rows)
        Write-ZipEntry $zip "xl/worksheets/_rels/sheet1.xml.rels" $sheetRels
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
    Write-Host "sloucit_mereni.ps1 verze 2026-08-11i"
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

    # Prefer Dny/ (or legacy MD1_popis_dny) when it has daily files
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
        $legacySub = Join-Path $folderPath $DAYS_SUBFOLDER_LEGACY
        if (Test-Path -LiteralPath $legacySub -PathType Container) {
            $inLegacy = @(Get-Md1Files $legacySub)
            if ($inLegacy.Count -gt 0) {
                $files = $inLegacy
                $sourcePath = $legacySub
                $daysSub = $legacySub
            }
        }
    }

    if ($files.Count -eq 0) {
        $leaf = Split-Path -Leaf $folderPath
        if (($leaf -eq $DAYS_SUBFOLDER_NAME) -or ($leaf -eq $DAYS_SUBFOLDER_LEGACY)) {
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
        Write-Host "Neni nic noveho ke slouceni (zadne YYMMDD_*_MD1.xlsx v Dny/)."
        Write-Host "To neni chyba — nove denni soubory z appky dej do Dny/."
        if ((Test-Path -LiteralPath $outPath -PathType Leaf) -and ((Get-Item -LiteralPath $outPath).Length -gt 64)) {
            try {
                $existingOnly = @(Read-XlsxRows $outPath)
                $keep = New-Object System.Collections.Generic.List[object]
                $skipHead = $true
                foreach ($row in $existingOnly) {
                    if (-not (Test-IsRowObject $row)) { continue }
                    if ($skipHead) {
                        if (($row.Role -eq "UPDATED") -or (Test-IsUpdateText ([string]$row.A))) { continue }
                        if ($row.Role -eq "BLANK") { continue }
                        $skipHead = $false
                    }
                    [void]$keep.Add($row)
                }
                $stampedOnly = Add-UpdateStamp -Rows $keep.ToArray()
                Write-Xlsx $outPath $stampedOnly
                Write-Host ("Aktualizace: {0}" -f $stampedOnly[0].A)
            } catch {
                Write-Host ("Souhrn neprepisuji ({0}), jen otevru." -f $_.Exception.Message)
            }
            Open-SummaryExcel $outPath
        }
        else {
            Write-Host ("Souhrn zatim neexistuje: {0}" -f $outPath)
        }
        exit 0
    }

    $mergeRows = New-Object System.Collections.Generic.List[object]
    $processed = New-Object System.Collections.Generic.List[object]
    $currentDate = ""
    $lastStation = ""
    $ok = 0
    $skipped = 0

    # Existujici souhrn = zaklad (nove denni soubory se pripoji)
    if ((Test-Path -LiteralPath $outPath -PathType Leaf) -and ((Get-Item -LiteralPath $outPath).Length -gt 64)) {
        try {
            $existing = @(Read-XlsxRows $outPath)
            $skipHead = $true
            foreach ($row in $existing) {
                if (-not (Test-IsRowObject $row)) { continue }
                if ($skipHead) {
                    if (($row.Role -eq "UPDATED") -or (Test-IsUpdateText ([string]$row.A))) { continue }
                    if ($row.Role -eq "BLANK") { continue }
                    $skipHead = $false
                }
                [void]$mergeRows.Add($row)
                if ($row.Role -eq "DATE" -and $row.A) { $currentDate = $row.A; $lastStation = "" }
                elseif ($row.Role -eq "STATION" -and $row.A) { $lastStation = $row.A }
            }
            Write-Host ("Nacten existujici souhrn: {0} radku" -f $mergeRows.Count)
        } catch {
            Write-Host ("  ! Souhrn nejde nacist, vytvorim novy: {0}" -f $_.Exception.Message)
            $mergeRows.Clear()
            $currentDate = ""
            $lastStation = ""
        }
    }

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
            if (-not (Test-IsRowObject $row)) { continue }
            if ($row.Role -eq "BLANK" -or (-not ($row.A -or $row.B -or $row.C -or $row.D))) { continue }

            if ($row.Role -eq "DATE") {
                if ($row.A -ne $currentDate) {
                    if ($mergeRows.Count -gt 0) { [void]$mergeRows.Add((New-Row "BLANK")) }
                    [void]$mergeRows.Add((New-Row "DATE" $row.A))
                    $currentDate = $row.A
                    $lastStation = ""
                    $wrote = $true
                }
                continue
            }

            if ($row.Role -eq "STATION") {
                if (-not $currentDate -and $guess) {
                    if ($mergeRows.Count -gt 0) { [void]$mergeRows.Add((New-Row "BLANK")) }
                    [void]$mergeRows.Add((New-Row "DATE" $guess))
                    $currentDate = $guess
                    $lastStation = ""
                }
                if ($row.A -ne $lastStation) {
                    [void]$mergeRows.Add((New-Row "BLANK"))
                    [void]$mergeRows.Add((New-Row "STATION" $row.A))
                    $lastStation = $row.A
                    $wrote = $true
                }
                continue
            }

            if (-not $currentDate -and $guess) {
                if ($mergeRows.Count -gt 0) { [void]$mergeRows.Add((New-Row "BLANK")) }
                [void]$mergeRows.Add((New-Row "DATE" $guess))
                $currentDate = $guess
                $lastStation = ""
            }
            [void]$mergeRows.Add((New-Row "DATA" $row.A $row.B $row.C $row.D))
            $wrote = $true
        }

        if ($wrote) {
            $ok++
            [void]$processed.Add($f)
        } else {
            $skipped++
        }
    }

    $dataOut = 0
    for ($di = 0; $di -lt $mergeRows.Count; $di++) {
        $dr = $mergeRows[$di]
        if ((Test-IsRowObject $dr) -and ($dr.Role -eq "DATA")) { $dataOut++ }
    }
    if ($dataOut -eq 0) {
        Write-Host ""
        Write-Host "CHYBA: soubory jsem nasel, ale uvnitr nejsou citelna DATA (koleje/vyhybky/...)."
        Write-Host "1) V OneDrive u souboru zrusit online-only (Keep on this device)."
        Write-Host "2) Kouknout vyse na radky sharedStrings / priklad A/B/C/D."
        Write-Host "3) Soubory musi byt primo z appky (YYMMDD_N_MD1.xlsx), ne prazdny souhrn."
        exit 1
    }

    # ToArray() — ne @($mergeRows), to v PS 5.1 pada u List[object]
    $stamped = Add-UpdateStamp -Rows $mergeRows.ToArray()
    Write-Xlsx $outPath $stamped
    Write-Host ("Aktualizace: {0}" -f $stamped[0].A)

    Write-Host ("Soubory OK: {0}" -f $ok)
    Write-Host ("Preskoceno: {0}" -f $skipped)
    Write-Host ("Datovych radku: {0}" -f $dataOut)
    Write-Host ("Ulozeno: {0}" -f $outPath)

    # Presun sloucenych dennich souboru do Dny/slouceno
    $archiveRoot = Split-Path -Parent $outPath
    $daysDir = Join-Path $archiveRoot $DAYS_SUBFOLDER_NAME
    if (-not (Test-Path -LiteralPath $daysDir)) {
        # legacy fallback
        $legacy = Join-Path $archiveRoot $DAYS_SUBFOLDER_LEGACY
        if (Test-Path -LiteralPath $legacy) { $daysDir = $legacy }
        else { New-Item -ItemType Directory -Path $daysDir -Force | Out-Null }
    }
    $archiveDir = Join-Path $daysDir $ARCHIVE_FOLDER_NAME
    if (-not (Test-Path -LiteralPath $archiveDir)) {
        New-Item -ItemType Directory -Path $archiveDir -Force | Out-Null
    }
    $moved = 0
    foreach ($f in $processed) {
        try {
            $dest = Join-Path $archiveDir $f.Name
            if (Test-Path -LiteralPath $dest) {
                $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
                $dest = Join-Path $archiveDir ("{0}_{1}{2}" -f $f.BaseName, $stamp, $f.Extension)
            }
            Move-Item -LiteralPath $f.FullName -Destination $dest -Force
            $moved++
        } catch {
            Write-Host ("  ! Nepodarilo se presunout {0}: {1}" -f $f.Name, $_.Exception.Message)
        }
    }
    Write-Host ("Presunuto do {0}/{1}: {2} souboru" -f $DAYS_SUBFOLDER_NAME, $ARCHIVE_FOLDER_NAME, $moved)

    Open-SummaryExcel $outPath
    exit 0
}
catch {
    Write-Host ""
    Write-Host "CHYBA skriptu:"
    Write-Host $_.Exception.Message
    Write-Host $_.ScriptStackTrace
    exit 99
}
