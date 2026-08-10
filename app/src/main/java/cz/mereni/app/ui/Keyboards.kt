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
import androidx.compose.foundation.layout.PaddingValues
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
import cz.mereni.app.data.StationNameCleaner

val KeyHeight: Dp = 56.dp
val ChipHeight: Dp = 52.dp
val FieldPanelHeight: Dp = 140.dp

/** Speciální tokeny vždy v klávesnici výhybek (poslední dvě červené). */
data class ExtraVyhybkaOption(val label: String, val color: Color)

val ExtraVyhybkaOptions = listOf(
    ExtraVyhybkaOption("po vůz", MereniColors.Vyhybka),
    ExtraVyhybkaOption("kkk", MereniColors.Vyhybka),
    ExtraVyhybkaOption("obsazeno", MereniColors.Danger),
    ExtraVyhybkaOption("vyloučeno", MereniColors.Danger),
)

@Composable
fun FieldKeyboard(
    activeField: ActiveField,
    pasportKeys: List<PasportKey>,
    usedLabels: Set<String>,
    lockedLabels: Set<String>,
    hour: Int,
    minute: Int,
    timeChosen: Boolean,
    onPasportKey: (PasportKey) -> Unit,
    onExtraLabel: (String) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onUseNow: () -> Unit,
    onClearTime: () -> Unit,
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
            ActiveField.POLE1 -> Pole1Keyboard(
                keys = pasportKeys,
                usedLabels = usedLabels,
                lockedLabels = lockedLabels,
                onKey = onPasportKey,
            )
            ActiveField.POLE2 -> Pole2Keyboard(
                keys = pasportKeys,
                usedLabels = usedLabels,
                lockedLabels = lockedLabels,
                onKey = onPasportKey,
                onExtraLabel = onExtraLabel,
            )
            ActiveField.CAS -> TimeKeyboard(
                hour = hour,
                minute = minute,
                timeChosen = timeChosen,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange,
                onUseNow = onUseNow,
                onClear = onClearTime,
            )
        }
    }
}

@Composable
fun Pole1Keyboard(
    keys: List<PasportKey>,
    usedLabels: Set<String>,
    lockedLabels: Set<String>,
    onKey: (PasportKey) -> Unit,
) {
    val koleje = keys.filter { it.kind == PasportKind.KOLEJ }
    val spojky = keys.filter { it.kind == PasportKind.SPOJKA }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyboardHalf(
            "Koleje",
            MereniColors.Kolej,
            koleje,
            onKey,
            Modifier.weight(1f),
            kolejMode = true,
            usedLabels = usedLabels,
            lockedLabels = lockedLabels,
        )
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MereniColors.Accent.copy(alpha = 0.35f))
        )
        KeyboardHalf(
            "Spojky",
            MereniColors.Spojka,
            spojky,
            onKey,
            Modifier.weight(1f),
            usedLabels = usedLabels,
            lockedLabels = lockedLabels,
        )
    }
}

@Composable
fun Pole2Keyboard(
    keys: List<PasportKey>,
    usedLabels: Set<String>,
    lockedLabels: Set<String>,
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
            usedLabels = usedLabels,
            lockedLabels = lockedLabels,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 104.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 4.dp),
        ) {
            ExtraVyhybkaOptions.forEach { opt ->
                val locked = opt.label in lockedLabels
                val used = locked || opt.label in usedLabels
                KeyRect(
                    label = opt.label,
                    color = opt.color,
                    used = used,
                    locked = locked,
                    onClick = { onExtraLabel(opt.label) },
                    minWidth = 100.dp,
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
    usedLabels: Set<String> = emptySet(),
    lockedLabels: Set<String> = emptySet(),
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
                        KolejKeyWithPicker(
                            key = key,
                            usedLabels = usedLabels,
                            lockedLabels = lockedLabels,
                            onKey = onKey,
                        )
                    } else {
                        val locked = key.label in lockedLabels
                        KeyRect(
                            label = key.label,
                            color = colorFor(key.kind),
                            used = locked || key.label in usedLabels,
                            locked = locked,
                            onClick = { onKey(key) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kolej s podkolejemi — velká karta jen pro číslo bez písmen;
 * varianty s písmeny vedle sebe, šířka podle počtu.
 * V horním poli = zašedlé a nepřidatelné; po uložení v CSV jen zašedlé (jdou znovu).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KolejKeyWithPicker(
    key: PasportKey,
    usedLabels: Set<String>,
    lockedLabels: Set<String>,
    onKey: (PasportKey) -> Unit,
) {
    var open by remember(key.label) { mutableStateOf(false) }
    fun isGray(label: String) = label in lockedLabels || label in usedLabels
    fun isLocked(label: String) = label in lockedLabels
    val mainGray = isGray(key.cobjekt)
    val mainLocked = isLocked(key.cobjekt)
    val allChildrenGray = key.children.isNotEmpty() && key.children.all { isGray(it.label) }
    val keyUsed = mainGray && (key.children.isEmpty() || allChildrenGray)
    KeyRect(
        label = "${key.label} ▾",
        color = colorFor(key.kind),
        used = keyUsed,
        // Dialog zůstává oteviratelný — jednotlivé volby se zamykají uvnitř.
        onClick = { open = true },
    )
    if (open) {
        val children = key.children
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 520.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MereniColors.SurfaceAlt)
                    .padding(16.dp)
            ) {
                Text(
                    "Kolej ${key.cobjekt}",
                    color = MereniColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Velká karta — jen varianta bez písmen (číslo koleje)
                    PickerOption(
                        label = key.cobjekt,
                        color = MereniColors.Kolej,
                        large = true,
                        used = mainGray,
                        locked = mainLocked,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        onKey(key.copy(iob = null, children = emptyList()))
                        open = false
                    }
                    if (children.isNotEmpty()) {
                        val gap = 8.dp
                        // Max 3 podkoleje vedle sebe; další řádky stejně (1A 1B 1C | 1D …)
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            children.chunked(3).forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowItems.forEach { child ->
                                        PickerOption(
                                            label = child.label,
                                            color = MereniColors.Kolej.copy(alpha = 0.88f),
                                            large = false,
                                            used = isGray(child.label),
                                            locked = isLocked(child.label),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            onKey(child)
                                            open = false
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
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

@Composable
private fun PickerOption(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    used: Boolean = false,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val height = if (large) 72.dp else 52.dp
    val fontSize = if (large) 26.sp else 18.sp
    val bg = if (used) MereniColors.UsedKeyBg else color
    val fg = if (used) {
        color.copy(alpha = 0.9f)
    } else if (color.luminance() > 0.55f) {
        MereniColors.Text
    } else {
        Color.White
    }
    val shape = RoundedCornerShape(if (large) 12.dp else 10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (used) Modifier.border(2.dp, color.copy(alpha = 0.85f), shape) else Modifier)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun TimeKeyboard(
    hour: Int,
    minute: Int,
    timeChosen: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onUseNow: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (timeChosen) "%02d:%02d".format(hour, minute) else "—:—",
                color = if (timeChosen) MereniColors.Cas else MereniColors.TextMuted,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUseNow) {
                    Text("Nastavit teď", color = MereniColors.Cas, fontWeight = FontWeight.SemiBold)
                }
                if (timeChosen) {
                    TextButton(onClick = onClear) {
                        Text("Bez času", color = MereniColors.TextMuted)
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WheelColumn("Hodiny", hour, 0..23, onHourChange, big = true)
            Text(
                ":",
                color = MereniColors.Cas,
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
                .border(2.dp, MereniColors.Cas, RoundedCornerShape(10.dp))
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
    used: Boolean = false,
    /** V horním poli — zašedlé a nelze znovu přidat (na rozdíl od jen CSV). */
    locked: Boolean = false,
) {
    // Zašedlé = v poli / v CSV; typ zůstává podle barvy okraje a textu.
    val bg = if (used) MereniColors.UsedKeyBg else color
    val fg = if (used) {
        color.copy(alpha = 0.9f)
    } else if (color.luminance() > 0.55f) {
        MereniColors.Text
    } else {
        Color.White
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(KeyHeight)
            .then(if (minWidth > 0.dp) Modifier.widthIn(min = minWidth) else Modifier)
            .clip(shape)
            .background(bg)
            .then(if (used) Modifier.border(2.dp, color.copy(alpha = 0.85f), shape) else Modifier)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            text = label,
            color = fg,
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

private val DangerExtraLabels = setOf("obsazeno", "vyloučeno")

fun colorForToken(token: SelectedToken): Color = when {
    token.custom -> MereniColors.Custom
    token.label in DangerExtraLabels -> MereniColors.Danger
    else -> colorFor(token.kind)
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
    val bg = colorForToken(token)
    val fg = if (bg.luminance() > 0.55f) MereniColors.Text else Color.White
    val shape = RoundedCornerShape(6.dp)
    val fromSecond = token.fromSlot == 1
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
                    .clip(shape)
                    .background(bg)
                    .then(
                        if (fromSecond) Modifier.border(2.5.dp, MereniColors.Dual, shape)
                        else Modifier
                    )
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
 * Řádek chipů — šipky jen když [reorderMode]; u pole 2 pomlčky mezi obdélníky.
 */
@Composable
fun ChipRow(
    items: List<SelectedToken>,
    reorderMode: Boolean,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    dashBetween: Boolean = false,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, token ->
            if (dashBetween && index > 0) {
                Text(
                    "–",
                    color = MereniColors.TextMuted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
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
    /** Barva typu pole — svítí v zakliknutém stavu (kolej / výhybka / čas). */
    accentColor: Color = MereniColors.Accent,
    /** Trvalý jemný nádech (např. karta času). */
    tintAlways: Boolean = false,
    showReorderToggle: Boolean = false,
    reorderMode: Boolean = false,
    onReorderToggle: (() -> Unit)? = null,
    showAddButton: Boolean = false,
    onAddClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        selected -> accentColor.copy(alpha = 0.16f)
        tintAlways -> accentColor.copy(alpha = 0.08f)
        else -> MereniColors.Surface
    }
    val border = when {
        selected -> Modifier.border(2.5.dp, accentColor, shape)
        tintAlways -> Modifier.border(1.5.dp, accentColor.copy(alpha = 0.45f), shape)
        else -> Modifier
    }
    Box(
        modifier = modifier
            .height(FieldPanelHeight)
            .clip(shape)
            .background(bg)
            .then(border)
            .clickable(onClick = onClick)
    ) {
        val bottomPad = if (showReorderToggle || showAddButton) 18.dp else 0.dp
        Box(
            contentAlignment = contentAlignment,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .padding(bottom = bottomPad),
        ) {
            content()
        }
        if (showAddButton && onAddClick != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MereniColors.Custom)
                    .clickable(onClick = onAddClick)
            ) {
                Text(
                    "+",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
                        if (reorderMode) accentColor else MereniColors.SurfaceAlt
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

/** Dialog pro vlastní obdélníček (+). */
@Composable
fun CustomTokenDialog(
    open: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!open) return
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MereniColors.SurfaceAlt)
                .padding(16.dp)
        ) {
            Text(
                title,
                color = MereniColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = MereniColors.Text, fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KeyHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MereniColors.Surface)
                    .border(2.dp, MereniColors.Custom, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("napiš text…", color = MereniColors.TextMuted, fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Zrušit", color = MereniColors.TextMuted)
                }
                TextButton(
                    onClick = {
                        val v = text.trim()
                        if (v.isNotEmpty()) {
                            onConfirm(v)
                            onDismiss()
                        }
                    },
                ) {
                    Text("Vložit", color = MereniColors.Custom, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Nápis aktivního pole — sjednocená kapitálka, barvy typů. */
@Composable
fun ActiveFieldCaption(activeField: ActiveField) {
    when (activeField) {
        ActiveField.POLE1 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Koleje", color = MereniColors.Kolej, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("a", color = MereniColors.TextMuted, fontSize = 15.sp)
            Text("spojky", color = MereniColors.Spojka, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        ActiveField.POLE2 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Od", color = MereniColors.Vyhybka, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(",", color = MereniColors.TextMuted, fontSize = 15.sp)
            Text("do", color = MereniColors.Vyhybka, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        ActiveField.CAS -> Text(
            "Čas",
            color = MereniColors.Cas,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Vyhledávač stanice. V dual režimu klepnutí aktivuje zdroj kláves;
 * 🔍 otevře dialog. U výsledků lze volitelně otevřít 2. vyhledávač.
 */
@Composable
fun StationSearchPicker(
    stations: List<Station>,
    selected: Station?,
    onSelect: (Station) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MereniColors.Accent,
    isKeySource: Boolean = false,
    slotLabel: String? = null,
    onActivate: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onOpenInSecond: ((Station) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val indexed = remember(stations) {
        stations.map { st ->
            val keys = (listOf(st.jmeno) + st.aliases)
                .map { StationNameCleaner.foldForSearch(it) }
                .distinct()
            st to keys
        }
    }
    val filtered = remember(indexed, query) {
        val q = StationNameCleaner.foldForSearch(query)
        if (q.length < 2) emptyList()
        else indexed.asSequence()
            .filter { (_, keys) -> keys.any { it.contains(q) } }
            .take(50)
            .map { it.first }
            .toList()
    }

    val shape = RoundedCornerShape(10.dp)
    val borderW = if (isKeySource) 2.5.dp else 1.5.dp
    val bg = if (isKeySource) accentColor.copy(alpha = 0.14f) else MereniColors.Surface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(52.dp)
            .widthIn(min = 160.dp, max = 300.dp)
            .clip(shape)
            .background(bg)
            .border(borderW, accentColor.copy(alpha = if (isKeySource) 1f else 0.55f), shape)
            .padding(start = 10.dp, end = 6.dp),
    ) {
        if (slotLabel != null) {
            Text(
                slotLabel,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable {
                    if (selected == null) {
                        open = true
                        query = ""
                    } else {
                        onActivate?.invoke()
                    }
                },
        ) {
            Text(
                text = selected?.jmeno ?: "Stanice…",
                color = if (selected == null) MereniColors.TextMuted else MereniColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    open = true
                    query = ""
                },
        ) {
            Text("🔍", fontSize = 14.sp)
        }
        if (onClear != null && selected != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
            ) {
                Text("×", color = MereniColors.TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 560.dp)
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
                    textStyle = TextStyle(color = MereniColors.Text, fontSize = 18.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MereniColors.Surface)
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("napiš aspoň 2 písmena…", color = MereniColors.TextMuted, fontSize = 18.sp)
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
                            .heightIn(max = 320.dp)
                    ) {
                        items(filtered.size) { i ->
                            val station = filtered[i]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (station.udu == selected?.udu) accentColor.copy(alpha = 0.35f)
                                        else MereniColors.Surface
                                    )
                                    .padding(horizontal = 8.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            onSelect(station)
                                            onActivate?.invoke()
                                            open = false
                                        }
                                        .padding(horizontal = 6.dp),
                                ) {
                                    Text(
                                        station.jmeno,
                                        color = MereniColors.Text,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 17.sp,
                                    )
                                }
                                if (onOpenInSecond != null) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MereniColors.Dual)
                                            .clickable {
                                                onOpenInSecond(station)
                                                open = false
                                            }
                                            .padding(horizontal = 12.dp),
                                    ) {
                                        Text(
                                            "+ 2",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
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
 * Ozubené kolečko — nastavení pasportu + verze + mereni.csv.
 */
@Composable
fun PasportSettingsButton(
    statusText: String,
    statusOk: Boolean,
    loading: Boolean,
    appVersion: String,
    recordCount: Int,
    onPick: () -> Unit,
    onReload: () -> Unit,
    onExportCsv: () -> Unit,
    exportMessage: String? = null,
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
                    "Nastavení",
                    color = MereniColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Verze aplikace  v$appVersion",
                    color = MereniColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "mereni.csv  •  $recordCount záznamů",
                    color = MereniColors.TextMuted,
                    fontSize = 13.sp,
                )
                if (exportMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(exportMessage, color = MereniColors.Kolej, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onExportCsv,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Exportovat CSV…",
                        color = MereniColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                Text(
                    "Ulož do Download, Documents nebo OneDrive (systémový výběr).",
                    color = MereniColors.TextMuted,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Pasport",
                    color = MereniColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText,
                    color = when {
                        loading -> MereniColors.Accent
                        statusOk -> MereniColors.Kolej
                        else -> MereniColors.Danger
                    },
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Vybrat SQLite otevře systémový dialog — včetně OneDrive, pokud je nainstalovaný.",
                    color = MereniColors.TextMuted,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onPick()
                        open = false
                    }) {
                        Text("Vybrat SQLite…", color = MereniColors.Accent, fontWeight = FontWeight.SemiBold)
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
