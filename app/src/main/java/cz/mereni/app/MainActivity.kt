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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cz.mereni.app.data.MeasurementStore
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind
import cz.mereni.app.data.PasportLoadResult
import cz.mereni.app.data.PasportRepository
import cz.mereni.app.data.PasportSqliteLoader
import cz.mereni.app.data.SelectedToken
import cz.mereni.app.data.Station
import cz.mereni.app.ui.ActiveFieldCaption
import cz.mereni.app.ui.ChipRow
import cz.mereni.app.ui.FieldKeyboard
import cz.mereni.app.ui.FieldPanel
import cz.mereni.app.ui.MereniColors
import cz.mereni.app.ui.PasportSettingsButton
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
                onSave = { udu, pole1, pole2, cas, poznamka ->
                    store.append(udu, pole1, pole2, cas, poznamka)
                    store.count()
                },
                onUsedPole1Labels = { udu ->
                    withContext(Dispatchers.IO) {
                        store.usedPole1LabelsForUdu(udu)
                    }
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
    onSave: (udu: String, pole1: String, pole2: String, casMereni: String, poznamka: String) -> Int,
    onUsedPole1Labels: suspend (String) -> Set<String>,
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
    var pasportLoading by remember { mutableStateOf(false) }
    var pasportLoadingMsg by remember { mutableStateOf("") }
    val pole1 = remember { mutableStateListOf<SelectedToken>() }
    val pole2 = remember { mutableStateListOf<SelectedToken>() }
    var nextTokenId by remember { mutableLongStateOf(1L) }
    var note by remember { mutableStateOf("") }
    var recordCount by remember { mutableIntStateOf(initialCount) }
    var reorderPole1 by remember { mutableStateOf(false) }
    var reorderPole2 by remember { mutableStateOf(false) }
    var usedPole1Labels by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun refreshUsedLabels(udu: String?) {
        if (udu.isNullOrBlank()) {
            usedPole1Labels = emptySet()
            return
        }
        scope.launch {
            usedPole1Labels = onUsedPole1Labels(udu)
        }
    }

    val now = remember { Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(now.get(Calendar.MINUTE)) }
    var timeChosen by remember { mutableStateOf(false) }

    fun nextId(): Long {
        val id = nextTokenId
        nextTokenId = id + 1
        return id
    }

    fun applyLoad(result: PasportLoadResult) {
        onLoadChange(result)
        selectedStation = null
        stationKeys = emptyList()
        pole1.clear()
        pole2.clear()
        note = ""
        reorderPole1 = false
        reorderPole2 = false
        usedPole1Labels = emptySet()
        pasportLoading = false
        pasportLoadingMsg = ""
    }

    fun selectStation(station: Station) {
        selectedStation = station
        pole1.clear()
        pole2.clear()
        reorderPole1 = false
        reorderPole2 = false
        keysLoading = true
        stationKeys = emptyList()
        refreshUsedLabels(station.udu)
        scope.launch {
            val keys = onKeysForStation(station, pasport.keys)
            stationKeys = keys
            keysLoading = false
        }
    }

    fun moveItem(list: MutableList<SelectedToken>, from: Int, to: Int) {
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
    }

    val pickPasport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pasportLoading = true
            pasportLoadingMsg = "Načítám ${PasportSqliteLoader.DB_FILE_NAME}…"
            scope.launch {
                runCatching { onPersistUri(uri) }
                    .onSuccess { applyLoad(it) }
                    .onFailure { e ->
                        pasportLoading = false
                        pasportLoadingMsg = ""
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
        note = ""
        reorderPole1 = false
        reorderPole2 = false
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

    val pasportStatusText = when {
        pasportLoading -> pasportLoadingMsg.ifBlank { "Načítám pasport…" }
        keysLoading -> "Načítám koleje / spojky / výhybky…"
        load.fromDeviceSqlite && selectedStation != null ->
            "Pasport OK · $keyStats"
        load.fromDeviceSqlite ->
            "Pasport OK · ${load.stats.ifBlank { load.sourceLabel }}"
        else ->
            "Pasport chybí — ${PasportSqliteLoader.DB_FILE_NAME} v Download, nebo Vybrat"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MereniColors.BackgroundTop, MereniColors.BackgroundBottom)
                )
            )
            .systemBarsPadding()
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
                Spacer(modifier = Modifier.width(14.dp))
                ActiveFieldCaption(activeField = activeField)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "mereni.csv • $recordCount",
                    color = MereniColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                PasportSettingsButton(
                    statusText = pasportStatusText,
                    statusOk = load.fromDeviceSqlite && !pasportLoading,
                    loading = pasportLoading || keysLoading,
                    onPick = {
                        pickPasport.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/x-sqlite3",
                                "application/vnd.sqlite3",
                                "*/*",
                            )
                        )
                    },
                    onReload = {
                        pasportLoading = true
                        pasportLoadingMsg = "Obnovuji pasport…"
                        scope.launch { applyLoad(onReload()) }
                    },
                )
            }

            if (pasportLoading || keysLoading) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pasportStatusText,
                    color = MereniColors.Accent,
                    fontSize = 11.sp,
                )
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
                    showReorderToggle = pole1.size > 1,
                    reorderMode = reorderPole1,
                    onReorderToggle = { reorderPole1 = !reorderPole1 },
                ) {
                    if (pole1.isEmpty()) {
                        Text("klepni klávesu", color = MereniColors.TextMuted, fontSize = 13.sp)
                    } else {
                        ScrollableChips(
                            items = pole1,
                            reorderMode = reorderPole1,
                            onRemove = { pole1.removeAt(it) },
                            onMove = { from, to -> moveItem(pole1, from, to) },
                        )
                    }
                }
                FieldPanel(
                    selected = activeField == ActiveField.POLE2,
                    onClick = { activeField = ActiveField.POLE2 },
                    modifier = Modifier.weight(1f),
                    showReorderToggle = pole2.size > 1,
                    reorderMode = reorderPole2,
                    onReorderToggle = { reorderPole2 = !reorderPole2 },
                ) {
                    if (pole2.isEmpty()) {
                        Text("klepni klávesu", color = MereniColors.TextMuted, fontSize = 13.sp)
                    } else {
                        ScrollableChips(
                            items = pole2,
                            reorderMode = reorderPole2,
                            onRemove = { pole2.removeAt(it) },
                            onMove = { from, to -> moveItem(pole2, from, to) },
                        )
                    }
                }
                FieldPanel(
                    selected = activeField == ActiveField.CAS,
                    onClick = { activeField = ActiveField.CAS },
                    modifier = Modifier.weight(0.7f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (timeChosen) {
                        Text(
                            text = timeLabel(),
                            color = MereniColors.Text,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "—:—",
                            color = MereniColors.TextMuted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Poznámka + Uložit / Vymazat uprostřed (nad klávesnicí) — mimo systémové menu tabletu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MereniColors.Text, fontSize = 14.sp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MereniColors.Surface)
                        .border(1.dp, MereniColors.ChipBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (note.isEmpty()) {
                                Text("Poznámka…", color = MereniColors.TextMuted, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
                Button(
                    onClick = {
                        val cas = if (timeChosen) timeLabel() else ""
                        if (pole1.isNotEmpty() || pole2.isNotEmpty() || cas.isNotBlank() || note.isNotBlank()) {
                            val udu = selectedStation?.udu.orEmpty()
                            recordCount = onSave(
                                udu,
                                pole1.joinToString(" ") { it.label },
                                pole2.joinToString(" ") { it.label },
                                cas,
                                note.trim(),
                            )
                            clearAll()
                            refreshUsedLabels(udu)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MereniColors.Accent,
                        contentColor = MereniColors.BackgroundTop,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text("Uložit", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Button(
                    onClick = { clearAll() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MereniColors.SurfaceAlt,
                        contentColor = MereniColors.Text,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text("Vymazat", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                selectedStation == null && activeField != ActiveField.CAS -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MereniColors.Surface, RoundedCornerShape(12.dp))
                    ) {
                        Text("Vyber stanici (tlačítko nahoře)", color = MereniColors.TextMuted)
                    }
                }
                keysLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MereniColors.Surface, RoundedCornerShape(12.dp))
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
                        usedPole1Labels = usedPole1Labels,
                        hour = hour,
                        minute = minute,
                        timeChosen = timeChosen,
                        onPasportKey = { key ->
                            val token = SelectedToken(nextId(), key.label, key.kind)
                            when (activeField) {
                                ActiveField.POLE1 -> pole1.add(token)
                                ActiveField.POLE2 -> pole2.add(token)
                                ActiveField.CAS -> Unit
                            }
                        },
                        onExtraLabel = { label ->
                            pole2.add(SelectedToken(nextId(), label, PasportKind.VYHYBKA))
                            activeField = ActiveField.POLE2
                        },
                        onHourChange = { hour = it; timeChosen = true },
                        onMinuteChange = { minute = it; timeChosen = true },
                        onUseNow = { useNow() },
                        onClearTime = { timeChosen = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/** Chipy v poli — scroll + volitelné šipky po klepnutí na <>. */
@Composable
private fun ScrollableChips(
    items: List<SelectedToken>,
    reorderMode: Boolean,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
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
            ChipRow(
                items = items,
                reorderMode = reorderMode,
                onRemove = onRemove,
                onMove = onMove,
            )
        }
    }
}
