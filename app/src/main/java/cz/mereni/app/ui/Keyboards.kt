package cz.mereni.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mereni.app.ActiveField
import cz.mereni.app.OdkudKamSide
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind

/** Výchozí výplň kláves pro CO SE MĚŘÍ (dokud není vlastní katalog). */
val CoSeMeriKeys: List<String> = listOf(
    "Teplota", "Tlak", "Rozchod", "Převýšení", "Směr", "Sklon",
)

/** Klávesnice času měření. */
val CasMereniKeys: List<String> = listOf(
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldKeyboard(
    activeField: ActiveField,
    odkudKamSide: OdkudKamSide,
    pasportKeys: List<PasportKey>,
    onPasportKey: (PasportKey) -> Unit,
    onTextKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MereniColors.Surface)
            .padding(10.dp)
    ) {
        when (activeField) {
            ActiveField.CO_SE_MERI -> TextKeyGrid(CoSeMeriKeys, onTextKey)
            ActiveField.CAS_MERENI -> TextKeyGrid(CasMereniKeys, onTextKey)
            ActiveField.ODKUD_KAM -> PasportKeyboard(
                side = odkudKamSide,
                keys = pasportKeys,
                onKey = onPasportKey,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextKeyGrid(labels: List<String>, onKey: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        labels.forEach { label ->
            KeyRect(
                label = label,
                color = MereniColors.AccentDark,
                onClick = { onKey(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasportKeyboard(
    side: OdkudKamSide,
    keys: List<PasportKey>,
    onKey: (PasportKey) -> Unit,
) {
    val filtered = when (side) {
        OdkudKamSide.ODKUD -> keys.filter {
            it.kind == PasportKind.SPOJKA || it.kind == PasportKind.KOLEJ
        }
        OdkudKamSide.KAM -> keys.filter { it.kind == PasportKind.VYHYBKA }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (side) {
                OdkudKamSide.ODKUD -> "Obdélník 1 — spojky a koleje"
                OdkudKamSide.KAM -> "Obdélník 2 — výhybky"
            },
            color = MereniColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        if (filtered.isEmpty()) {
            Text(
                text = "Pasport TPI zatím není načtený. Nahraj DZS_PASPORT_TPI.sqlite a vygeneruj pasport_tpi_v….json.",
                color = MereniColors.TextMuted,
                fontSize = 13.sp,
            )
            return
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            filtered.forEach { key ->
                ExpandablePasportKey(key = key, onKey = onKey)
            }
        }
    }
}

@Composable
private fun ExpandablePasportKey(
    key: PasportKey,
    onKey: (PasportKey) -> Unit,
) {
    var expanded by remember(key.label) { mutableStateOf(false) }

    Column {
        KeyRect(
            label = key.label,
            color = colorFor(key.kind),
            badge = if (key.hasExpandableChildren) "▾" else null,
            onClick = { onKey(key) },
            onBadgeClick = if (key.hasExpandableChildren) {
                { expanded = !expanded }
            } else null,
        )

        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(top = 6.dp, start = 4.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                key.children.forEach { child ->
                    KeyRect(
                        label = child.label,
                        color = colorFor(child.kind).copy(alpha = 0.85f),
                        onClick = { onKey(child) },
                    )
                }
            }
        }
    }
}

@Composable
fun KeyRect(
    label: String,
    color: Color,
    onClick: () -> Unit,
    badge: String? = null,
    onBadgeClick: (() -> Unit)? = null,
) {
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .heightIn(min = 28.dp)
        ) {
            Text(
                text = label,
                color = MereniColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (badge != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MereniColors.Accent)
                    .clickable { onBadgeClick?.invoke() ?: onClick() }
            ) {
                Text(text = badge, fontSize = 10.sp, color = MereniColors.BackgroundTop)
            }
        }
    }
}

fun colorFor(kind: PasportKind): Color = when (kind) {
    PasportKind.SPOJKA -> MereniColors.Spojka
    PasportKind.KOLEJ -> MereniColors.Kolej
    PasportKind.VYHYBKA -> MereniColors.Vyhybka
}

@Composable
fun ChipToken(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(end = 6.dp, bottom = 4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MereniColors.ChipBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = MereniColors.ChipText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(16.dp)
                .clip(CircleShape)
                .background(MereniColors.Danger)
                .clickable(onClick = onRemove)
        ) {
            Text(
                text = "−",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun DashSeparator() {
    Text(
        text = "–",
        color = MereniColors.Dash,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
fun FieldPanel(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MereniColors.SurfaceAlt else MereniColors.Surface)
            .then(
                if (selected) Modifier.border(2.dp, MereniColors.Accent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(
            text = title,
            color = MereniColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}
