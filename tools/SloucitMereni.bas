Attribute VB_Name = "SloucitMereni"
Option Explicit

' Souhrn_mereni.xlsm ve stejne slozce jako *_MD1.xlsx
' Alt+F8 -> Mereni_Nastavit (jednou) -> pak jen tlacitko Sloucit

Private Const COLOR_DATE As Long = 16506555
Private Const COLOR_STATION As Long = 11723007

Private gBusy As Boolean

Public Sub Mereni_Nastavit()
    Dim wb As Workbook
    Dim savePath As Variant
    Dim ws As Worksheet
    Dim shp As Shape
    Dim btn As Button

    Set wb = ThisWorkbook

    If Len(wb.Path) = 0 Or LCase$(Right$(wb.Name, 5)) <> ".xlsm" Then
        savePath = Application.GetSaveAsFilename( _
            InitialFileName:="Souhrn_mereni.xlsm", _
            FileFilter:="Excel makro (*.xlsm), *.xlsm", _
            Title:="Ulozit do slozky s *_MD1.xlsx")
        If savePath = False Then Exit Sub
        If LCase$(Right$(CStr(savePath), 5)) <> ".xlsm" Then savePath = CStr(savePath) & ".xlsm"
        Application.DisplayAlerts = False
        On Error Resume Next
        wb.SaveAs Filename:=CStr(savePath), FileFormat:=52
        If Err.Number <> 0 Then
            Application.DisplayAlerts = True
            MsgBox Err.Description, vbCritical
            Exit Sub
        End If
        On Error GoTo 0
        Application.DisplayAlerts = True
    End If

    Set ws = EnsureSheet(wb, "Start")
    On Error Resume Next
    ws.Move Before:=wb.Sheets(1)
    On Error GoTo 0

    ws.Cells.Clear
    ws.Range("A1").Value = "Mereni"
    ws.Range("A1").Font.Size = 20
    ws.Range("A1").Font.Bold = True
    ws.Range("A3").Value = wb.Path
    ws.Columns("A").ColumnWidth = 80

    On Error Resume Next
    For Each shp In ws.Shapes
        shp.Delete
    Next shp
    On Error GoTo 0

    Set btn = ws.Buttons.Add(20, 80, 220, 48)
    btn.OnAction = "'" & Replace(wb.Name, "'", "''") & "'!Sloucit"
    btn.Characters.Text = "Sloucit"
    btn.Font.Size = 16
    btn.Font.Bold = True

    Call EnsureSheet(wb, "Mereni")
    wb.Save
End Sub

Public Sub VytvoritTlacitko()
    Call Mereni_Nastavit
End Sub

Public Sub Sloucit()
    Call SloucitVsechnaMereni
End Sub

Public Sub SloucitVsechnaMereni()
    Dim src As String
    Dim wbOut As Workbook
    Dim wsOut As Worksheet
    Dim files As Collection
    Dim i As Long
    Dim fileName As String
    Dim wbIn As Workbook
    Dim wsIn As Worksheet
    Dim lastRow As Long
    Dim r As Long
    Dim a As String, b As String, c As String, d As String
    Dim fill As Long
    Dim role As String
    Dim curDate As String
    Dim curStation As String
    Dim guessed As String
    Dim outRow As Long
    Dim nOk As Long
    Dim nSkip As Long

    If gBusy Then Exit Sub
    gBusy = True
    On Error GoTo Fail

    src = LocalFolder()
    If Len(src) = 0 Then
        MsgBox "Otevri soubor z Pruzkumnika (C:\...), ne z https://", vbExclamation
        gBusy = False
        Exit Sub
    End If
    If Not Fso().FolderExists(src) Then
        MsgBox "Slozka neexistuje:" & vbCrLf & src, vbCritical
        gBusy = False
        Exit Sub
    End If

    Set wbOut = ThisWorkbook
    Set wsOut = EnsureSheet(wbOut, "Mereni")
    wsOut.Cells.Clear
    wsOut.Columns("A").ColumnWidth = 32
    wsOut.Columns("B").ColumnWidth = 18
    wsOut.Columns("C").ColumnWidth = 14
    wsOut.Columns("D").ColumnWidth = 36
    outRow = 1

    Application.ScreenUpdating = False
    Application.DisplayAlerts = False

    Set files = ListMd1(src)
    nOk = 0
    nSkip = 0

    For i = 1 To files.Count
        fileName = CStr(files(i))
        Set wbIn = Nothing
        On Error Resume Next
        Set wbIn = Workbooks.Open(Filename:=src & "\" & fileName, ReadOnly:=True, UpdateLinks:=0)
        On Error GoTo Fail
        If wbIn Is Nothing Then
            nSkip = nSkip + 1
            GoTo NextFile
        End If
        If StrComp(wbIn.FullName, wbOut.FullName, vbTextCompare) = 0 Then
            wbIn.Close SaveChanges:=False
            GoTo NextFile
        End If

        Set wsIn = FindMereniSheet(wbIn)
        lastRow = wsIn.Cells(wsIn.Rows.Count, "A").End(xlUp).Row
        curDate = ""
        curStation = ""

        For r = 1 To lastRow
            a = CellText(wsIn.Cells(r, 1))
            b = CellText(wsIn.Cells(r, 2))
            c = CellText(wsIn.Cells(r, 3))
            d = CellText(wsIn.Cells(r, 4))
            If a = "" And b = "" And c = "" And d = "" Then GoTo NextRow

            fill = -1
            On Error Resume Next
            If wsIn.Cells(r, 1).Interior.Pattern <> xlNone Then fill = wsIn.Cells(r, 1).Interior.Color
            On Error GoTo Fail

            role = DetectRole(a, b, c, d, fill, curDate)

            If role = "DATE" Then
                If a <> curDate Then
                    If outRow > 1 Then outRow = outRow + 1
                    Call WriteDate(wsOut, outRow, a)
                    outRow = outRow + 1
                    curDate = a
                    curStation = ""
                End If
            ElseIf role = "STATION" Then
                If curDate = "" Then
                    guessed = DateFromFileName(fileName)
                    If guessed <> "" Then
                        If outRow > 1 Then outRow = outRow + 1
                        Call WriteDate(wsOut, outRow, guessed)
                        outRow = outRow + 1
                        curDate = guessed
                    End If
                End If
                If a <> curStation Then
                    outRow = outRow + 1
                    Call WriteStation(wsOut, outRow, a)
                    outRow = outRow + 1
                    curStation = a
                End If
            Else
                If curDate = "" Then
                    guessed = DateFromFileName(fileName)
                    If guessed <> "" Then
                        If outRow > 1 Then outRow = outRow + 1
                        Call WriteDate(wsOut, outRow, guessed)
                        outRow = outRow + 1
                        curDate = guessed
                    End If
                End If
                Call WriteData(wsOut, outRow, a, b, c, d)
                outRow = outRow + 1
            End If
NextRow:
        Next r

        wbIn.Close SaveChanges:=False
        nOk = nOk + 1
NextFile:
    Next i

    On Error Resume Next
    wbOut.Save
    On Error GoTo 0
    Application.DisplayAlerts = True
    Application.ScreenUpdating = True
    wsOut.Activate
    MsgBox "Hotovo. Souboru: " & nOk & "  Preskoceno: " & nSkip, vbInformation
    gBusy = False
    Exit Sub

Fail:
    Application.DisplayAlerts = True
    Application.ScreenUpdating = True
    gBusy = False
    MsgBox "Chyba " & Err.Number & ": " & Err.Description, vbCritical
End Sub

Private Function LocalFolder() As String
    Dim p As String
    p = ThisWorkbook.Path
    If Len(p) = 0 Then Exit Function
    If InStr(1, LCase$(p), "http", vbTextCompare) > 0 Then Exit Function
    If InStr(1, LCase$(ThisWorkbook.FullName), "http", vbTextCompare) > 0 Then Exit Function
    p = Replace(p, "/", "\")
    Do While Right$(p, 1) = "\"
        p = Left$(p, Len(p) - 1)
    Loop
    LocalFolder = p
End Function

Private Function Fso() As Object
    Static o As Object
    If o Is Nothing Then Set o = CreateObject("Scripting.FileSystemObject")
    Set Fso = o
End Function

Private Function IsMd1(ByVal name As String) As Boolean
    Dim n As String
    n = LCase$(name)
    If n = "souhrn_mereni.xlsx" Or n = "souhrn_mereni.xlsm" Then Exit Function
    IsMd1 = (Len(n) >= 9) And (Right$(n, 9) = "_md1.xlsx")
End Function

Private Function ListMd1(ByVal folder As String) As Collection
    Dim col As Collection
    Dim arr() As String
    Dim n As Long, i As Long, j As Long
    Dim tmp As String
    Dim f As Object

    Set col = New Collection
    n = 0
    On Error Resume Next
    For Each f In Fso().GetFolder(folder).Files
        If IsMd1(CStr(f.Name)) Then
            n = n + 1
            ReDim Preserve arr(1 To n)
            arr(n) = CStr(f.Name)
        End If
    Next f
    On Error GoTo 0

    If n = 0 Then
        Set ListMd1 = col
        Exit Function
    End If

    For i = 1 To n - 1
        For j = i + 1 To n
            If StrComp(arr(i), arr(j), vbTextCompare) > 0 Then
                tmp = arr(i): arr(i) = arr(j): arr(j) = tmp
            End If
        Next j
    Next i
    For i = 1 To n
        col.Add arr(i)
    Next i
    Set ListMd1 = col
End Function

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

Private Sub WriteDate(ByVal ws As Worksheet, ByVal row As Long, ByVal txt As String)
    With ws.Cells(row, 1)
        .Value = txt
        .Font.Bold = True
        .Font.Size = 16
        .HorizontalAlignment = xlLeft
        .Interior.Color = COLOR_DATE
    End With
    ws.Rows(row).RowHeight = 24
End Sub

Private Sub WriteStation(ByVal ws As Worksheet, ByVal row As Long, ByVal txt As String)
    With ws.Cells(row, 1)
        .Value = txt
        .Font.Bold = True
        .Font.Size = 16
        .HorizontalAlignment = xlLeft
        .Interior.Color = COLOR_STATION
    End With
    ws.Rows(row).RowHeight = 24
End Sub

Private Sub WriteData(ByVal ws As Worksheet, ByVal row As Long, ByVal a As String, ByVal b As String, ByVal c As String, ByVal d As String)
    ws.Cells(row, 1).Value = a
    ws.Cells(row, 2).Value = b
    ws.Cells(row, 3).Value = c
    ws.Cells(row, 4).Value = d
    With ws.Range(ws.Cells(row, 1), ws.Cells(row, 4))
        .HorizontalAlignment = xlCenter
        .Font.Size = 14
    End With
    ws.Rows(row).RowHeight = 24
End Sub

Private Function DateFromFileName(ByVal fileName As String) As String
    Dim y As Integer, m As Integer, d As Integer
    If Len(fileName) < 10 Then Exit Function
    If Not Left$(fileName, 6) Like "######" Then Exit Function
    If InStr(1, fileName, "_MD1", vbTextCompare) = 0 Then Exit Function
    y = CInt(Left$(fileName, 2))
    m = CInt(Mid$(fileName, 3, 2))
    d = CInt(Mid$(fileName, 5, 2))
    If m < 1 Or m > 12 Or d < 1 Or d > 31 Then Exit Function
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

Private Function DetectRole(ByVal a As String, ByVal b As String, ByVal c As String, ByVal d As String, _
    ByVal fillColor As Long, ByVal currentDate As String) As String
    If fillColor = COLOR_DATE Then
        DetectRole = "DATE"
        Exit Function
    End If
    If fillColor = COLOR_STATION Then
        DetectRole = "STATION"
        Exit Function
    End If
    If a <> "" And b = "" And c = "" And d = "" Then
        If currentDate = "" Or a Like "*.*.####" Then
            DetectRole = "DATE"
        Else
            DetectRole = "STATION"
        End If
        Exit Function
    End If
    DetectRole = "DATA"
End Function
