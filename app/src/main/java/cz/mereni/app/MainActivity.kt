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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = MeasurementStore(this)
        store.ensureHeader()
        val version = BuildConfig.VERSION_NAME
        val initial = PasportRepository.load(this, version)

        setContent {
            val scope = rememberCoroutineScope()
            var load by remember { mutableStateOf(initial) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                scope.launch(Dispatchers.IO) {
                    val again = PasportRepository.reload(this@MainActivity, version)
                    withContext(Dispatchers.Main) { load = again }
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT in 23..32) {
                    val ok = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!ok) {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else if (!load.fromDeviceSqlite) {
                        val again = withContext(Dispatchers.IO) {
                            PasportRepository.reload(this@MainActivity, version)
                        }
                        if (again.fromDeviceSqlite) load = again
                    }
                } else if (!load.fromDeviceSqlite) {
                    val again = withContext(Dispatchers.IO) {
                        PasportRepository.reload(this@MainActivity, version)
                    }
                    if (again.fromDeviceSqlite) load = again
                }
            }

            MereniApp(
                appVersion = version,
                load = load,
                onLoadChange = { load = it },
                initialCount = store.count(),
                onSave = { udu, pole1, pole2, cas ->
                    store.append(udu, pole1, pole2, cas)
                    store.count()
                },
                onPersistUri = { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) { }
                    withContext(Dispatchers.IO) {
                        PasportRepository.loadFromUri(this@MainActivity, uri)
                    }
                },
                onReload = {
                    withContext(Dispatchers.IO) {
                        PasportRepository.reload(this@MainActivity, version)
                    }
                },
                onKeysForStation = { station, fallback ->
                    withContext(Dispatchers.IO) {
                        PasportRepository.keysForStation(this@MainActivity, station, fallback)
                    }
                },
            )
        }
    }
}

@Composable
fun MereniApp(
    appVersion: String,
    load: PasportLoadResult,
    onLoadChange: (PasportLoadResult) -> Unit,
    initialCount: Int,
    onSave: (udu: String, pole1: String, pole2: String, casMereni: String) -> Int,
    onPersistUri: suspend (Uri) -> PasportLoadResult,
    onReload: suspend () -> PasportLoadResult,
    onKeysForStation: suspend (Station?, List<PasportKey>) -> List<PasportKey>,
) {
    val scope = rememberCoroutineScope()
    val pasport = load.data
    var activeField by remember { mutableStateOf(ActiveField.POLE1) }
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
        onLoadChange(result)
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
        stationKeys = emptyList()
        scope.launch {
            val keys = onKeysForStation(station, pasport.keys)
            stationKeys = keys
            keysLoading = false
        }
    }

    val pickPasport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { onPersistUri(uri) }
                    .onSuccess { applyLoad(it) }
                    .onFailure { e ->
                        onLoadChange(
                            PasportLoadResult(
                                data = pasport,
                                fromDeviceSqlite = false,
                                sourceLabel = uri.toString(),
                                error = e.message ?: "Nepodařilo se načíst SQLite",
                            )
                        )
                    }
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
            // Horní lišta — kompaktní, vyhledávač v dialogu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Měření",
                    color = MereniColors.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "v$appVersion", color = MereniColors.Accent, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(12.dp))
                StationSearchPicker(
                    stations = pasport.stations,
                    selected = selectedStation,
                    onSelect = { selectStation(it) },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "mereni.csv • $recordCount",
                    color = MereniColors.TextMuted,
                    fontSize = 12.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        load.fromDeviceSqlite && selectedStation != null ->
                            "Pasport OK · $keyStats"
                        load.fromDeviceSqlite ->
                            "Pasport OK · ${load.stats.ifBlank { load.sourceLabel }}"
                        else ->
                            "Pasport chybí — ${PasportSqliteLoader.DB_FILE_NAME} v Download, nebo Vybrat"
                    },
                    color = if (load.fromDeviceSqlite) MereniColors.Kolej else MereniColors.Danger,
                    fontSize = 11.sp,
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
                TextButton(onClick = {
                    scope.launch { applyLoad(onReload()) }
                }) {
                    Text("Obnovit", color = MereniColors.TextMuted, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3 vyšší pole — chipy scrollovatelné
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FieldPanel(
                    selected = activeField == ActiveField.POLE1,
                    onClick = { activeField = ActiveField.POLE1 },
                    modifier = Modifier.weight(1.25f),
                ) {
                    ScrollableChips(pole1) { pole1.removeAt(it) }
                }
                FieldPanel(
                    selected = activeField == ActiveField.POLE2,
                    onClick = { activeField = ActiveField.POLE2 },
                    modifier = Modifier.weight(1f),
                ) {
                    ScrollableChips(pole2) { pole2.removeAt(it) }
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            when {
                selectedStation == null && activeField != ActiveField.CAS -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(MereniColors.Surface)
                    ) {
                        Text("Vyber stanici (tlačítko nahoře)", color = MereniColors.TextMuted)
                    }
                }
                keysLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(MereniColors.Surface)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MereniColors.Accent)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Načítám koleje / spojky / výhybky…", color = MereniColors.TextMuted)
                        }
                    }
                }
                else -> {
                    FieldKeyboard(
                        activeField = activeField,
                        pasportKeys = stationKeys,
                        hour = hour,
                        minute = minute,
                        onPasportKey = { key ->
                            when (activeField) {
                                ActiveField.POLE1 -> pole1.add(key.label)
                                ActiveField.POLE2 -> pole2.add(key.label)
                                ActiveField.CAS -> Unit
                            }
                        },
                        onHourChange = { hour = it; timeChosen = true },
                        onMinuteChange = { minute = it; timeChosen = true },
                        onUseNow = { useNow() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Chipy v poli — vodorovný i svislý scroll podle potřeby. */
@Composable
private fun ScrollableChips(items: List<String>, onRemove: (Int) -> Unit) {
    if (items.isEmpty()) {
        Text(text = "klepni klávesu", color = MereniColors.TextMuted, fontSize = 13.sp)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, label ->
                    ChipToken(label = label, onRemove = { onRemove(index) })
                }
            }
        }
    }
}
