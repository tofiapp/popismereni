Attribute VB_Name = "SloucitMereni"
Option Explicit

' ONE summary workbook in the OneDrive folder:
'   Souhrn_mereni.xlsm  (same file every run - overwritten, not a new Book)
'
' Layout of sheet "Mereni" = Android app:
'   date (blue) / station (orange) / data A-D
'
' Sources: YYMMDD_N_MD1.xlsx and mereni_MD1.xlsx
'
' Setup (German Excel), once:
'   1) Alt+F11 -> Datei -> Datei importieren... -> this .bas
'   2) Alt+F8 -> VytvoritTlacitko -> Ausfuhren
'      -> creates/opens Souhrn_mereni.xlsm with a big button
'   3) Later: just click the button (no Alt+F8)

Private Function SourceFolder() As String
    ' Path via ChrW = ASCII-safe .bas (Sprava zeleznic)
    SourceFolder = "C:\Users\hrubesk\OneDrive - Spr" & ChrW(225) & "va " & _
                   ChrW(382) & "eleznic\MD1_rozdeleno"
End Function

Private Function SourcePath() As String
    Dim p As String
    p = SourceFolder()
    If Right$(p, 1) <> "\" Then p = p & "\"
    SourcePath = p
End Function

Private Function SouhrnPath() As String
    SouhrnPath = SourcePath() & "Souhrn_mereni.xlsm"
End Function

' --- run once: make the button workbook ---
Public Sub VytvoritTlacitko()
    Dim src As String
    src = SourcePath()
    If Dir(src, vbDirectory) = "" Then
        MsgBox "Folder not found:" & vbCrLf & src, vbCritical
        Exit Sub
    End If

    Dim wb As Workbook
    Set wb = OpenOrCreateSouhrn()

    Dim wsBtn As Worksheet
    Set wsBtn = EnsureSheet(wb, "Start")
    wsBtn.Move Before:=wb.Sheets(1)

    wsBtn.Cells.Clear
    wsBtn.Range("A1").Value = "Mereni - slouceni OneDrive"
    wsBtn.Range("A1").Font.Size = 18
    wsBtn.Range("A1").Font.Bold = True
    wsBtn.Range("A3").Value = "Klikni tlacitko nize. Vysledek je list Mereni ve STEJNEM souboru."
    wsBtn.Range("A4").Value = "Soubor: " & SouhrnPath()
    wsBtn.Columns("A").ColumnWidth = 80

    ' remove old buttons
    Dim shp As Shape
    On Error Resume Next
    For Each shp In wsBtn.Shapes
        shp.Delete
    Next shp
    On Error GoTo 0

    Dim btn As Button
    Set btn = wsBtn.Buttons.Add(Left:=20, Top:=100, Width:=260, Height:=50)
    btn.OnAction = "'" & wb.Name & "'!SloucitVsechnaMereni"
    btn.Characters.Text = "Sloucit mereni"
    btn.Font.Size = 16
    btn.Font.Bold = True

    Call EnsureSheet(wb, "Mereni")
    Call SaveSouhrn(wb)

    MsgBox "Hotovo. Ulozene:" & vbCrLf & SouhrnPath() & vbCrLf & vbCrLf & _
           "Priste otevri tento soubor a klikni tlacitko.", vbInformation
End Sub

' --- main: refresh list Mereni inside the SAME Souhrn file ---
Public Sub SloucitVsechnaMereni()
    Const FILE_PATTERN As String = "*_MD1.xlsx"
    Const COLOR_DATE As Long = 16506555
    Const COLOR_STATION As Long = 11723007

    Dim srcPath As String
    srcPath = SourcePath()

    If Dir(srcPath, vbDirectory) = "" Then
        MsgBox "Folder not found:" & vbCrLf & srcPath & vbCrLf & _
               "Edit SourceFolder() in the macro.", vbCritical
        Exit Sub
    End If

    Dim wbOut As Workbook
    Dim wsOut As Worksheet
    Set wbOut = OpenOrCreateSouhrn()
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
    Application.StatusBar = "Sloucit mereni..."

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

    Call SaveSouhrn(wbOut)

    Application.StatusBar = False
    Application.DisplayAlerts = True
    Application.ScreenUpdating = True

    On Error Resume Next
    wbOut.Sheets("Mereni").Activate
    On Error GoTo 0

    MsgBox "Hotovo - jeden soubor Souhrn_mereni.xlsm." & vbCrLf & _
           "Zdroju OK: " & filesProcessed & vbCrLf & _
           "Preskoceno: " & filesSkipped & vbCrLf & _
           "Ulozeno: " & SouhrnPath(), vbInformation
End Sub

' Open existing Souhrn_mereni.xlsm / .xlsx, or create once.
Private Function OpenOrCreateSouhrn() As Workbook
    Dim pathXlsm As String
    Dim pathXlsx As String
    Dim wb As Workbook

    pathXlsm = SouhrnPath()
    pathXlsx = SourcePath() & "Souhrn_mereni.xlsx"

    Set wb = WorkbookByFullName(pathXlsm)
    If Not wb Is Nothing Then
        Set OpenOrCreateSouhrn = wb
        Exit Function
    End If

    Set wb = WorkbookByFullName(pathXlsx)
    If Not wb Is Nothing Then
        Set OpenOrCreateSouhrn = wb
        Exit Function
    End If

    If Dir(pathXlsm) <> "" Then
        Set OpenOrCreateSouhrn = Workbooks.Open(Filename:=pathXlsm, UpdateLinks:=0)
        Exit Function
    End If

    If Dir(pathXlsx) <> "" Then
        Set wb = Workbooks.Open(Filename:=pathXlsx, UpdateLinks:=0)
        ' migrate to xlsm once (macros + button need it)
        Application.DisplayAlerts = False
        On Error Resume Next
        wb.SaveAs Filename:=pathXlsm, FileFormat:=52
        On Error GoTo 0
        Application.DisplayAlerts = True
        Set OpenOrCreateSouhrn = wb
        Exit Function
    End If

    Set wb = Workbooks.Add
    Application.DisplayAlerts = False
    On Error Resume Next
    wb.SaveAs Filename:=pathXlsm, FileFormat:=52
    If Err.Number <> 0 Then
        Err.Clear
        ' OneDrive sometimes blocks xlsm -> fall back xlsx (button still works if macros in this wb)
        wb.SaveAs Filename:=pathXlsx, FileFormat:=51
    End If
    On Error GoTo 0
    Application.DisplayAlerts = True
    Set OpenOrCreateSouhrn = wb
End Function

Private Sub SaveSouhrn(ByVal wb As Workbook)
    Application.DisplayAlerts = False
    On Error Resume Next
    wb.Save
    If Err.Number <> 0 Then
        Err.Clear
        ' retry SaveAs to known path (OneDrive lock workaround)
        If LCase$(Right$(wb.FullName, 5)) = ".xlsm" Or InStr(1, wb.Name, ".xlsm", vbTextCompare) > 0 Then
            wb.SaveAs Filename:=SouhrnPath(), FileFormat:=52
        Else
            wb.SaveAs Filename:=SourcePath() & "Souhrn_mereni.xlsx", FileFormat:=51
        End If
    End If
    On Error GoTo 0
    Application.DisplayAlerts = True
End Sub

Private Function WorkbookByFullName(ByVal fullPath As String) As Workbook
    Dim wb As Workbook
    For Each wb In Application.Workbooks
        If StrComp(wb.FullName, fullPath, vbTextCompare) = 0 Then
            Set WorkbookByFullName = wb
            Exit Function
        End If
    Next wb
    Set WorkbookByFullName = Nothing
End Function

Private Function EnsureSheet(ByVal wb As Workbook, ByVal sheetName As String) As Worksheet
    Dim ws As Worksheet
    On Error Resume Next
    Set ws = wb.Worksheets(sheetName)
    On Error GoTo 0
    If ws Is Nothing Then
        Set ws = wb.Worksheets.Add(After:=wb.Sheets(wb.Sheets.Count))
        ws.Name = sheetName
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
    name = Dir(srcPath & pattern)
    Do While name <> ""
        If StrComp(name, "Souhrn_mereni.xlsx", vbTextCompare) <> 0 And _
           StrComp(name, "Souhrn_mereni.xlsm", vbTextCompare) <> 0 Then
            n = n + 1
            ReDim Preserve arr(1 To n)
            arr(n) = name
        End If
        name = Dir()
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
