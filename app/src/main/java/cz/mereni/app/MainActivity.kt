package cz.mereni.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mereni.app.data.MeasurementStore
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportRepository
import cz.mereni.app.ui.ChipToken
import cz.mereni.app.ui.DashSeparator
import cz.mereni.app.ui.FieldKeyboard
import cz.mereni.app.ui.FieldPanel
import cz.mereni.app.ui.MereniColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = MeasurementStore(this)
        store.ensureHeader()
        val version = BuildConfig.VERSION_NAME
        val pasport = PasportRepository.load(this, version)
        setContent {
            MereniApp(
                appVersion = version,
                pasportKeys = pasport,
                initialCount = store.count(),
                onSave = { co, odkudKam, cas ->
                    store.append(co, odkudKam, cas)
                    store.count()
                },
            )
        }
    }
}

@Composable
fun MereniApp(
    appVersion: String,
    pasportKeys: List<PasportKey>,
    initialCount: Int,
    onSave: (coSeMeri: String, odkudKam: String, casMereni: String) -> Int,
) {
    var activeField by remember { mutableStateOf(ActiveField.CO_SE_MERI) }
    var odkudKamSide by remember { mutableStateOf(OdkudKamSide.ODKUD) }
    val coSeMeri = remember { mutableStateListOf<String>() }
    val odkud = remember { mutableStateListOf<String>() }
    val kam = remember { mutableStateListOf<String>() }
    val casMereni = remember { mutableStateListOf<String>() }
    var recordCount by remember { mutableIntStateOf(initialCount) }

    fun clearAll() {
        coSeMeri.clear()
        odkud.clear()
        kam.clear()
        casMereni.clear()
        odkudKamSide = OdkudKamSide.ODKUD
        activeField = ActiveField.CO_SE_MERI
    }

    fun formatOdkudKam(): String {
        val left = odkud.joinToString(" ")
        val right = kam.joinToString(" ")
        return when {
            left.isNotEmpty() && right.isNotEmpty() -> "$left – $right"
            left.isNotEmpty() -> left
            else -> right
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MereniColors.BackgroundTop, MereniColors.BackgroundBottom)
                )
            )
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Měření",
                    color = MereniColors.Text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "v$appVersion",
                    color = MereniColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "mereni.csv • $recordCount záznamů",
                    color = MereniColors.TextMuted,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FieldPanel(
                    title = "CO SE MĚŘÍ",
                    selected = activeField == ActiveField.CO_SE_MERI,
                    onClick = { activeField = ActiveField.CO_SE_MERI },
                    modifier = Modifier.weight(1.1f),
                ) {
                    ChipRow(coSeMeri) { coSeMeri.removeAt(it) }
                }

                FieldPanel(
                    title = "ODKUD – KAM",
                    selected = activeField == ActiveField.ODKUD_KAM,
                    onClick = { activeField = ActiveField.ODKUD_KAM },
                    modifier = Modifier.weight(1.4f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        OdkudKamSlot(
                            placeholder = "obdélník 1",
                            items = odkud,
                            highlighted = activeField == ActiveField.ODKUD_KAM &&
                                odkudKamSide == OdkudKamSide.ODKUD,
                            onSelect = {
                                activeField = ActiveField.ODKUD_KAM
                                odkudKamSide = OdkudKamSide.ODKUD
                            },
                            onRemove = { odkud.removeAt(it) },
                        )
                        DashSeparator()
                        OdkudKamSlot(
                            placeholder = "obdélník 2",
                            items = kam,
                            highlighted = activeField == ActiveField.ODKUD_KAM &&
                                odkudKamSide == OdkudKamSide.KAM,
                            onSelect = {
                                activeField = ActiveField.ODKUD_KAM
                                odkudKamSide = OdkudKamSide.KAM
                            },
                            onRemove = { kam.removeAt(it) },
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            activeField = ActiveField.ODKUD_KAM
                            odkudKamSide = OdkudKamSide.ODKUD
                        }) {
                            Text(
                                text = "← ODKUD",
                                color = if (odkudKamSide == OdkudKamSide.ODKUD) MereniColors.Accent
                                else MereniColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        TextButton(onClick = {
                            activeField = ActiveField.ODKUD_KAM
                            odkudKamSide = OdkudKamSide.KAM
                        }) {
                            Text(
                                text = "KAM →",
                                color = if (odkudKamSide == OdkudKamSide.KAM) MereniColors.Accent
                                else MereniColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                FieldPanel(
                    title = "ČAS MĚŘENÍ",
                    selected = activeField == ActiveField.CAS_MERENI,
                    onClick = { activeField = ActiveField.CAS_MERENI },
                    modifier = Modifier.weight(0.8f),
                ) {
                    ChipRow(casMereni) { casMereni.removeAt(it) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val co = coSeMeri.joinToString(" ")
                        val od = formatOdkudKam()
                        val cas = casMereni.joinToString("")
                        if (co.isNotBlank() || od.isNotBlank() || cas.isNotBlank()) {
                            recordCount = onSave(co, od, cas)
                            clearAll()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MereniColors.Accent,
                        contentColor = MereniColors.BackgroundTop,
                    ),
                ) {
                    Text("Uložit záznam", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { clearAll() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MereniColors.SurfaceAlt,
                        contentColor = MereniColors.Text,
                    ),
                ) {
                    Text("Vymazat")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FieldKeyboard(
                activeField = activeField,
                odkudKamSide = odkudKamSide,
                pasportKeys = pasportKeys,
                onPasportKey = { key ->
                    when (odkudKamSide) {
                        OdkudKamSide.ODKUD -> odkud.add(key.label)
                        OdkudKamSide.KAM -> kam.add(key.label)
                    }
                },
                onTextKey = { label ->
                    when (activeField) {
                        ActiveField.CO_SE_MERI -> coSeMeri.add(label)
                        ActiveField.CAS_MERENI -> casMereni.add(label)
                        ActiveField.ODKUD_KAM -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OdkudKamSlot(
    placeholder: String,
    items: List<String>,
    highlighted: Boolean,
    onSelect: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) MereniColors.SurfaceAlt else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(2.dp)
    ) {
        if (items.isEmpty()) {
            Text(
                text = placeholder,
                color = MereniColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            items.forEachIndexed { index, label ->
                ChipToken(label = label, onRemove = { onRemove(index) })
            }
        }
    }
}

@Composable
private fun ChipRow(items: List<String>, onRemove: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (items.isEmpty()) {
            Text(
                text = "klepni klávesu",
                color = MereniColors.TextMuted,
                fontSize = 12.sp,
            )
        } else {
            items.forEachIndexed { index, label ->
                ChipToken(label = label, onRemove = { onRemove(index) })
            }
        }
    }
}
