package cz.mereni.app

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cz.mereni.app.data.MeasurementStore
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind
import cz.mereni.app.data.PasportLoadResult
import cz.mereni.app.data.PasportRepository
import cz.mereni.app.data.PasportSqliteLoader
import cz.mereni.app.data.SafUris
import cz.mereni.app.data.SelectedToken
import cz.mereni.app.data.Station
import cz.mereni.app.ui.ActiveFieldCaption
import cz.mereni.app.ui.ChipRow
import cz.mereni.app.ui.CustomTokenDialog
import cz.mereni.app.ui.FieldKeyboard
import cz.mereni.app.ui.FieldPanel
import cz.mereni.app.ui.MereniColors
import cz.mereni.app.ui.PasportSettingsButton
import cz.mereni.app.ui.StationSearchPicker
import java.nio.charset.Charset
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** URI ze Sdílet / Otevřít v (OneDrive často grantuje jen touto cestou). */
    private val externalUris = MutableSharedFlow<Uri>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = MeasurementStore(this)
        store.ensureHeader()
        val version = BuildConfig.VERSION_NAME
        val initial = PasportRepository.load(this, version)
        extractIncomingUri(intent)?.let { externalUris.tryEmit(it) }

        setContent {
            val scope = rememberCoroutineScope()
            var load by remember { mutableStateOf(initial) }
            var bootCsvMessage by remember { mutableStateOf<String?>(null) }
            var bootRecordCount by remember { mutableIntStateOf(store.count()) }

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

            LaunchedEffect(Unit) {
                externalUris.collectLatest { uri ->
                    // Číst hned na Main — OneDrive grant je krátký
                    val bytes = runCatching { SafUris.readAllBytes(contentResolver, uri) }
                    val name = runCatching {
                        contentResolver.query(uri, null, null, null, null)?.use { c ->
                            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                        }
                    }.getOrNull().orEmpty().lowercase()
                    val mime = contentResolver.getType(uri).orEmpty().lowercase()
                    val asCsv = name.endsWith(".csv") || "csv" in mime ||
                        (mime.startsWith("text/") && !name.contains("pasport"))
                    val asSqlite = name.endsWith(".sqlite") || name.endsWith(".db") ||
                        "sqlite" in mime || name.contains("pasport")
                    bytes.onFailure { e ->
                        bootCsvMessage = e.message ?: SafUris.denyMessage(uri)
                        return@collectLatest
                    }
                    val data = bytes.getOrThrow()
                    when {
                        asCsv || (!asSqlite && mime.startsWith("text/")) -> {
                            runCatching {
                                withContext(Dispatchers.IO) { store.importFromBytes(data) }
                            }.onSuccess { n ->
                                bootRecordCount = n
                                bootCsvMessage = "CSV ze sdílení načteno · $n záznamů"
                            }.onFailure { e ->
                                bootCsvMessage = e.message ?: "Import CSV selhal"
                            }
                        }
                        else -> {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    PasportRepository.loadFromBytes(this@MainActivity, data, uri)
                                }
                            }.onSuccess { load = it }
                                .onFailure { e ->
                                    bootCsvMessage = e.message ?: SafUris.denyMessage(uri)
                                }
                        }
                    }
                }
            }

            MereniApp(
                appVersion = version,
                load = load,
                onLoadChange = { load = it },
                initialCount = bootRecordCount,
                externalStatusMessage = bootCsvMessage,
                onSave = { udu, pole1, pole2, cas, poznamka ->
                    store.append(udu, pole1, pole2, cas, poznamka)
                    store.count()
                },
                onUsedLabels = { udu ->
                    withContext(Dispatchers.IO) {
                        store.usedLabelsForUdu(udu)
                    }
                },
                onPersistBytes = { bytes, uri ->
                    withContext(Dispatchers.IO) {
                        PasportRepository.loadFromBytes(this@MainActivity, bytes, uri)
                    }
                },
                onExportCsv = { uri ->
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            store.exportTo(out)
                        } ?: error("Nelze otevřít cíl pro zápis")
                    }
                },
                onImportBytes = { bytes ->
                    withContext(Dispatchers.IO) {
                        store.importFromBytes(bytes)
                    }
                },
                onShareCsv = {
                    store.ensureHeader()
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        store.csvFile,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, MeasurementStore.CSV_NAME)
                        putExtra(Intent.EXTRA_TITLE, MeasurementStore.CSV_NAME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newUri(
                            contentResolver,
                            MeasurementStore.CSV_NAME,
                            uri,
                        )
                    }
                    val chooser = Intent.createChooser(send, "Sdílet mereni.csv").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(chooser)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingUri(intent)?.let { externalUris.tryEmit(it) }
    }

    private fun extractIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                } ?: intent.data
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.firstOrNull()
            }
            else -> null
        }
    }
}

@Composable
fun MereniApp(
    appVersion: String,
    load: PasportLoadResult,
    onLoadChange: (PasportLoadResult) -> Unit,
    initialCount: Int,
    externalStatusMessage: String? = null,
    onSave: (udu: String, pole1: String, pole2: String, casMereni: String, poznamka: String) -> Int,
    onUsedLabels: suspend (String) -> Set<String>,
    onPersistBytes: suspend (ByteArray, Uri?) -> PasportLoadResult,
    onExportCsv: suspend (Uri) -> Unit,
    onImportBytes: suspend (ByteArray) -> Int,
    onShareCsv: () -> Unit,
    onReload: suspend () -> PasportLoadResult,
    onKeysForStation: suspend (Station?, List<PasportKey>) -> List<PasportKey>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pasport = load.data
    var activeField by remember { mutableStateOf(ActiveField.POLE1) }
    var stationA by remember { mutableStateOf<Station?>(null) }
    var stationB by remember { mutableStateOf<Station?>(null) }
    var keysA by remember { mutableStateOf<List<PasportKey>>(emptyList()) }
    var keysB by remember { mutableStateOf<List<PasportKey>>(emptyList()) }
    /** 0 = stanice A, 1 = stanice B (jen v dual režimu). */
    var activeSlot by remember { mutableIntStateOf(0) }
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
    var usedLabelsA by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usedLabelsB by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customDialogFor by remember { mutableStateOf<ActiveField?>(null) }
    var noteFocused by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(externalStatusMessage, initialCount) {
        if (externalStatusMessage != null) exportMessage = externalStatusMessage
        recordCount = initialCount
    }

    val dualMode = stationB != null
    val activeStation = if (activeSlot == 1 && stationB != null) stationB else stationA
    val stationKeys = if (activeSlot == 1 && dualMode) keysB else keysA
    /** CSV zašednutí jen pro aktivní vyhledávač (přesné UDU). */
    val usedLabels = if (activeSlot == 1 && dualMode) usedLabelsB else usedLabelsA
    /**
     * Právě v horních obdélnících z **tohoto** slotu — zašedlé a nelze znovu přidat.
     * Slot 1 a slot 2 se navzájem neblokují (stejná kolej 3a na obou OK).
     */
    val lockedLabels = (pole1 + pole2)
        .filter { !dualMode || it.fromSlot == activeSlot }
        .map { it.label }
        .toSet()

    fun refreshUsedLabels() {
        scope.launch {
            usedLabelsA = stationA?.udu?.takeIf { it.isNotBlank() }?.let { onUsedLabels(it) } ?: emptySet()
            usedLabelsB = stationB?.udu?.takeIf { it.isNotBlank() }?.let { onUsedLabels(it) } ?: emptySet()
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
        stationA = null
        stationB = null
        keysA = emptyList()
        keysB = emptyList()
        activeSlot = 0
        pole1.clear()
        pole2.clear()
        note = ""
        reorderPole1 = false
        reorderPole2 = false
        usedLabelsA = emptySet()
        usedLabelsB = emptySet()
        pasportLoading = false
        pasportLoadingMsg = ""
    }

    fun selectStationA(station: Station, clearFields: Boolean = true) {
        stationA = station
        activeSlot = 0
        if (clearFields) {
            pole1.clear()
            pole2.clear()
            reorderPole1 = false
            reorderPole2 = false
        }
        keysLoading = true
        keysA = emptyList()
        scope.launch {
            keysA = onKeysForStation(station, pasport.keys)
            keysLoading = false
            refreshUsedLabels()
        }
    }

    fun selectStationB(station: Station) {
        stationB = station
        activeSlot = 1
        keysLoading = true
        keysB = emptyList()
        scope.launch {
            keysB = onKeysForStation(station, pasport.keys)
            keysLoading = false
            refreshUsedLabels()
        }
    }

    fun clearStationB() {
        stationB = null
        keysB = emptyList()
        activeSlot = 0
        refreshUsedLabels()
    }

    fun moveItem(list: MutableList<SelectedToken>, from: Int, to: Int) {
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
    }

    fun loadPasportFromUri(uri: Uri) {
        val bytes = runCatching {
            SafUris.readAllBytes(context.contentResolver, uri)
        }
        pasportLoading = true
        pasportLoadingMsg = "Načítám ${PasportSqliteLoader.DB_FILE_NAME}…"
        scope.launch {
            runCatching {
                val data = bytes.getOrThrow()
                onPersistBytes(data, uri)
            }.onSuccess { applyLoad(it) }
                .onFailure { e ->
                    pasportLoading = false
                    pasportLoadingMsg = ""
                    onLoadChange(
                        PasportLoadResult(
                            data = pasport,
                            fromDeviceSqlite = false,
                            sourceLabel = uri.toString(),
                            error = e.message ?: SafUris.denyMessage(uri),
                        )
                    )
                }
        }
    }

    val pickPasport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) loadPasportFromUri(uri)
    }

    val saveCsvAs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/*"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { onExportCsv(uri) }
                .onSuccess { exportMessage = "CSV uloženo" }
                .onFailure { e ->
                    exportMessage = e.message ?: "Uložení se nepovedlo"
                }
        }
    }

    fun importCsvFromBytes(bytes: ByteArray, sourceLabel: String) {
        scope.launch {
            runCatching {
                onImportBytes(bytes)
            }.onSuccess { n ->
                recordCount = n
                exportMessage = "CSV načteno ($sourceLabel) · $n záznamů"
                refreshUsedLabels()
            }.onFailure { e ->
                exportMessage = e.message ?: "Import CSV selhal"
            }
        }
    }

    fun importCsvFromUri(uri: Uri) {
        val bytes = runCatching { SafUris.readAllBytes(context.contentResolver, uri) }
        if (bytes.isFailure) {
            exportMessage = bytes.exceptionOrNull()?.message ?: SafUris.denyMessage(uri)
            return
        }
        importCsvFromBytes(bytes.getOrThrow(), "Files")
    }

    fun importCsvFromClipboard() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = cm?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            exportMessage = "Schránka je prázdná"
            return
        }
        val item = clip.getItemAt(0)
        val text = item.coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            importCsvFromBytes(text.toByteArray(Charset.forName("UTF-8")), "schránka")
            return
        }
        // Některé appky dají do schránky URI souboru místo textu
        val uri = item.uri
        if (uri != null) {
            importCsvFromUri(uri)
            return
        }
        val desc = clip.description
        exportMessage = if (desc?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
            "Schránka neobsahuje text CSV"
        } else {
            "Ve schránce není text — v OneDrive/Excelu označ CSV a Kopírovat"
        }
    }

    val importCsv = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) importCsvFromUri(uri)
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
        load.fromDeviceSqlite && activeStation != null ->
            "Pasport OK · $keyStats" + if (dualMode) " · dual" else ""
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(10.dp))
                StationSearchPicker(
                    stations = pasport.stations,
                    selected = stationA,
                    onSelect = { selectStationA(it, clearFields = stationA == null) },
                    accentColor = MereniColors.Accent,
                    isKeySource = activeSlot == 0 && stationA != null,
                    slotLabel = if (dualMode) "1" else null,
                    onActivate = { activeSlot = 0 },
                    onOpenInSecond = { selectStationB(it) },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    ActiveFieldCaption(activeField = activeField)
                }
                if (dualMode) {
                    StationSearchPicker(
                        stations = pasport.stations,
                        selected = stationB,
                        onSelect = { selectStationB(it) },
                        accentColor = MereniColors.Dual,
                        isKeySource = activeSlot == 1,
                        slotLabel = "2",
                        onActivate = { activeSlot = 1 },
                        onClear = { clearStationB() },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                PasportSettingsButton(
                    statusText = pasportStatusText,
                    statusOk = load.fromDeviceSqlite && !pasportLoading,
                    loading = pasportLoading || keysLoading,
                    appVersion = appVersion,
                    recordCount = recordCount,
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
                    onImportCsv = {
                        exportMessage = null
                        importCsv.launch(
                            arrayOf(
                                "text/*",
                                "text/csv",
                                "text/comma-separated-values",
                                "application/csv",
                                "*/*",
                            )
                        )
                    },
                    onImportCsvClipboard = {
                        exportMessage = null
                        importCsvFromClipboard()
                    },
                    onImportCsvText = { text ->
                        exportMessage = null
                        val t = text.trim()
                        if (t.isEmpty()) {
                            exportMessage = "Vložený text je prázdný"
                        } else {
                            importCsvFromBytes(t.toByteArray(Charset.forName("UTF-8")), "vložený text")
                        }
                    },
                    onShareCsv = {
                        exportMessage = null
                        onShareCsv()
                        exportMessage = "Otevřen výběr aplikací (OneDrive, …)"
                    },
                    onSaveCsvAs = {
                        exportMessage = null
                        saveCsvAs.launch(MeasurementStore.CSV_NAME)
                    },
                    exportMessage = exportMessage,
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
                    accentColor = MereniColors.Kolej,
                    showReorderToggle = pole1.size > 1,
                    reorderMode = reorderPole1,
                    onReorderToggle = { reorderPole1 = !reorderPole1 },
                    showAddButton = true,
                    onAddClick = {
                        activeField = ActiveField.POLE1
                        customDialogFor = ActiveField.POLE1
                    },
                ) {
                    if (pole1.isEmpty()) {
                        Text("klepni klávesu", color = MereniColors.TextMuted, fontSize = 13.sp)
                    } else {
                        ScrollableChips(
                            items = pole1,
                            reorderMode = reorderPole1,
                            dashBetween = false,
                            onRemove = { pole1.removeAt(it) },
                            onMove = { from, to -> moveItem(pole1, from, to) },
                        )
                    }
                }
                FieldPanel(
                    selected = activeField == ActiveField.POLE2,
                    onClick = { activeField = ActiveField.POLE2 },
                    modifier = Modifier.weight(1f),
                    accentColor = MereniColors.Vyhybka,
                    showReorderToggle = pole2.size > 1,
                    reorderMode = reorderPole2,
                    onReorderToggle = { reorderPole2 = !reorderPole2 },
                    showAddButton = true,
                    onAddClick = {
                        activeField = ActiveField.POLE2
                        customDialogFor = ActiveField.POLE2
                    },
                ) {
                    if (pole2.isEmpty()) {
                        Text("klepni klávesu", color = MereniColors.TextMuted, fontSize = 13.sp)
                    } else {
                        ScrollableChips(
                            items = pole2,
                            reorderMode = reorderPole2,
                            dashBetween = true,
                            onRemove = { pole2.removeAt(it) },
                            onMove = { from, to -> moveItem(pole2, from, to) },
                        )
                    }
                }
                FieldPanel(
                    selected = activeField == ActiveField.CAS,
                    onClick = {
                        activeField = ActiveField.CAS
                        if (!timeChosen) useNow()
                    },
                    modifier = Modifier.weight(0.7f),
                    contentAlignment = Alignment.Center,
                    accentColor = MereniColors.Cas,
                ) {
                    if (timeChosen) {
                        Text(
                            text = timeLabel(),
                            color = MereniColors.Cas,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "—:—",
                            color = MereniColors.Cas.copy(alpha = 0.55f),
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
                val noteShape = RoundedCornerShape(8.dp)
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MereniColors.Text, fontSize = 14.sp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(noteShape)
                        .background(
                            if (noteFocused) MereniColors.Poznamka.copy(alpha = 0.14f)
                            else MereniColors.Surface
                        )
                        .border(
                            width = if (noteFocused) 2.dp else 1.dp,
                            color = if (noteFocused) MereniColors.Poznamka else MereniColors.ChipBorder,
                            shape = noteShape,
                        )
                        .onFocusChanged { noteFocused = it.isFocused }
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
                        val noteText = note.trim()
                        val hasContent = pole1.isNotEmpty() || pole2.isNotEmpty() ||
                            cas.isNotBlank() || noteText.isNotBlank()
                        if (!hasContent) return@Button

                        fun saveFor(
                            udu: String,
                            p1: List<SelectedToken>,
                            p2: List<SelectedToken>,
                            withMeta: Boolean,
                        ) {
                            if (p1.isEmpty() && p2.isEmpty() && !withMeta) return
                            if (udu.isBlank() && p1.isEmpty() && p2.isEmpty() && !withMeta) return
                            recordCount = onSave(
                                udu,
                                p1.joinToString(" ") { it.label },
                                p2.joinToString(" - ") { it.label },
                                if (withMeta) cas else "",
                                if (withMeta) noteText else "",
                            )
                        }

                        if (dualMode) {
                            val slots = listOf(
                                0 to stationA,
                                1 to stationB,
                            )
                            var metaDone = false
                            for ((slot, st) in slots) {
                                if (st == null) continue
                                val p1 = pole1.filter { it.fromSlot == slot }
                                val p2 = pole2.filter { it.fromSlot == slot }
                                val withMeta = !metaDone &&
                                    (p1.isNotEmpty() || p2.isNotEmpty() ||
                                        cas.isNotBlank() || noteText.isNotBlank())
                                if (p1.isEmpty() && p2.isEmpty() && !withMeta) continue
                                saveFor(st.udu, p1, p2, withMeta)
                                if (withMeta) metaDone = true
                            }
                            if (!metaDone && (cas.isNotBlank() || noteText.isNotBlank())) {
                                saveFor(stationA?.udu.orEmpty(), emptyList(), emptyList(), true)
                            }
                        } else {
                            saveFor(
                                stationA?.udu.orEmpty(),
                                pole1.toList(),
                                pole2.toList(),
                                true,
                            )
                        }
                        clearAll()
                        refreshUsedLabels()
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
                stationA == null && activeField != ActiveField.CAS -> {
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
                        usedLabels = usedLabels,
                        lockedLabels = lockedLabels,
                        hour = hour,
                        minute = minute,
                        timeChosen = timeChosen,
                        onPasportKey = { key ->
                            if (key.label !in lockedLabels) {
                                val token = SelectedToken(
                                    id = nextId(),
                                    label = key.label,
                                    kind = key.kind,
                                    fromSlot = if (dualMode) activeSlot else 0,
                                )
                                when (activeField) {
                                    ActiveField.POLE1 -> pole1.add(token)
                                    ActiveField.POLE2 -> pole2.add(token)
                                    ActiveField.CAS -> Unit
                                }
                            }
                        },
                        onExtraLabel = { label ->
                            if (label !in lockedLabels) {
                                pole2.add(
                                    SelectedToken(
                                        id = nextId(),
                                        label = label,
                                        kind = PasportKind.VYHYBKA,
                                        fromSlot = if (dualMode) activeSlot else 0,
                                    )
                                )
                                activeField = ActiveField.POLE2
                            }
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

        CustomTokenDialog(
            open = customDialogFor != null,
            title = when (customDialogFor) {
                ActiveField.POLE1 -> "Vlastní kolej / spojka"
                ActiveField.POLE2 -> "Vlastní od / do"
                else -> "Vlastní hodnota"
            },
            onDismiss = { customDialogFor = null },
            onConfirm = { label ->
                if (label !in lockedLabels) {
                    val token = SelectedToken(
                        id = nextId(),
                        label = label,
                        kind = when (customDialogFor) {
                            ActiveField.POLE2 -> PasportKind.VYHYBKA
                            else -> PasportKind.KOLEJ
                        },
                        custom = true,
                        fromSlot = if (dualMode) activeSlot else 0,
                    )
                    when (customDialogFor) {
                        ActiveField.POLE1 -> pole1.add(token)
                        ActiveField.POLE2 -> pole2.add(token)
                        else -> Unit
                    }
                }
            },
        )
    }
}

/** Chipy v poli — scroll + volitelné šipky po klepnutí na <>. */
@Composable
private fun ScrollableChips(
    items: List<SelectedToken>,
    reorderMode: Boolean,
    dashBetween: Boolean = false,
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
                dashBetween = dashBetween,
                onRemove = onRemove,
                onMove = onMove,
            )
        }
    }
}
