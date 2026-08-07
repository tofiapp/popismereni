package cz.mereni.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cz.mereni.app.data.MeasurementStore
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind
import cz.mereni.app.data.PasportLoadResult
import cz.mereni.app.data.PasportRepository
import cz.mereni.app.data.PasportSqliteLoader
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

        // Povolení čtení Download (API ≤ 32) — ať se DB najde sama
        if (Build.VERSION.SDK_INT in 23..32) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 42)
            }
        }

        val initial = PasportRepository.load(this, version)
        setContent {
            MereniApp(
                appVersion = version,
                initialLoad = initial,
                initialCount = store.count(),
                onSave = { udu, pole1, pole2, cas ->
                    store.append(udu, pole1, pole2, cas)
                    store.count()
                },
                onPersistUri = { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) { }
                    PasportRepository.loadFromUri(this, uri)
                },
                onReload = { PasportRepository.reload(this, version) },
                onKeysForStation = { station, fallback ->
                    PasportRepository.keysForStation(this, station, fallback)
                },
            )
        }
    }
}

@Composable
fun MereniApp(
    appVersion: String,
    initialLoad: PasportLoadResult,
    initialCount: Int,
    onSave: (udu: String, pole1: String, pole2: String, casMereni: String) -> Int,
    onPersistUri: (Uri) -> PasportLoadResult,
    onReload: () -> PasportLoadResult,
    onKeysForStation: (Station?, List<PasportKey>) -> List<PasportKey>,
) {
    var load by remember { mutableStateOf(initialLoad) }
    var pasport by remember { mutableStateOf(initialLoad.data) }
    var activeField by remember { mutableStateOf(ActiveField.POLE1) }
    // Nestartuj s první stanicí — uživatel vyhledá (rychlejší start)
    var selectedStation by remember { mutableStateOf<Station?>(null) }
    var stationKeys by remember { mutableStateOf<List<PasportKey>>(emptyList()) }
    var keysLoading by remember { mutableStateOf(false) }
    val pole1 = remember { mutableStateListOf<String>() }
    val pole2 = remember { mutableStateListOf<String>() }
    var recordCount by remember { mutableIntStateOf(initialCount) }

    val now = remember { Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(now.get(Calendar.MINUTE)) }
    var timeChosen by remember { mutableStateOf(false) }

    fun applyLoad(result: PasportLoadResult) {
        load = result
        pasport = result.data
        selectedStation = null
        stationKeys = emptyList()
        pole1.clear()
        pole2.clear()
    }

    fun selectStation(station: Station) {
        selectedStation = station
        pole1.clear()
        pole2.clear()
        keysLoading = true
        stationKeys = onKeysForStation(station, pasport.keys)
        keysLoading = false
    }

    // Po startu zkus ještě jednou najít DB (po udělení oprávnění)
    LaunchedEffect(Unit) {
        if (!load.fromDeviceSqlite) {
            val again = onReload()
            if (again.fromDeviceSqlite) applyLoad(again)
        }
    }

    val pickPasport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching { onPersistUri(uri) }
                .onSuccess { applyLoad(it) }
                .onFailure { e ->
                    load = PasportLoadResult(
                        data = pasport,
                        fromDeviceSqlite = false,
                        sourceLabel = uri.toString(),
                        error = e.message ?: "Nepodařilo se načíst SQLite",
                    )
                }
        }
    }

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

    val keyStats = remember(stationKeys) {
        val k = stationKeys.count { it.kind == PasportKind.KOLEJ }
        val s = stationKeys.count { it.kind == PasportKind.SPOJKA }
        val v = stationKeys.count { it.kind == PasportKind.VYHYBKA }
        "koleje $k · spojky $s · výhybky $v"
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
                Spacer(modifier = Modifier.width(12.dp))
                StationSearchPicker(
                    stations = pasport.stations,
                    selected = selectedStation,
                    onSelect = { selectStation(it) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "mereni.csv • $recordCount",
                    color = MereniColors.TextMuted,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = buildString {
                        if (load.fromDeviceSqlite) {
                            append("Pasport OK · ${load.sourceLabel}")
                            if (load.stats.isNotBlank()) append(" · ${load.stats}")
                            if (selectedStation != null) append(" · $keyStats")
                        } else {
                            append("Pasport nenalezen — dej ${PasportSqliteLoader.DB_FILE_NAME} do Download")
                        }
                    },
                    color = if (load.fromDeviceSqlite) MereniColors.Kolej else MereniColors.Danger,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    pickPasport.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/x-sqlite3",
                            "application/vnd.sqlite3",
                            "*/*",
                        )
                    )
                }) {
                    Text("Vybrat", color = MereniColors.Accent, fontSize = 12.sp)
                }
                TextButton(onClick = { applyLoad(onReload()) }) {
                    Text("Obnovit", color = MereniColors.TextMuted, fontSize = 12.sp)
                }
            }
            if (!load.fromDeviceSqlite && load.error != null) {
                Text(
                    text = load.error ?: "",
                    color = MereniColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (keysLoading) {
                Text("Načítám klávesy…", color = MereniColors.TextMuted, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                        Text(text = "čas", color = MereniColors.TextMuted, fontSize = 13.sp)
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

            if (selectedStation == null && activeField != ActiveField.CAS) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(MereniColors.Surface)
                ) {
                    Text(
                        text = "Nejdřív vyhledej a vyber stanici",
                        color = MereniColors.TextMuted,
                        fontSize = 15.sp,
                    )
                }
            } else {
                FieldKeyboard(
                    activeField = activeField,
                    pasportKeys = stationKeys,
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
            Text(text = "klepni klávesu", color = MereniColors.TextMuted, fontSize = 13.sp)
        } else {
            items.forEachIndexed { index, label ->
                ChipToken(label = label, onRemove = { onRemove(index) })
            }
        }
    }
}
