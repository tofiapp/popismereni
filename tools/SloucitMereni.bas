Attribute VB_Name = "SloucitMereni"
Option Explicit

' ONE shared workbook:
'   - macros + button (Start) + result (Mereni)
'   - Auto_Open: merge on open
'   - folder watch: every POLL_SECONDS check *_MD1.xlsx; if new/changed -> merge
'   (Excel must stay open for the watch; closed file waits until next open)
'
' Setup (ONE step):
'   1) New empty workbook
'   2) Alt+F11 -> Datei importieren -> this .bas  (module under THIS workbook)
'   3) Alt+F8 -> Mereni_Nastavit  (or VytvoritTlacitko)
'      -> dialog: save as Souhrn_mereni.xlsm into folder with *_MD1.xlsx
'      -> creates button in THE SAME file (macros stay inside)
'   4) Close other workbooks. Next time open only Souhrn_mereni.xlsm + enable macros.
'
' Do NOT save as .xlsx (that deletes macros). Do NOT run Nastavit from a different file.

Private Const FILE_PATTERN As String = "*_MD1.xlsx"
Private Const COLOR_DATE As Long = 16506555
Private Const COLOR_STATION As Long = 11723007
Private Const POLL_SECONDS As Long = 120
Private Const SIG_NAME As String = "MereniFolderSig"

' next OnTime tick (must cancel on close)
Public gNextPoll As Date
Private gBusy As Boolean

' ---------- auto start / stop ----------
Public Sub Auto_Open()
    Application.StatusBar = "Mereni: slouceni pri otevreni..."
    Call SloucitCore(True)
    Call StartFolderWatch
    Application.StatusBar = "Mereni: hlida slozku kazdych " & POLL_SECONDS & " s"
End Sub

Public Sub Auto_Close()
    Call StopFolderWatch
    Application.StatusBar = False
End Sub

Public Sub StartFolderWatch()
    Call StopFolderWatch
    On Error Resume Next
    gNextPoll = Now + TimeSerial(0, 0, POLL_SECONDS)
    Application.OnTime EarliestTime:=gNextPoll, Procedure:=QualifiedMacro("CheckFolderAndRefresh"), Schedule:=True
    On Error GoTo 0
End Sub

Public Sub StopFolderWatch()
    On Error Resume Next
    If gNextPoll <> 0 Then
        Application.OnTime EarliestTime:=gNextPoll, Procedure:=QualifiedMacro("CheckFolderAndRefresh"), Schedule:=False
    End If
    On Error GoTo 0
    gNextPoll = 0
End Sub

' "'Souhrn_mereni.xlsm'!MacroName" so button/OnTime find macros in THIS file
Private Function QualifiedMacro(ByVal macroName As String) As String
    QualifiedMacro = "'" & Replace(ThisWorkbook.Name, "'", "''") & "'!" & macroName
End Function

' ========== once: save THIS workbook as xlsm + button (same file) ==========
' Prefer this name in Alt+F8 list
Public Sub Mereni_Nastavit()
    Call VytvoritTlacitko
End Sub

Public Sub VytvoritTlacitko()
    Dim wb As Workbook
    Set wb = ThisWorkbook

    ' Must import .bas into the workbook you are setting up (not into another open file)
    Dim savePath As Variant
    Dim needSaveAs As Boolean
    needSaveAs = (Len(wb.Path) = 0) Or (LCase$(Right$(wb.Name, 5)) <> ".xlsm")

    If needSaveAs Then
        MsgBox "Vyber slozku se soubory *_MD1.xlsx a uloz jako Souhrn_mereni.xlsm." & vbCrLf & _
               "Dulezite: typ souboru musi byt .xlsm (s makry), ne .xlsx.", vbInformation

        savePath = Application.GetSaveAsFilename( _
            InitialFileName:=DefaultSaveSuggestion(), _
            FileFilter:="Excel s makry (*.xlsm), *.xlsm", _
            Title:="Ulozit Souhrn_mereni.xlsm do slozky s merenim")

        If savePath = False Then
            MsgBox "Zruseno. Bez ulozeni .xlsm nejde tlacitko vytvorit.", vbExclamation
            Exit Sub
        End If
        If LCase$(Right$(CStr(savePath), 5)) <> ".xlsm" Then
            savePath = CStr(savePath) & ".xlsm"
        End If

        Application.DisplayAlerts = False
        On Error Resume Next
        wb.SaveAs Filename:=CStr(savePath), FileFormat:=52
        If Err.Number <> 0 Then
            Dim errMsg As String
            errMsg = Err.Description
            Err.Clear
            Application.DisplayAlerts = True
            MsgBox "Ulozeni .xlsm selhalo:" & vbCrLf & errMsg & vbCrLf & vbCrLf & _
                   "Zkus jinou slozku (nebo lokalni disk) a znovu Mereni_Nastavit.", vbCritical
            Exit Sub
        End If
        On Error GoTo 0
        Application.DisplayAlerts = True
    Else
        wb.Save
    End If

    ' After SaveAs, ThisWorkbook IS Souhrn_mereni.xlsm and still has this module
    Dim wsBtn As Worksheet
    Set wsBtn = EnsureSheet(wb, "Start")
    On Error Resume Next
    wsBtn.Move Before:=wb.Sheets(1)
    On Error GoTo 0

    wsBtn.Cells.Clear
    wsBtn.Range("A1").Value = "Mereni - slouceni"
    wsBtn.Range("A1").Font.Size = 18
    wsBtn.Range("A1").Font.Bold = True
    wsBtn.Range("A3").Value = "1) Povol makra (Inhalt aktivieren)."
    wsBtn.Range("A4").Value = "2) Tlacitko nize = sloucit ted. Jinak bezi samo pri otevreni + kazde 2 min."
    wsBtn.Range("A5").Value = "Slozka zdroju (= tento soubor): " & wb.Path
    wsBtn.Range("A6").Value = "Soubor: " & wb.FullName
    wsBtn.Range("A8").Value = "Kdyz tlacitko hlasi ze makro neni v souboru: makra jsou v JINEM sesitu." & _
        " Zavri ostatni, nech jen tento .xlsm, znovu import .bas sem, znovu Mereni_Nastavit."
    wsBtn.Columns("A").ColumnWidth = 100

    Dim shp As Shape
    On Error Resume Next
    For Each shp In wsBtn.Shapes
        shp.Delete
    Next shp
    On Error GoTo 0

    Dim btn As Button
    Set btn = wsBtn.Buttons.Add(Left:=20, Top:=160, Width:=300, Height:=55)
    ' Point explicitly at THIS workbook (fixes "macro not in this workbook")
    btn.OnAction = QualifiedMacro("SloucitVsechnaMereni")
    btn.Characters.Text = "Sloucit ted"
    btn.Font.Size = 16
    btn.Font.Bold = True

    Call EnsureSheet(wb, "Mereni")
    Call StartFolderWatch
    wb.Save

    MsgBox "Hotovo. Makra + tlacitko jsou v JEDNOM souboru:" & vbCrLf & wb.FullName & vbCrLf & vbCrLf & _
           "Zavri ostatni sesity (Mappe1/Sesit1)." & vbCrLf & _
           "Priste otevri jen tento .xlsm -> Inhalt aktivieren -> tlacitko nebo pockej na auto.", vbInformation
End Sub

Private Function DefaultSaveSuggestion() As String
    Dim p As String
    p = Environ$("USERPROFILE")
    If Len(ThisWorkbook.Path) > 0 Then
        DefaultSaveSuggestion = ThisWorkbook.Path & "\Souhrn_mereni.xlsm"
    ElseIf Len(p) > 0 Then
        DefaultSaveSuggestion = p & "\Souhrn_mereni.xlsm"
    Else
        DefaultSaveSuggestion = "Souhrn_mereni.xlsm"
    End If
End Function

Private Function SourceFolder() As String
    Dim p As String
    p = ThisWorkbook.Path

    ' Opened from browser / SharePoint URL -> Dir() throws error 52
    If Len(p) = 0 Or IsWebPath(p) Or IsWebPath(ThisWorkbook.FullName) Then
        SourceFolder = ""
        Exit Function
    End If

    p = Replace(p, "/", "\")
    Do While Right$(p, 1) = "\"
        p = Left$(p, Len(p) - 1)
    Loop
    SourceFolder = p
End Function

Private Function SourcePath() As String
    Dim p As String
    p = SourceFolder()
    If Len(p) = 0 Then
        SourcePath = ""
        Exit Function
    End If
    SourcePath = p & "\"
End Function

Private Function IsWebPath(ByVal p As String) As Boolean
    Dim t As String
    t = LCase$(Trim$(p))
    If Len(t) = 0 Then
        IsWebPath = False
        Exit Function
    End If
    IsWebPath = (Left$(t, 7) = "http://") Or (Left$(t, 8) = "https://") Or _
                (InStr(t, "://") > 0) Or (InStr(t, "sharepoint.com") > 0)
End Function

Private Function FolderExistsLocal(ByVal folderPath As String) As Boolean
    On Error Resume Next
    FolderExistsLocal = ((GetAttr(folderPath) And vbDirectory) = vbDirectory)
    If Err.Number <> 0 Then
        Err.Clear
        FolderExistsLocal = False
    End If
    On Error GoTo 0
End Function

' Dir() on bad/URL path = runtime 52 - always wrap
Private Function SafeDir(ByVal pathOrPattern As String) As String
    On Error Resume Next
    SafeDir = Dir(pathOrPattern)
    If Err.Number <> 0 Then
        Err.Clear
        SafeDir = ""
    End If
    On Error GoTo 0
End Function

Private Function SafeDirNext() As String
    On Error Resume Next
    SafeDirNext = Dir()
    If Err.Number <> 0 Then
        Err.Clear
        SafeDirNext = ""
    End If
    On Error GoTo 0
End Function

Private Sub ExplainBadPath(ByVal silent As Boolean)
    If silent Then
        Application.StatusBar = "Mereni: otevri soubor z lokalni OneDrive slozky (ne z prohlizece)"
        Exit Sub
    End If
    MsgBox "Cesta k souboru neni lokalni disk (casto https:// SharePoint)." & vbCrLf & vbCrLf & _
           "Oprav:" & vbCrLf & _
           "1) Ve Windows Exploreru otevri synchronizovanou OneDrive slozku" & vbCrLf & _
           "2) Dvojklik na Souhrn_mereni.xlsm (ne Open in Browser)" & vbCrLf & _
           "3) Inhalt aktivieren / povolit makra" & vbCrLf & vbCrLf & _
           "Aktualni FullName:" & vbCrLf & ThisWorkbook.FullName, vbCritical
End Sub

' Called by OnTime - silent refresh only when folder content changed
Public Sub CheckFolderAndRefresh()
    On Error GoTo ScheduleNext

    Dim srcPath As String
    srcPath = SourcePath()
    If Len(srcPath) = 0 Then GoTo ScheduleNext

    Dim sig As String
    sig = FolderFingerprint(srcPath)
    If Len(sig) > 0 And sig <> GetStoredSig() Then
        Application.StatusBar = "Mereni: novy/zmeneny soubor - slouci..."
        Call SloucitCore(True)
    End If

ScheduleNext:
    On Error Resume Next
    gNextPoll = Now + TimeSerial(0, 0, POLL_SECONDS)
    Application.OnTime EarliestTime:=gNextPoll, Procedure:=QualifiedMacro("CheckFolderAndRefresh"), Schedule:=True
    On Error GoTo 0
End Sub

' Manual button - with MsgBox
Public Sub SloucitVsechnaMereni()
    Call StopFolderWatch
    Call SloucitCore(False)
    Call StartFolderWatch
End Sub

' ========== core merge ==========
Private Sub SloucitCore(ByVal silent As Boolean)
    If gBusy Then Exit Sub
    gBusy = True

    On Error GoTo Fail

    Dim srcPath As String
    srcPath = SourcePath()
    If Len(srcPath) = 0 Then
        Call ExplainBadPath(silent)
        gBusy = False
        Exit Sub
    End If

    If Not FolderExistsLocal(SourceFolder()) Then
        If Not silent Then MsgBox "Slozka nenalezena:" & vbCrLf & SourceFolder(), vbCritical
        gBusy = False
        Exit Sub
    End If

    Dim wbOut As Workbook
    Set wbOut = ThisWorkbook

    Dim wsOut As Worksheet
    Set wsOut = EnsureSheet(wbOut, "Mereni")
    wsOut.Cells.Clear
    wsOut.Columns("A").ColumnWidth = 32
    wsOut.Columns("B").ColumnWidth = 18
    wsOut.Columns("C").ColumnWidth = 14
    wsOut.Columns("D").ColumnWidth = 36

    Dim outRow As Long
    outRow = 1

    Application.ScreenUpdating = False
    Application.DisplayAlerts = False
    Application.StatusBar = "Mereni: slouci..."

    Dim files As Collection
    Set files = ListSortedFiles(srcPath, FILE_PATTERN)

    Dim filesProcessed As Long
    Dim filesSkipped As Long
    Dim i As Long
    Dim fileName As String
    Dim wbIn As Workbook
    Dim wsIn As Worksheet
    Dim ok As Boolean
    Dim lastRow As Long
    Dim r As Long
    Dim a As String
    Dim b As String
    Dim c As String
    Dim d As String
    Dim fillColor As Long
    Dim role As String
    Dim currentDate As String
    Dim lastStation As String
    Dim guessed As String
    Dim wrote As Boolean

    filesProcessed = 0
    filesSkipped = 0

    For i = 1 To files.Count
        fileName = CStr(files(i))
        ok = True
        Set wbIn = Nothing
        On Error Resume Next
        Set wbIn = Workbooks.Open(Filename:=srcPath & fileName, ReadOnly:=True, UpdateLinks:=0)
        If wbIn Is Nothing Then ok = False
        On Error GoTo 0

        If Not ok Then
            filesSkipped = filesSkipped + 1
            GoTo NextFile
        End If

        If StrComp(wbIn.FullName, wbOut.FullName, vbTextCompare) = 0 Then
            wbIn.Close SaveChanges:=False
            GoTo NextFile
        End If

        Set wsIn = FindMereniSheet(wbIn)
        lastRow = wsIn.Cells(wsIn.Rows.Count, "A").End(xlUp).Row
        currentDate = ""
        lastStation = ""
        wrote = False

        For r = 1 To lastRow
            a = CellText(wsIn.Cells(r, 1))
            b = CellText(wsIn.Cells(r, 2))
            c = CellText(wsIn.Cells(r, 3))
            d = CellText(wsIn.Cells(r, 4))

            If a = "" And b = "" And c = "" And d = "" Then GoTo NextSrcRow

            fillColor = -1
            On Error Resume Next
            If wsIn.Cells(r, 1).Interior.Pattern <> xlNone Then
                fillColor = wsIn.Cells(r, 1).Interior.Color
            End If
            On Error GoTo 0

            role = DetectRole(a, b, c, d, fillColor, currentDate, COLOR_DATE, COLOR_STATION)

            If role = "DATE" Then
                If a <> currentDate Then
                    If outRow > 1 Then outRow = outRow + 1
                    Call WriteDateRow(wsOut, outRow, a, COLOR_DATE)
                    outRow = outRow + 1
                    currentDate = a
                    lastStation = ""
                    wrote = True
                End If

            ElseIf role = "STATION" Then
                If currentDate = "" Then
                    guessed = DateFromFileName(fileName)
                    If guessed <> "" Then
                        If outRow > 1 Then outRow = outRow + 1
                        Call WriteDateRow(wsOut, outRow, guessed, COLOR_DATE)
                        outRow = outRow + 1
                        currentDate = guessed
                    End If
                End If
                If a <> lastStation Then
                    outRow = outRow + 1
                    Call WriteStationRow(wsOut, outRow, a, COLOR_STATION)
                    outRow = outRow + 1
                    lastStation = a
                    wrote = True
                End If

            Else
                If currentDate = "" Then
                    guessed = DateFromFileName(fileName)
                    If guessed <> "" Then
                        If outRow > 1 Then outRow = outRow + 1
                        Call WriteDateRow(wsOut, outRow, guessed, COLOR_DATE)
                        outRow = outRow + 1
                        currentDate = guessed
                    End If
                End If
                Call WriteDataRow(wsOut, outRow, a, b, c, d)
                outRow = outRow + 1
                wrote = True
            End If
NextSrcRow:
        Next r

        wbIn.Close SaveChanges:=False
        If wrote Then
            filesProcessed = filesProcessed + 1
        Else
            filesSkipped = filesSkipped + 1
        End If
NextFile:
    Next i

    Call SetStoredSig(FolderFingerprint(srcPath))

    On Error Resume Next
    wbOut.Save
    On Error GoTo 0

    Application.StatusBar = "Mereni: OK (" & filesProcessed & " souboru)"
    Application.DisplayAlerts = True
    Application.ScreenUpdating = True

    If Not silent Then
        On Error Resume Next
        wsOut.Activate
        On Error GoTo 0
        MsgBox "Hotovo." & vbCrLf & _
               "Zdroju OK: " & filesProcessed & vbCrLf & _
               "Preskoceno: " & filesSkipped & vbCrLf & _
               "Soubor: " & wbOut.FullName, vbInformation
    End If

    gBusy = False
    Exit Sub

Fail:
    Application.DisplayAlerts = True
    Application.ScreenUpdating = True
    Application.StatusBar = False
    gBusy = False
    If Not silent Then
        MsgBox "Chyba " & Err.Number & ": " & Err.Description & vbCrLf & vbCrLf & _
               "FullName: " & ThisWorkbook.FullName & vbCrLf & _
               "Pokud je to https://... otevri soubor z Exploreru (lokalni OneDrive).", vbCritical
    End If
End Sub

Private Function FolderFingerprint(ByVal srcPath As String) As String
    Dim name As String
    Dim sig As String
    Dim full As String
    Dim fd As Variant
    Dim fl As Variant
    sig = ""
    name = SafeDir(srcPath & FILE_PATTERN)
    Do While name <> ""
        If StrComp(name, "Souhrn_mereni.xlsx", vbTextCompare) <> 0 And _
           StrComp(name, "Souhrn_mereni.xlsm", vbTextCompare) <> 0 Then
            full = srcPath & name
            fd = ""
            fl = ""
            On Error Resume Next
            fd = FileDateTime(full)
            fl = FileLen(full)
            On Error GoTo 0
            sig = sig & name & "|" & CStr(fd) & "|" & CStr(fl) & ";"
        End If
        name = SafeDirNext()
    Loop
    FolderFingerprint = sig
End Function

Private Function GetStoredSig() As String
    On Error Resume Next
    GetStoredSig = CStr(ThisWorkbook.Names(SIG_NAME).RefersToRange.Value)
    If Err.Number <> 0 Then
        Err.Clear
        GetStoredSig = ""
    End If
    On Error GoTo 0
End Function

Private Sub SetStoredSig(ByVal sig As String)
    Dim ws As Worksheet
    Set ws = EnsureSheet(ThisWorkbook, "Start")
    On Error Resume Next
    ThisWorkbook.Names(SIG_NAME).Delete
    On Error GoTo 0
    ' store on Start!IV1 (far cell) + named range
    ws.Range("IV1").Value = sig
    ThisWorkbook.Names.Add Name:=SIG_NAME, RefersTo:=ws.Range("IV1")
End Sub

Private Function EnsureSheet(ByVal wb As Workbook, ByVal sheetName As String) As Worksheet
    Dim ws As Worksheet
    On Error Resume Next
    Set ws = wb.Worksheets(sheetName)
    On Error GoTo 0
    If ws Is Nothing Then
        Set ws = wb.Worksheets.Add(After:=wb.Sheets(wb.Sheets.Count))
        On Error Resume Next
        ws.Name = sheetName
        On Error GoTo 0
    End If
    Set EnsureSheet = ws
End Function

Private Function ListSortedFiles(ByVal srcPath As String, ByVal pattern As String) As Collection
    Dim col As Collection
    Dim name As String
    Dim i As Long
    Dim j As Long
    Dim tmp As String
    Dim arr() As String
    Dim n As Long

    Set col = New Collection
    n = 0
    name = SafeDir(srcPath & pattern)
    Do While name <> ""
        If StrComp(name, "Souhrn_mereni.xlsx", vbTextCompare) <> 0 And _
           StrComp(name, "Souhrn_mereni.xlsm", vbTextCompare) <> 0 Then
            n = n + 1
            ReDim Preserve arr(1 To n)
            arr(n) = name
        End If
        name = SafeDirNext()
    Loop

    If n = 0 Then
        Set ListSortedFiles = col
        Exit Function
    End If

    For i = 1 To n - 1
        For j = i + 1 To n
            If StrComp(arr(i), arr(j), vbTextCompare) > 0 Then
                tmp = arr(i)
                arr(i) = arr(j)
                arr(j) = tmp
            End If
        Next j
    Next i

    For i = 1 To n
        col.Add arr(i)
    Next i
    Set ListSortedFiles = col
End Function

Private Function FindMereniSheet(ByVal wb As Workbook) As Worksheet
    Dim ws As Worksheet
    For Each ws In wb.Worksheets
        If StrComp(ws.Name, "Mereni", vbTextCompare) = 0 Then
            Set FindMereniSheet = ws
            Exit Function
        End If
    Next ws
    Set FindMereniSheet = wb.Worksheets(1)
End Function

Private Sub WriteDateRow(ByVal ws As Worksheet, ByVal row As Long, ByVal txt As String, ByVal fillColor As Long)
    With ws.Cells(row, 1)
        .Value = txt
        .Font.Bold = True
        .Font.Size = 16
        .HorizontalAlignment = xlLeft
        .VerticalAlignment = xlCenter
        .Interior.Color = fillColor
    End With
    ws.Rows(row).RowHeight = 24
End Sub

Private Sub WriteStationRow(ByVal ws As Worksheet, ByVal row As Long, ByVal txt As String, ByVal fillColor As Long)
    With ws.Cells(row, 1)
        .Value = txt
        .Font.Bold = True
        .Font.Size = 16
        .HorizontalAlignment = xlLeft
        .VerticalAlignment = xlCenter
        .Interior.Color = fillColor
    End With
    ws.Rows(row).RowHeight = 24
End Sub

Private Sub WriteDataRow(ByVal ws As Worksheet, ByVal row As Long, ByVal a As String, ByVal b As String, ByVal c As String, ByVal d As String)
    ws.Cells(row, 1).Value = a
    ws.Cells(row, 2).Value = b
    ws.Cells(row, 3).Value = c
    ws.Cells(row, 4).Value = d
    With ws.Range(ws.Cells(row, 1), ws.Cells(row, 4))
        .HorizontalAlignment = xlCenter
        .VerticalAlignment = xlCenter
        .Font.Size = 14
    End With
    ws.Rows(row).RowHeight = 24
End Sub

Private Function DateFromFileName(ByVal fileName As String) As String
    Dim y As Integer
    Dim m As Integer
    Dim d As Integer

    If Len(fileName) < 10 Then
        DateFromFileName = ""
        Exit Function
    End If
    If Not Left$(fileName, 6) Like "######" Then
        DateFromFileName = ""
        Exit Function
    End If
    If InStr(1, fileName, "_MD1", vbTextCompare) = 0 Then
        DateFromFileName = ""
        Exit Function
    End If

    y = CInt(Left$(fileName, 2))
    m = CInt(Mid$(fileName, 3, 2))
    d = CInt(Mid$(fileName, 5, 2))
    If m < 1 Or m > 12 Or d < 1 Or d > 31 Then
        DateFromFileName = ""
        Exit Function
    End If
    DateFromFileName = CStr(d) & "." & CStr(m) & "." & CStr(2000 + y)
End Function

Private Function CellText(ByVal cell As Range) As String
    Dim v As Variant
    v = cell.Value
    If IsError(v) Or IsEmpty(v) Then
        CellText = ""
    Else
        CellText = Trim$(CStr(v))
    End If
End Function

Private Function DetectRole( _
    ByVal a As String, _
    ByVal b As String, _
    ByVal c As String, _
    ByVal d As String, _
    ByVal fillColor As Long, _
    ByVal currentDate As String, _
    ByVal colorDate As Long, _
    ByVal colorStation As Long _
) As String
    If fillColor = colorDate Then
        DetectRole = "DATE"
        Exit Function
    End If
    If fillColor = colorStation Then
        DetectRole = "STATION"
        Exit Function
    End If
    If a <> "" And b = "" And c = "" And d = "" Then
        If currentDate = "" Or LooksLikeDate(a) Then
            DetectRole = "DATE"
        Else
            DetectRole = "STATION"
        End If
        Exit Function
    End If
    DetectRole = "DATA"
End Function

Private Function LooksLikeDate(ByVal s As String) As Boolean
    LooksLikeDate = (s Like "*.*.####") Or (s Like "*/*.*/####")
End Function
