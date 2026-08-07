package cz.mereni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import cz.mereni.app.ActiveField
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind
import cz.mereni.app.data.SelectedToken
import cz.mereni.app.data.Station
import kotlin.math.roundToInt

val KeyHeight: Dp = 56.dp
val ChipHeight: Dp = 52.dp
val FieldPanelHeight: Dp = 140.dp

@Composable
fun FieldKeyboard(
    activeField: ActiveField,
    pasportKeys: List<PasportKey>,
    hour: Int,
    minute: Int,
    onPasportKey: (PasportKey) -> Unit,
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
            ActiveField.POLE2 -> Pole2Keyboard(keys = pasportKeys, onKey = onPasportKey)
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
fun Pole2Keyboard(keys: List<PasportKey>, onKey: (PasportKey) -> Unit) {
    KeyboardHalf(
        "Výhybky",
        MereniColors.Vyhybka,
        keys.filter { it.kind == PasportKind.VYHYBKA },
        onKey,
        Modifier.fillMaxSize(),
    )
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
 * Kolej s podkolejemi — klepnutí na celou klávesu otevře velký picker (snadný zásah).
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Vyber hlavní kolej nebo podkolej",
                    color = MereniColors.TextMuted,
                    fontSize = 13.sp,
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
                        label = "${key.label} (hlavní)",
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
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(KeyHeight)
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
 * Barevný chip — dlouhý stisk a táhnutí mění pořadí.
 */
@Composable
fun ChipToken(
    token: SelectedToken,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val bg = colorFor(token.kind)
    val fg = if (bg.luminance() > 0.55f) MereniColors.Text else Color.White
    Box(
        modifier = modifier
            .then(if (elevated) Modifier.shadow(8.dp, RoundedCornerShape(6.dp)).zIndex(2f) else Modifier)
            .then(dragModifier)
    ) {
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
}

/**
 * Řádek chipů s přeuspořádáním dlouhým stiskem + tažením.
 */
@Composable
fun ReorderableChipRow(
    items: List<SelectedToken>,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 56.dp.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, token ->
            val isDragging = draggingId == token.id
            ChipToken(
                token = token,
                onRemove = { onRemove(index) },
                elevated = isDragging,
                modifier = Modifier.padding(end = 2.dp),
                dragModifier = Modifier
                    .offset {
                        IntOffset(if (isDragging) dragOffsetX.roundToInt() else 0, 0)
                    }
                    .pointerInput(token.id, items.map { it.id }) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = token.id
                                dragOffsetX = 0f
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffsetX = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetX += amount.x
                                val currentIndex = items.indexOfFirst { it.id == token.id }
                                if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                if (dragOffsetX > swapThresholdPx && currentIndex < items.lastIndex) {
                                    onMove(currentIndex, currentIndex + 1)
                                    dragOffsetX -= swapThresholdPx
                                } else if (dragOffsetX < -swapThresholdPx && currentIndex > 0) {
                                    onMove(currentIndex, currentIndex - 1)
                                    dragOffsetX += swapThresholdPx
                                }
                            },
                        )
                    },
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
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .height(FieldPanelHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MereniColors.SurfaceAlt else MereniColors.Surface)
            .then(
                if (selected) Modifier.border(2.dp, MereniColors.Accent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        content()
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
