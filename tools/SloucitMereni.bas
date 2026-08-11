Attribute VB_Name = "SloucitMereni"
Option Explicit

' LOKALNI soubor (Documents / plocha) - NE ukladat do OneDrive.
' OneDrive slozka = jen data *_MD1.xlsx + vystup Souhrn_mereni.xlsx (bez maker).
'
' 1) Novy sesit, import tohoto .bas
' 2) Uloz jako C:\Users\...\Documents\MereniSloucit.xlsm
' 3) Alt+F8 -> Nastavit
' 4) Tlacitko Sloucit

Private Const COLOR_DATE As Long = 16506555
Private Const COLOR_STATION As Long = 11723007
Private Const OUT_NAME As String = "Souhrn_mereni.xlsx"

Private gBusy As Boolean

Public Sub Nastavit()
    Dim wb As Workbook
    Dim ws As Worksheet
    Dim folder As String
    Dim shp As Shape
    Dim btn As Button

    Set wb = ThisWorkbook

    ' Musi byt lokalni .xlsm (ne OneDrive / https)
    If IsWebPath(wb.FullName) Or IsWebPath(wb.Path) Then
        MsgBox "Tento soubor je na OneDrive/https - Excel makra blokuje." & vbCrLf & _
               "Uloz makra lokalne: Soubor -> Ulozit jako -> Documents\MereniSloucit.xlsm", vbCritical
        Exit Sub
    End If

    If Len(wb.Path) = 0 Or LCase$(Right$(wb.Name, 5)) <> ".xlsm" Then
        Dim sp As Variant
        sp = Application.GetSaveAsFilename( _
            InitialFileName:=Environ$("USERPROFILE") & "\Documents\MereniSloucit.xlsm", _
            FileFilter:="Excel makro (*.xlsm), *.xlsm", _
            Title:="Ulozit LOKALNE (ne do OneDrive)")
        If sp = False Then Exit Sub
        If LCase$(Right$(CStr(sp), 5)) <> ".xlsm" Then sp = CStr(sp) & ".xlsm"
        If IsWebPath(CStr(sp)) Or InStr(1, LCase$(CStr(sp)), "onedrive", vbTextCompare) > 0 Then
            MsgBox "Vyber slozku mimo OneDrive (napr. Documents).", vbExclamation
            Exit Sub
        End If
        Application.DisplayAlerts = False
        On Error Resume Next
        wb.SaveAs Filename:=CStr(sp), FileFormat:=52
        If Err.Number <> 0 Then
            Application.DisplayAlerts = True
            MsgBox Err.Description, vbCritical
            Exit Sub
        End If
        On Error GoTo 0
        Application.DisplayAlerts = True
    End If

    folder = PickDataFolder(GetSavedFolder())
    If Len(folder) = 0 Then Exit Sub
    Call SaveFolder(folder)

    Set ws = EnsureSheet(wb, "Start")
    On Error Resume Next
    ws.Move Before:=wb.Sheets(1)
    For Each shp In ws.Shapes
        shp.Delete
    Next shp
    On Error GoTo 0

    ws.Cells.Clear
    ws.Range("A1").Value = "Mereni"
    ws.Range("A1").Font.Bold = True
    ws.Range("A1").Font.Size = 20
    ws.Range("A3").Value = "Data:"
    ws.Range("B3").Value = folder
    ws.Range("A4").Value = "Vystup:"
    ws.Range("B4").Value = folder & "\" & OUT_NAME
    ws.Columns("A").ColumnWidth = 12
    ws.Columns("B").ColumnWidth = 70

    Set btn = ws.Buttons.Add(20, 100, 200, 44)
    btn.OnAction = "'" & Replace(wb.Name, "'", "''") & "'!Sloucit"
    btn.Characters.Text = "Sloucit"
    btn.Font.Size = 16
    btn.Font.Bold = True

    wb.Save
End Sub

Public Sub Mereni_Nastavit()
    Call Nastavit
End Sub

Public Sub VytvoritTlacitko()
    Call Nastavit
End Sub

Public Sub Sloucit()
    Call SloucitVsechnaMereni
End Sub

Public Sub SloucitVsechnaMereni()
    Dim src As String
    Dim outPath As String
    Dim files As Collection
    Dim wbOut As Workbook
    Dim wsOut As Worksheet
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
    Dim createdOut As Boolean

    If gBusy Then Exit Sub
    gBusy = True
    On Error GoTo Fail

    src = GetSavedFolder()
    If Len(src) = 0 Or Not Fso().FolderExists(src) Then
        src = PickDataFolder("")
        If Len(src) = 0 Then
            gBusy = False
            Exit Sub
        End If
        Call SaveFolder(src)
    End If

    outPath = src & "\" & OUT_NAME
    Set files = ListMd1(src)

    createdOut = False
    Set wbOut = WorkbookByPath(outPath)
    If wbOut Is Nothing Then
        Set wbOut = Workbooks.Add(xlWBATWorksheet)
        createdOut = True
    End If

    Set wsOut = wbOut.Worksheets(1)
    On Error Resume Next
    wsOut.Name = "Mereni"
    On Error GoTo Fail
    wsOut.Cells.Clear
    wsOut.Columns("A").ColumnWidth = 32
    wsOut.Columns("B").ColumnWidth = 18
    wsOut.Columns("C").ColumnWidth = 14
    wsOut.Columns("D").ColumnWidth = 36
    outRow = 1
    nOk = 0
    nSkip = 0

    Application.ScreenUpdating = False
    Application.DisplayAlerts = False

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
    If createdOut Or StrComp(wbOut.FullName, outPath, vbTextCompare) <> 0 Then
        wbOut.SaveAs Filename:=outPath, FileFormat:=51
    Else
        wbOut.Save
    End If
    On Error GoTo 0

    Application.DisplayAlerts = True
    Application.ScreenUpdating = True
    MsgBox "Hotovo" & vbCrLf & outPath & vbCrLf & "Souboru: " & nOk, vbInformation
    gBusy = False
    Exit Sub

Fail:
    Application.DisplayAlerts = True
    Application.ScreenUpdating = True
    gBusy = False
    MsgBox "Chyba " & Err.Number & ": " & Err.Description, vbCritical
End Sub

Private Function PickDataFolder(ByVal initial As String) As String
    Dim fd As FileDialog
    Set fd = Application.FileDialog(msoFileDialogFolderPicker)
    fd.Title = "Slozka s *_MD1.xlsx (OneDrive sync)"
    If Len(initial) > 0 Then fd.InitialFileName = initial
    If fd.Show <> -1 Then
        PickDataFolder = ""
        Exit Function
    End If
    PickDataFolder = fd.SelectedItems(1)
End Function

Private Function GetSavedFolder() As String
    On Error Resume Next
    GetSavedFolder = CStr(ThisWorkbook.Names("DataFolder").RefersToRange.Value)
    If Err.Number <> 0 Then GetSavedFolder = ""
    On Error GoTo 0
End Function

Private Sub SaveFolder(ByVal folder As String)
    Dim ws As Worksheet
    Set ws = EnsureSheet(ThisWorkbook, "Start")
    ws.Range("Z1").Value = folder
    On Error Resume Next
    ThisWorkbook.Names("DataFolder").Delete
    On Error GoTo 0
    ThisWorkbook.Names.Add Name:="DataFolder", RefersTo:=ws.Range("Z1")
End Sub

Private Function WorkbookByPath(ByVal fullPath As String) As Workbook
    Dim wb As Workbook
    For Each wb In Application.Workbooks
        If StrComp(wb.FullName, fullPath, vbTextCompare) = 0 Then
            Set WorkbookByPath = wb
            Exit Function
        End If
    Next wb
    If Fso().FileExists(fullPath) Then
        On Error Resume Next
        Set WorkbookByPath = Workbooks.Open(Filename:=fullPath, UpdateLinks:=0)
        On Error GoTo 0
    End If
End Function

Private Function IsWebPath(ByVal p As String) As Boolean
    Dim t As String
    t = LCase$(Trim$(p))
    If Len(t) = 0 Then Exit Function
    IsWebPath = (InStr(t, "http://") > 0) Or (InStr(t, "https://") > 0) Or _
                (InStr(t, "://") > 0) Or (InStr(t, "sharepoint.com") > 0)
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
    If fillColor = COLOR_DATE Then DetectRole = "DATE": Exit Function
    If fillColor = COLOR_STATION Then DetectRole = "STATION": Exit Function
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
