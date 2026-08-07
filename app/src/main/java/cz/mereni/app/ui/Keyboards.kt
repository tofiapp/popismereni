package cz.mereni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cz.mereni.app.ActiveField
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind
import cz.mereni.app.data.SelectedToken
import cz.mereni.app.data.Station

val KeyHeight: Dp = 56.dp
val ChipHeight: Dp = 52.dp
val FieldPanelHeight: Dp = 140.dp

/** Speciální tokeny vždy v klávesnici výhybek. */
val ExtraVyhybkaLabels = listOf("po vůz", "kkk")

@Composable
fun FieldKeyboard(
    activeField: ActiveField,
    pasportKeys: List<PasportKey>,
    hour: Int,
    minute: Int,
    onPasportKey: (PasportKey) -> Unit,
    onExtraLabel: (String) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onUseNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MereniColors.Surface)
            .padding(10.dp)
    ) {
        when (activeField) {
            ActiveField.POLE1 -> Pole1Keyboard(keys = pasportKeys, onKey = onPasportKey)
            ActiveField.POLE2 -> Pole2Keyboard(
                keys = pasportKeys,
                onKey = onPasportKey,
                onExtraLabel = onExtraLabel,
            )
            ActiveField.CAS -> TimeKeyboard(
                hour = hour,
                minute = minute,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange,
                onUseNow = onUseNow,
            )
        }
    }
}

@Composable
fun Pole1Keyboard(keys: List<PasportKey>, onKey: (PasportKey) -> Unit) {
    val koleje = keys.filter { it.kind == PasportKind.KOLEJ }
    val spojky = keys.filter { it.kind == PasportKind.SPOJKA }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyboardHalf("Koleje", MereniColors.Kolej, koleje, onKey, Modifier.weight(1f), kolejMode = true)
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MereniColors.Accent.copy(alpha = 0.35f))
        )
        KeyboardHalf("Spojky", MereniColors.Spojka, spojky, onKey, Modifier.weight(1f))
    }
}

@Composable
fun Pole2Keyboard(
    keys: List<PasportKey>,
    onKey: (PasportKey) -> Unit,
    onExtraLabel: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyboardHalf(
            "Výhybky",
            MereniColors.Vyhybka,
            keys.filter { it.kind == PasportKind.VYHYBKA },
            onKey,
            Modifier.weight(1f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 96.dp)
                .padding(start = 4.dp),
        ) {
            Text(
                "stálé",
                color = MereniColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            ExtraVyhybkaLabels.forEach { label ->
                KeyRect(
                    label = label,
                    color = MereniColors.Vyhybka,
                    onClick = { onExtraLabel(label) },
                    minWidth = 92.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyboardHalf(
    title: String,
    accent: Color,
    keys: List<PasportKey>,
    onKey: (PasportKey) -> Unit,
    modifier: Modifier = Modifier,
    kolejMode: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = "$title (${keys.size})",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (keys.isEmpty()) {
            Text("Pro tuto stanici žádné položky", color = MereniColors.TextMuted, fontSize = 12.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                keys.forEach { key ->
                    if (kolejMode && key.hasExpandableChildren) {
                        KolejKeyWithPicker(key = key, onKey = onKey)
                    } else {
                        KeyRect(
                            label = key.label,
                            color = colorFor(key.kind),
                            onClick = { onKey(key) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kolej s podkolejemi — klepnutí otevře picker (bez nápisu „hlavní“).
 */
@Composable
private fun KolejKeyWithPicker(key: PasportKey, onKey: (PasportKey) -> Unit) {
    var open by remember(key.label) { mutableStateOf(false) }
    KeyRect(
        label = "${key.label} ▾",
        color = colorFor(key.kind),
        onClick = { open = true },
    )
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 420.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MereniColors.SurfaceAlt)
                    .padding(16.dp)
            ) {
                Text(
                    "Kolej ${key.label}",
                    color = MereniColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    PickerOption(
                        label = key.label,
                        color = MereniColors.Kolej,
                    ) {
                        onKey(key)
                        open = false
                    }
                    key.children.forEach { child ->
                        PickerOption(
                            label = child.label,
                            color = MereniColors.Kolej.copy(alpha = 0.88f),
                        ) {
                            onKey(child)
                            open = false
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.End)) {
                    Text("Zavřít", color = MereniColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun PickerOption(label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = label,
            color = if (color.luminance() > 0.55f) MereniColors.Text else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun TimeKeyboard(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onUseNow: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%02d:%02d".format(hour, minute),
                color = MereniColors.Accent,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onUseNow) {
                Text("Nastavit teď", color = MereniColors.Accent, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WheelColumn("Hodiny", hour, 0..23, onHourChange, big = true)
            Text(
                ":",
                color = MereniColors.Accent,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            WheelColumn("Minuty", minute, 0..59, onMinuteChange, big = true)
        }
    }
}

@Composable
private fun WheelColumn(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    big: Boolean = false,
) {
    val boxW = if (big) 88.dp else 64.dp
    val boxH = if (big) 72.dp else KeyHeight
    val fontSize = if (big) 32.sp else 22.sp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = MereniColors.TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        KeyRect(label = "▲", color = MereniColors.SurfaceAlt, onClick = {
            onChange(if (value >= range.last) range.first else value + 1)
        })
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(boxW)
                .height(boxH)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, MereniColors.Accent, RoundedCornerShape(10.dp))
                .background(MereniColors.Surface)
        ) {
            Text(
                "%02d".format(value),
                color = MereniColors.Text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        KeyRect(label = "▼", color = MereniColors.SurfaceAlt, onClick = {
            onChange(if (value <= range.first) range.last else value - 1)
        })
    }
}

@Composable
fun KeyRect(
    label: String,
    color: Color,
    onClick: () -> Unit,
    minWidth: Dp = 0.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(KeyHeight)
            .then(if (minWidth > 0.dp) Modifier.widthIn(min = minWidth) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            text = label,
            color = if (color.luminance() > 0.55f) MereniColors.Text else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

fun colorFor(kind: PasportKind): Color = when (kind) {
    PasportKind.SPOJKA -> MereniColors.Spojka
    PasportKind.KOLEJ -> MereniColors.Kolej
    PasportKind.VYHYBKA -> MereniColors.Vyhybka
}

/**
 * Barevný chip — šipky ‹ › jen v režimu přesunu.
 */
@Composable
fun ChipToken(
    token: SelectedToken,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
) {
    val bg = colorFor(token.kind)
    val fg = if (bg.luminance() > 0.55f) MereniColors.Text else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(end = 6.dp),
    ) {
        if (onMoveLeft != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MereniColors.SurfaceAlt)
                    .clickable(onClick = onMoveLeft)
            ) {
                Text("‹", color = MereniColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .padding(horizontal = 14.dp)
            ) {
                Text(token.label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MereniColors.Danger)
                    .clickable(onClick = onRemove)
            ) {
                Text("−", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (onMoveRight != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MereniColors.SurfaceAlt)
                    .clickable(onClick = onMoveRight)
            ) {
                Text("›", color = MereniColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Řádek chipů — šipky jen když [reorderMode].
 */
@Composable
fun ChipRow(
    items: List<SelectedToken>,
    reorderMode: Boolean,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, token ->
            ChipToken(
                token = token,
                onRemove = { onRemove(index) },
                onMoveLeft = if (reorderMode && index > 0) {
                    { onMove(index, index - 1) }
                } else null,
                onMoveRight = if (reorderMode && index < items.lastIndex) {
                    { onMove(index, index + 1) }
                } else null,
            )
        }
    }
}

@Composable
fun FieldPanel(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    showReorderToggle: Boolean = false,
    reorderMode: Boolean = false,
    onReorderToggle: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(FieldPanelHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MereniColors.SurfaceAlt else MereniColors.Surface)
            .then(
                if (selected) Modifier.border(2.dp, MereniColors.Accent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = contentAlignment,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .padding(bottom = if (showReorderToggle) 18.dp else 0.dp),
        ) {
            content()
        }
        if (showReorderToggle && onReorderToggle != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (reorderMode) MereniColors.Accent else MereniColors.SurfaceAlt
                    )
                    .border(1.dp, MereniColors.ChipBorder, RoundedCornerShape(6.dp))
                    .clickable(onClick = onReorderToggle)
            ) {
                Text(
                    "<>",
                    color = if (reorderMode) Color.White else MereniColors.Text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Placeholder v 1. poli — Koleje / spojky ve svých barvách. */
@Composable
fun Pole1Placeholder() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Koleje",
            color = MereniColors.Kolej,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "a",
            color = MereniColors.TextMuted,
            fontSize = 16.sp,
        )
        Text(
            "spojky",
            color = MereniColors.Spojka,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Placeholder v 2. poli — od / do. */
@Composable
fun Pole2Placeholder() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "od",
            color = MereniColors.Vyhybka,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(",", color = MereniColors.TextMuted, fontSize = 18.sp)
        Text(
            "do",
            color = MereniColors.Vyhybka,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Kompaktní tlačítko stanice — vyhledávání v dialogu (nerozbíjí layout).
 */
@Composable
fun StationSearchPicker(
    stations: List<Station>,
    selected: Station?,
    onSelect: (Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val indexed = remember(stations) { stations.map { it to it.jmeno.lowercase() } }
    val filtered = remember(indexed, query) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else indexed.asSequence().filter { (_, n) -> n.contains(q) }.take(40).map { it.first }.toList()
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(KeyHeight)
            .widthIn(min = 160.dp, max = 280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MereniColors.Surface)
            .border(1.dp, MereniColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable {
                open = true
                query = ""
            }
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = selected?.jmeno ?: "Stanice…",
            color = if (selected == null) MereniColors.TextMuted else MereniColors.Text,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 480.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MereniColors.SurfaceAlt)
                    .padding(16.dp)
            ) {
                Text(
                    "Hledat stanici",
                    color = MereniColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MereniColors.Text, fontSize = 16.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KeyHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MereniColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("napiš aspoň 2 písmena…", color = MereniColors.TextMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                when {
                    stations.isEmpty() -> Text(
                        "Žádné stanice — vyber SQLite z Download",
                        color = MereniColors.TextMuted,
                        fontSize = 13.sp,
                    )
                    query.trim().length < 2 -> Text(
                        "Napiš aspoň 2 písmena (${stations.size})",
                        color = MereniColors.TextMuted,
                        fontSize = 13.sp,
                    )
                    filtered.isEmpty() -> Text("Nic nenalezeno", color = MereniColors.TextMuted, fontSize = 13.sp)
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filtered.size) { i ->
                            val station = filtered[i]
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(KeyHeight)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (station.udu == selected?.udu) MereniColors.AccentDark
                                        else MereniColors.Surface
                                    )
                                    .clickable {
                                        onSelect(station)
                                        open = false
                                    }
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(station.jmeno, color = MereniColors.Text, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.End)) {
                    Text("Zavřít", color = MereniColors.Accent)
                }
            }
        }
    }
}

/**
 * Ozubené kolečko — nastavení pasportu (Vybrat / Obnovit / stav).
 */
@Composable
fun PasportSettingsButton(
    statusText: String,
    statusOk: Boolean,
    loading: Boolean,
    onPick: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MereniColors.Surface)
            .border(1.dp, MereniColors.ChipBorder, CircleShape)
            .clickable { open = true }
    ) {
        Text("⚙", fontSize = 18.sp, color = MereniColors.Text)
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 440.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MereniColors.SurfaceAlt)
                    .padding(16.dp)
            ) {
                Text(
                    "Pasport",
                    color = MereniColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = statusText,
                    color = when {
                        loading -> MereniColors.Accent
                        statusOk -> MereniColors.Kolej
                        else -> MereniColors.Danger
                    },
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onPick()
                        open = false
                    }) {
                        Text("Vybrat SQLite", color = MereniColors.Accent, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = {
                        onReload()
                        open = false
                    }) {
                        Text("Obnovit", color = MereniColors.TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.End)) {
                    Text("Zavřít", color = MereniColors.Accent)
                }
            }
        }
    }
}
