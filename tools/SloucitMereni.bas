Attribute VB_Name = "SloucitMereni"
Option Explicit

' Slouci vsechny *_MD1.xlsx do jednoho sesitu se STEJNYM vzhledem jako appka:
'
'   10.8.2026                          <- datum (modre, jen A)
'                                      <- prazdny radek
'   Nazev stanice                      <- stanice (oranzove, jen A)
'   koleje | vyhybky | cas | poznamka  <- data (4 sloupce A-D, na stred)
'                                      <- prazdny radek
'   Dalsi stanice
'   ...
'
' Neni to plocha tabulka Datum|Stanice|... — bloky jsou "vyse" jako v appce.
'
' Nemecky Excel: Alt+F11 -> Datei -> Datei importieren... -> tento .bas
' Uprav SOURCE_FOLDER, Alt+F8 -> SloucitVsechnaMereni -> Ausfuhren

Public Sub SloucitVsechnaMereni()
    Const SOURCE_FOLDER As String = "C:\Users\hrubesk\OneDrive - Správa železnic\MD1_rozdeleno"
    Const FILE_PATTERN As String = "*_MD1.xlsx"

    Const COLOR_DATE As Long = 16506555      ' #BBDEFB
    Const COLOR_STATION As Long = 11723007   ' #FFE0B2

    Dim srcPath As String
    srcPath = SOURCE_FOLDER
    If Right$(srcPath, 1) <> "\" Then srcPath = srcPath & "\"

    If Dir(srcPath, vbDirectory) = "" Then
        MsgBox "Slozka nenalezena:" & vbCrLf & srcPath & vbCrLf & _
               "Uprav konstantu SOURCE_FOLDER v kodu makra.", vbCritical
        Exit Sub
    End If

    Dim wbOut As Workbook
    Dim wsOut As Worksheet
    Set wbOut = Workbooks.Add
    Set wsOut = wbOut.Sheets(1)
    wsOut.Name = "Mereni"

    ' Stejne sirky sloupcu jako SimpleXlsx v appce
    wsOut.Columns("A").ColumnWidth = 32
    wsOut.Columns("B").ColumnWidth = 18
    wsOut.Columns("C").ColumnWidth = 14
    wsOut.Columns("D").ColumnWidth = 36

    Dim outRow As Long
    outRow = 1

    Application.ScreenUpdating = False
    Application.DisplayAlerts = False

    Dim fileNames() As String
    Dim fileCount As Long
    fileCount = CollectSortedFiles(srcPath, FILE_PATTERN, fileNames)

    Dim filesProcessed As Long
    Dim filesSkipped As Long
    Dim i As Long
    filesProcessed = 0
    filesSkipped = 0

    For i = 1 To fileCount
        Dim wbIn As Workbook
        Dim ok As Boolean
        ok = True
        Set wbIn = Nothing
        On Error Resume Next
        Set wbIn = Workbooks.Open( _
            fileName:=srcPath & fileNames(i), _
            ReadOnly:=True, _
            UpdateLinks:=0, _
            IgnoreReadOnlyRecommended:=True)
        If wbIn Is Nothing Then ok = False
        On Error GoTo 0

        If Not ok Then
            filesSkipped = filesSkipped + 1
        Else
            Dim wsIn As Worksheet
            Set wsIn = FindMereniSheet(wbIn)

            Dim lastRow As Long
            lastRow = wsIn.Cells(wsIn.Rows.Count, "A").End(xlUp).Row

            Dim currentDate As String
            Dim lastWrittenStation As String
            Dim wroteSomethingFromFile As Boolean
            currentDate = ""
            lastWrittenStation = ""
            wroteSomethingFromFile = False

            Dim r As Long
            Dim a As String, b As String, c As String, d As String
            Dim fillColor As Long
            Dim role As String

            For r = 1 To lastRow
                a = CellText(wsIn.Cells(r, 1))
                b = CellText(wsIn.Cells(r, 2))
                c = CellText(wsIn.Cells(r, 3))
                d = CellText(wsIn.Cells(r, 4))

                If a = "" And b = "" And c = "" And d = "" Then
                    GoTo NextSourceRow
                End If

                fillColor = -1
                On Error Resume Next
                If wsIn.Cells(r, 1).Interior.Pattern <> xlNone Then
                    fillColor = wsIn.Cells(r, 1).Interior.Color
                End If
                On Error GoTo 0

                role = DetectRole(a, b, c, d, fillColor, currentDate, COLOR_DATE, COLOR_STATION)

                Select Case role
                    Case "DATE"
                        ' Novy den / novy soubor — datum "nahore" jako v appce
                        If a <> currentDate Then
                            If outRow > 1 Then
                                outRow = outRow + 1 ' oddelovac mezi dny
                            End If
                            WriteDateRow wsOut, outRow, a, COLOR_DATE
                            outRow = outRow + 1
                            currentDate = a
                            lastWrittenStation = ""
                            wroteSomethingFromFile = True
                        End If

                    Case "STATION"
                        If currentDate = "" Then
                            ' Soubor bez data — dopln z nazvu YYMMDD_MD1.xlsx pokud jde
                            guessed = DateFromFileName(fileNames(i))
                            If guessed <> "" Then
                                If outRow > 1 Then outRow = outRow + 1
                                WriteDateRow wsOut, outRow, guessed, COLOR_DATE
                                outRow = outRow + 1
                                currentDate = guessed
                            End If
                        End If
                        If a <> lastWrittenStation Then
                            outRow = outRow + 1 ' prazdny radek pred stanici
                            WriteStationRow wsOut, outRow, a, COLOR_STATION
                            outRow = outRow + 1
                            lastWrittenStation = a
                            wroteSomethingFromFile = True
                        End If

                    Case Else
                        ' DATA — 4 sloupce jako appka (bez Datum/Stanice ve sloupcich)
                        If currentDate = "" Then
                            guessed = DateFromFileName(fileNames(i))
                            If guessed <> "" Then
                                If outRow > 1 Then outRow = outRow + 1
                                WriteDateRow wsOut, outRow, guessed, COLOR_DATE
                                outRow = outRow + 1
                                currentDate = guessed
                            End If
                        End If
                        WriteDataRow wsOut, outRow, a, b, c, d
                        outRow = outRow + 1
                        wroteSomethingFromFile = True
                End Select
NextSourceRow:
            Next r

            wbIn.Close SaveChanges:=False
            If wroteSomethingFromFile Then
                filesProcessed = filesProcessed + 1
            Else
                filesSkipped = filesSkipped + 1
            End If
        End If
    Next i

    Application.DisplayAlerts = True
    Application.ScreenUpdating = True

    Dim outPath As String
    outPath = srcPath & "Souhrn_mereni.xlsx"
    On Error Resume Next
    Kill outPath
    On Error GoTo 0
    wbOut.SaveAs fileName:=outPath, FileFormat:=xlOpenXMLWorkbook

    MsgBox "Hotovo." & vbCrLf & _
           "Zpracovano souboru: " & filesProcessed & vbCrLf & _
           "Preskoceno: " & filesSkipped & vbCrLf & _
           "Ulozeno: " & outPath & vbCrLf & vbCrLf & _
           "Vzhled = appka: datum / stanice vyse, data A-D pod nimi.", vbInformation
End Sub

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

Private Sub WriteDataRow(ByVal ws As Worksheet, ByVal row As Long, _
    ByVal a As String, ByVal b As String, ByVal c As String, ByVal d As String)
    Dim col As Long
    Dim vals As Variant
    vals = Array(a, b, c, d)
    For col = 1 To 4
        With ws.Cells(row, col)
            .Value = vals(col - 1)
            .HorizontalAlignment = xlCenter
            .VerticalAlignment = xlCenter
            .Font.Size = 14
        End With
    Next col
    ws.Rows(row).RowHeight = 24
End Sub

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

Private Function CollectSortedFiles( _
    ByVal srcPath As String, _
    ByVal pattern As String, _
    ByRef fileNames() As String _
) As Long
    Dim name As String
    Dim n As Long
    Dim arr() As String
    n = 0
    name = Dir(srcPath & pattern)
    Do While name <> ""
        ' nebrat pripadny souhrn, kdyby nazev sedel na pattern
        If StrComp(name, "Souhrn_mereni.xlsx", vbTextCompare) <> 0 Then
            n = n + 1
            ReDim Preserve arr(1 To n)
            arr(n) = name
        End If
        name = Dir()
    Loop
    If n = 0 Then
        CollectSortedFiles = 0
        Exit Function
    End If
    BubbleSortStrings arr
    fileNames = arr
    CollectSortedFiles = n
End Function

Private Sub BubbleSortStrings(ByRef arr() As String)
    Dim i As Long, j As Long
    Dim tmp As String
    For i = LBound(arr) To UBound(arr) - 1
        For j = i + 1 To UBound(arr)
            If StrComp(arr(i), arr(j), vbTextCompare) > 0 Then
                tmp = arr(i)
                arr(i) = arr(j)
                arr(j) = tmp
            End If
        Next j
    Next i
End Sub

Private Function DateFromFileName(ByVal fileName As String) As String
    ' YYMMDD_MD1.xlsx -> d.M.yyyy
    Dim base As String
    Dim y As Integer, m As Integer, d As Integer
    base = fileName
    If InStr(1, base, "_MD1", vbTextCompare) <= 6 Then
        DateFromFileName = ""
        Exit Function
    End If
    If Not Left$(base, 6) Like "######" Then
        DateFromFileName = ""
        Exit Function
    End If
    y = CInt(Left$(base, 2))
    m = CInt(Mid$(base, 3, 2))
    d = CInt(Mid$(base, 5, 2))
    If m < 1 Or m > 12 Or d < 1 Or d > 31 Then
        DateFromFileName = ""
        Exit Function
    End If
    DateFromFileName = CStr(d) & "." & CStr(m) & "." & CStr(2000 + y)
End Function

Private Function CellText(ByVal cell As Range) As String
    Dim v As Variant
    v = cell.Value
    If IsError(v) Then
        CellText = ""
    ElseIf IsEmpty(v) Then
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

    Dim onlyA As Boolean
    onlyA = (a <> "" And b = "" And c = "" And d = "")

    If onlyA Then
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
