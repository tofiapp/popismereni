let
    Nove = TabNove,
    ArchivTab = Excel.CurrentWorkbook(){[Name="TabArchiv"]}[Content],

    ColNove = Table.TransformColumns(
        Table.SelectColumns(Nove, {"ZdrojovySoubor"}),
        {{"ZdrojovySoubor", each Text.Trim(Text.From(_)), type text}}),
    ColArchiv = Table.TransformColumns(
        Table.SelectColumns(ArchivTab, {"ZdrojovySoubor"}),
        {{"ZdrojovySoubor", each Text.Trim(Text.From(_)), type text}}),

    SouboryNove = Table.SelectRows(Table.Distinct(ColNove), each [ZdrojovySoubor] <> "" and [ZdrojovySoubor] <> "ZdrojovySoubor"),
    SouboryArchiv = Table.SelectRows(Table.Distinct(ColArchiv), each [ZdrojovySoubor] <> "" and [ZdrojovySoubor] <> "ZdrojovySoubor"),

    Ceka = Table.RowCount(
        Table.NestedJoin(SouboryNove, {"ZdrojovySoubor"}, SouboryArchiv, {"ZdrojovySoubor"}, "x", JoinKind.LeftAnti)),

    TextStavu =
        if Table.RowCount(SouboryNove) = 0 then "Žádná data v dotazu"
        else if Ceka = 0 then "✔ Aktuální"
        else "⬤ Čeká " & Text.From(Ceka) & (
            if Ceka = 1 then " nový soubor"
            else if Ceka < 5 then " nové soubory"
            else " nových souborů")
in
    #table({"Stav"}, {{TextStavu}})
