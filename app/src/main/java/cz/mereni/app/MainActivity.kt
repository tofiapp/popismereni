package cz.mereni.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mereni.app.data.MeasurementStore
import cz.mereni.app.data.PasportData
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportRepository
import cz.mereni.app.data.Station
import cz.mereni.app.ui.ChipToken
import cz.mereni.app.ui.FieldKeyboard
import cz.mereni.app.ui.FieldPanel
import cz.mereni.app.ui.MereniColors
import cz.mereni.app.ui.StationSearchPicker
import java.util.Calendar

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
                pasport = pasport,
                initialCount = store.count(),
                onSave = { udu, pole1, pole2, cas ->
                    store.append(udu, pole1, pole2, cas)
                    store.count()
                },
            )
        }
    }
}

@Composable
fun MereniApp(
    appVersion: String,
    pasport: PasportData,
    initialCount: Int,
    onSave: (udu: String, pole1: String, pole2: String, casMereni: String) -> Int,
) {
    var activeField by remember { mutableStateOf(ActiveField.POLE1) }
    var selectedStation by remember { mutableStateOf<Station?>(pasport.stations.firstOrNull()) }
    val pole1 = remember { mutableStateListOf<String>() }
    val pole2 = remember { mutableStateListOf<String>() }
    var recordCount by remember { mutableIntStateOf(initialCount) }

    val now = remember { Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(now.get(Calendar.MINUTE)) }
    var timeChosen by remember { mutableStateOf(false) }

    fun useNow() {
        val c = Calendar.getInstance()
        hour = c.get(Calendar.HOUR_OF_DAY)
        minute = c.get(Calendar.MINUTE)
        timeChosen = true
    }

    fun clearAll() {
        pole1.clear()
        pole2.clear()
        timeChosen = false
        useNow()
        timeChosen = false
        activeField = ActiveField.POLE1
    }

    fun timeLabel(): String = "%02d:%02d".format(hour, minute)

    val filteredKeys = remember(pasport.keys, selectedStation) {
        val udu = selectedStation?.udu
        if (udu.isNullOrBlank()) pasport.keys
        else pasport.keys.filter { it.udu == null || it.udu == udu }
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
                Spacer(modifier = Modifier.width(16.dp))
                StationSearchPicker(
                    stations = pasport.stations,
                    selected = selectedStation,
                    onSelect = {
                        selectedStation = it
                        pole1.clear()
                        pole2.clear()
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "mereni.csv • $recordCount",
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
                    selected = activeField == ActiveField.POLE1,
                    onClick = { activeField = ActiveField.POLE1 },
                    modifier = Modifier.weight(1.25f),
                ) {
                    ChipRow(pole1) { pole1.removeAt(it) }
                }

                FieldPanel(
                    selected = activeField == ActiveField.POLE2,
                    onClick = { activeField = ActiveField.POLE2 },
                    modifier = Modifier.weight(1f),
                ) {
                    ChipRow(pole2) { pole2.removeAt(it) }
                }

                FieldPanel(
                    selected = activeField == ActiveField.CAS,
                    onClick = {
                        activeField = ActiveField.CAS
                        if (!timeChosen) useNow()
                    },
                    modifier = Modifier.weight(0.7f),
                ) {
                    if (timeChosen) {
                        ChipToken(label = timeLabel(), onRemove = { timeChosen = false })
                    } else {
                        Text(
                            text = "čas",
                            color = MereniColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val cas = if (timeChosen) timeLabel() else ""
                        if (pole1.isNotEmpty() || pole2.isNotEmpty() || cas.isNotBlank()) {
                            recordCount = onSave(
                                selectedStation?.udu.orEmpty(),
                                pole1.joinToString(" "),
                                pole2.joinToString(" "),
                                cas,
                            )
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
                pasportKeys = filteredKeys,
                hour = hour,
                minute = minute,
                onPasportKey = { key: PasportKey ->
                    when (activeField) {
                        ActiveField.POLE1 -> pole1.add(key.label)
                        ActiveField.POLE2 -> pole2.add(key.label)
                        ActiveField.CAS -> Unit
                    }
                },
                onHourChange = {
                    hour = it
                    timeChosen = true
                },
                onMinuteChange = {
                    minute = it
                    timeChosen = true
                },
                onUseNow = { useNow() },
                modifier = Modifier.fillMaxWidth(),
            )
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
                fontSize = 13.sp,
            )
        } else {
            items.forEachIndexed { index, label ->
                ChipToken(label = label, onRemove = { onRemove(index) })
            }
        }
    }
}
