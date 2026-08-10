Attribute VB_Name = "SloucitMereni"
Option Explicit

' Slouci vsechny soubory *_MD1.xlsx z lokalni OneDrive slozky do Souhrn_mereni.xlsx
' Sloupce = vzhled appky Mereni:
'   Datum | Stanice | Koleje | Vyhybky | Cas | Poznamka
'
' Jak nahrat (nemecky Excel):
'   Alt+F11 -> Datei -> Datei importieren... -> tento .bas
'   (NE Ctrl+V celeho souboru vcetne Attribute radku)
' Uprav SOURCE_FOLDER, pak Alt+F8 -> SloucitVsechnaMereni -> Ausfuhren
'
' Role radku (stejne jako appka):
'   modry A  = datum   (styl DATE, #BBDEFB)
'   oranzovy A = stanice (styl STATION, #FFE0B2)
'   ostatni neprazdne = data (Koleje, Vyhybky, Cas, Poznamka)

Public Sub SloucitVsechnaMereni()
    Const SOURCE_FOLDER As String = "C:\Users\hrubesk\OneDrive - Správa železnic\MD1_rozdeleno"
    Const FILE_PATTERN As String = "*_MD1.xlsx"

    ' Barvy vyplne z SimpleXlsx (RGB -> VBA Color = R + G*256 + B*65536)
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
    wsOut.Name = "Souhrn"

    ' Stejne poradi / vyznam jako v appce (pole1, pole2, cas, poznamka)
    wsOut.Range("A1").Value = "Datum"
    wsOut.Range("B1").Value = "Stanice"
    wsOut.Range("C1").Value = "Koleje"
    wsOut.Range("D1").Value = "Výhybky"
    wsOut.Range("E1").Value = "Čas"
    wsOut.Range("F1").Value = "Poznámka"
    wsOut.Range("A1:F1").Font.Bold = True

    Dim outRow As Long
    outRow = 2

    Application.ScreenUpdating = False
    Application.DisplayAlerts = False

    Dim fileName As String
    Dim filesProcessed As Long
    Dim filesSkipped As Long
    Dim rowsWritten As Long
    filesProcessed = 0
    filesSkipped = 0
    rowsWritten = 0

    fileName = Dir(srcPath & FILE_PATTERN)

    Do While fileName <> ""
        Dim wbIn As Workbook
        Dim ok As Boolean
        ok = True
        Set wbIn = Nothing
        On Error Resume Next
        Set wbIn = Workbooks.Open( _
            fileName:=srcPath & fileName, _
            ReadOnly:=True, _
            UpdateLinks:=0, _
            IgnoreReadOnlyRecommended:=True)
        If wbIn Is Nothing Then ok = False
        On Error GoTo 0

        If Not ok Then
            filesSkipped = filesSkipped + 1
        Else
            Dim wsIn As Worksheet
            Set wsIn = Nothing
            Dim ws As Worksheet
            For Each ws In wbIn.Worksheets
                If StrComp(ws.Name, "Mereni", vbTextCompare) = 0 Then
                    Set wsIn = ws
                    Exit For
                End If
            Next ws
            If wsIn Is Nothing Then Set wsIn = wbIn.Worksheets(1)

            Dim lastRow As Long
            lastRow = wsIn.Cells(wsIn.Rows.Count, "A").End(xlUp).Row

            Dim currentDate As String
            Dim currentStation As String
            currentDate = ""
            currentStation = ""

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
                    ' prazdny oddelovac mezi stanicemi
                    GoTo NextRow
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
                        currentDate = a
                    Case "STATION"
                        currentStation = a
                    Case Else
                        ' DATA — A=Koleje, B=Vyhybky, C=Cas, D=Poznamka
                        wsOut.Cells(outRow, 1).Value = currentDate
                        wsOut.Cells(outRow, 2).Value = currentStation
                        wsOut.Cells(outRow, 3).Value = a
                        wsOut.Cells(outRow, 4).Value = b
                        wsOut.Cells(outRow, 5).Value = c
                        wsOut.Cells(outRow, 6).Value = d
                        outRow = outRow + 1
                        rowsWritten = rowsWritten + 1
                End Select
NextRow:
            Next r

            wbIn.Close SaveChanges:=False
            filesProcessed = filesProcessed + 1
        End If

        fileName = Dir()
    Loop

    If outRow > 2 Then
        wsOut.Range("A1:F1").AutoFilter
    End If
    wsOut.Columns("A:F").AutoFit

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
           "Radku dat: " & rowsWritten & vbCrLf & _
           "Ulozeno: " & outPath, vbInformation
End Sub

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

' Stejna logika jako SimpleXlsx: barva A, jinak heuristika.
' Dulezite: datovy radek muze mit jen Koleje (A) a prazdne B/C/D —
' to NENI stanice, pokud uz mame datum a neni to oranzova stanice.
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
    ' Appka pise d.M.yyyy (cs), napr. 10.8.2026
    LooksLikeDate = (s Like "*.*.####") Or (s Like "*/*.*/####")
End Function
