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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cz.mereni.app.data.CreateXlsxDocumentContract
import cz.mereni.app.data.CreateXlsxRequest
import cz.mereni.app.data.MeasurementStore
import cz.mereni.app.data.PasportKey
import cz.mereni.app.data.PasportKind
import cz.mereni.app.data.PasportLoadResult
import cz.mereni.app.data.PasportRepository
import cz.mereni.app.data.PasportSqliteLoader
import cz.mereni.app.data.SafUris
import cz.mereni.app.data.SelectedToken
import cz.mereni.app.data.Station
import cz.mereni.app.ui.ChipRow
import cz.mereni.app.ui.CustomTokenDialog
import cz.mereni.app.ui.FieldKeyboard
import cz.mereni.app.ui.FieldPanel
import cz.mereni.app.ui.MereniColors
import cz.mereni.app.ui.PasportSettingsButton
import cz.mereni.app.ui.StationSearchPicker
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = MeasurementStore(this)
        store.ensureReady()
        val version = BuildConfig.VERSION_NAME
        val initial = PasportRepository.load(this, version)

        setContent {
            val scope = rememberCoroutineScope()
            var load by remember { mutableStateOf(initial) }
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

            MereniApp(
                appVersion = version,
                load = load,
                onLoadChange = { load = it },
                initialCount = bootRecordCount,
                initialDayRecord = store.dayRecordNumber(),
                initialOneDriveSynced = store.isSyncedToOneDrive(),
                onSave = { stationName, udu, pole1, pole2, cas, poznamka ->
                    store.append(stationName, udu, pole1, pole2, cas, poznamka)
                    store.count() to store.dayRecordNumber()
                },
                onUsedLabels = { stationName ->
                    withContext(Dispatchers.IO) {
                        store.usedLabelsForStation(stationName)
                    }
                },
                onPersistBytes = { bytes, uri ->
                    withContext(Dispatchers.IO) {
                        PasportRepository.loadFromBytes(this@MainActivity, bytes, uri)
                    }
                },
                onPrepareOneDriveFile = {
                    withContext(Dispatchers.IO) {
                        store.prepareExportFile()
                    }
                },
                onConfirmOneDriveSaved = {
                    store.confirmOneDriveSavedAndClear()
                    store.count() to store.dayRecordNumber()
                },
                onCancelOneDriveConfirm = {
                    store.cancelPendingOneDriveConfirm()
                },
                onIsPendingOneDriveConfirm = {
                    store.isPendingOneDriveConfirm()
                },
                onTrySaveToDnyFolder = { file ->
                    store.tryWriteExportToDnyFolder(file)
                },
                onWriteExportToUri = { uri, file ->
                    store.writeExportToUri(uri, file)
                },
                onGetDnyTreeUri = { store.getDnyTreeUri() },
                onGetLastSaveUri = { store.getLastSaveUri() },
                onGetDnyFolderLabel = { store.getDnyFolderLabel() },
                onSetDnyTreeFolder = { uri, label, persisted ->
                    store.setDnyTreeFolder(uri, label, persisted)
                },
                onClearDnyTreeFolder = { store.clearDnyTreeFolder() },
                onTakePersistableTree = { uri ->
                    SafUris.takePersistableReadWrite(contentResolver, uri)
                },
                onShareOneDrive = { file ->
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        file,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = MeasurementStore.MIME_XLSX
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, file.name)
                        putExtra(Intent.EXTRA_TITLE, file.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newUri(
                            contentResolver,
                            file.name,
                            uri,
                        )
                    }
                    val chooser = Intent.createChooser(send, "Sdílet denní soubor").apply {
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

}

@Composable
fun MereniApp(
    appVersion: String,
    load: PasportLoadResult,
    onLoadChange: (PasportLoadResult) -> Unit,
    initialCount: Int,
    initialDayRecord: Int,
    initialOneDriveSynced: Boolean,
    onSave: (stationName: String, udu: String, pole1: String, pole2: String, casMereni: String, poznamka: String) -> Pair<Int, Int>,
    onUsedLabels: suspend (String) -> Pair<Set<String>, Set<String>>,
    onPersistBytes: suspend (ByteArray, Uri?) -> PasportLoadResult,
    onPrepareOneDriveFile: suspend () -> File,
    onConfirmOneDriveSaved: () -> Pair<Int, Int>,
    onCancelOneDriveConfirm: () -> Unit,
    onIsPendingOneDriveConfirm: () -> Boolean,
    onTrySaveToDnyFolder: (File) -> Boolean,
    onWriteExportToUri: (Uri, File) -> Unit,
    onGetDnyTreeUri: () -> Uri?,
    onGetLastSaveUri: () -> Uri?,
    onGetDnyFolderLabel: () -> String,
    onSetDnyTreeFolder: (Uri, String, Boolean) -> Boolean,
    onClearDnyTreeFolder: () -> Unit,
    onTakePersistableTree: (Uri) -> Boolean,
    onShareOneDrive: (File) -> Unit,
    onReload: suspend () -> PasportLoadResult,
    onKeysForStation: suspend (Station?, List<PasportKey>) -> List<PasportKey>,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
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
    var dayRecordNum by remember { mutableIntStateOf(initialDayRecord.coerceAtLeast(0)) }
    var oneDriveSynced by remember { mutableStateOf(initialOneDriveSynced) }
    var leftForOneDriveShare by remember { mutableStateOf(false) }
    var showOneDriveConfirm by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var dnyFolderLabel by remember { mutableStateOf(onGetDnyFolderLabel()) }
    var reorderPole1 by remember { mutableStateOf(false) }
    var reorderPole2 by remember { mutableStateOf(false) }
    var usedPole1A by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usedPole2A by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usedPole1B by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usedPole2B by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customDialogFor by remember { mutableStateOf<ActiveField?>(null) }
    var noteFocused by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialCount) {
        recordCount = initialCount
    }

    LaunchedEffect(Unit) {
        if (onIsPendingOneDriveConfirm()) {
            showOneDriveConfirm = true
        }
    }

    DisposableEffect(activity) {
        val owner = activity ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && leftForOneDriveShare) {
                leftForOneDriveShare = false
                if (onIsPendingOneDriveConfirm()) {
                    showOneDriveConfirm = true
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val dualMode = stationB != null
    val activeStation = if (activeSlot == 1 && stationB != null) stationB else stationA
    val stationKeys = if (activeSlot == 1 && dualMode) keysB else keysA
    /** CSV/Excel zašednutí — zvlášť koleje/spojky a výhybky, jen aktivní vyhledávač. */
    val usedLabels = when {
        activeField == ActiveField.POLE1 && activeSlot == 1 && dualMode -> usedPole1B
        activeField == ActiveField.POLE1 -> usedPole1A
        activeField == ActiveField.POLE2 && activeSlot == 1 && dualMode -> usedPole2B
        activeField == ActiveField.POLE2 -> usedPole2A
        else -> emptySet()
    }
    /**
     * Právě v horním poli stejného typu z **tohoto** slotu.
     * Koleje a výhybky se navzájem neblokují (kolej 4 ≠ výhybka 4).
     */
    val lockedLabels = when (activeField) {
        ActiveField.POLE1 -> pole1
            .filter { !dualMode || it.fromSlot == activeSlot }
            .map { it.label }
            .toSet()
        ActiveField.POLE2 -> pole2
            .filter { !dualMode || it.fromSlot == activeSlot }
            .map { it.label }
            .toSet()
        ActiveField.CAS -> emptySet()
    }

    fun refreshUsedLabels() {
        scope.launch {
            val a = stationA?.jmeno?.takeIf { it.isNotBlank() }?.let { onUsedLabels(it) }
            usedPole1A = a?.first ?: emptySet()
            usedPole2A = a?.second ?: emptySet()
            val b = stationB?.jmeno?.takeIf { it.isNotBlank() }?.let { onUsedLabels(it) }
            usedPole1B = b?.first ?: emptySet()
            usedPole2B = b?.second ?: emptySet()
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
        usedPole1A = emptySet()
        usedPole2A = emptySet()
        usedPole1B = emptySet()
        usedPole2B = emptySet()
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

    val createXlsx = rememberLauncherForActivityResult(
        contract = CreateXlsxDocumentContract(),
    ) { uri: Uri? ->
        val file = pendingExportFile
        pendingExportFile = null
        if (uri == null || file == null) {
            onCancelOneDriveConfirm()
            exportMessage = "Ukládání zrušeno"
            return@rememberLauncherForActivityResult
        }
        // Zápis hned na Main — OneDrive grant je krátký
        runCatching { onWriteExportToUri(uri, file) }
            .onSuccess {
                oneDriveSynced = false
                showOneDriveConfirm = true
                exportMessage = "Uloženo: ${file.name}"
            }
            .onFailure { e ->
                onCancelOneDriveConfirm()
                exportMessage = e.message ?: "Zápis se nepovedl"
            }
    }

    val pickDnyFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) {
            exportMessage = "Výběr složky zrušen"
            return@rememberLauncherForActivityResult
        }
        val persisted = onTakePersistableTree(uri)
        val name = DocumentFile.fromTreeUri(context, uri)?.name
            ?.takeIf { it.isNotBlank() }
            ?: "Dny"
        onSetDnyTreeFolder(uri, name, persisted)
        dnyFolderLabel = name
        exportMessage = if (persisted) {
            "Složka Dny nastavena: $name — příště uloží přímo sem"
        } else {
            "Složka zapamatována ($name), ale OneDrive často nepovolí trvalý zápis. " +
                "Uložit otevře „Uložit jako…“ blízko této cesty."
        }
    }

    fun launchSavePicker(file: File) {
        pendingExportFile = file
        val initial = onGetDnyTreeUri() ?: onGetLastSaveUri()
        createXlsx.launch(CreateXlsxRequest(fileName = file.name, initialUri = initial))
        exportMessage = "Ulož ${file.name} do ${MeasurementStore.DNY_HINT_PATH}"
    }

    fun startOneDriveSave(preferShare: Boolean = false) {
        exportMessage = null
        scope.launch {
            val file = onPrepareOneDriveFile()
            oneDriveSynced = false
            if (preferShare) {
                leftForOneDriveShare = true
                onShareOneDrive(file)
                exportMessage = "Sdílení ${file.name}…"
                return@launch
            }
            if (onGetDnyTreeUri() != null && onTrySaveToDnyFolder(file)) {
                showOneDriveConfirm = true
                exportMessage = "Uloženo do Dny: ${file.name}"
                return@launch
            }
            // CreateDocument — název předvyplněný, tip na poslední / Dny URI
            launchSavePicker(file)
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
                StationSearchPicker(
                    stations = pasport.stations,
                    selected = stationA,
                    onSelect = { selectStationA(it, clearFields = stationA == null) },
                    accentColor = MereniColors.Search1,
                    isKeySource = activeSlot == 0 && stationA != null,
                    slotLabel = if (dualMode) "1" else null,
                    onActivate = { activeSlot = 0 },
                    onOpenInSecond = { selectStationB(it) },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val oneDriveAccent = if (oneDriveSynced) {
                            MereniColors.Vyhybka
                        } else {
                            MereniColors.Danger
                        }
                        val oneDriveShape = RoundedCornerShape(10.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(40.dp)
                                .clip(oneDriveShape)
                                .background(oneDriveAccent.copy(alpha = 0.12f))
                                .border(2.5.dp, oneDriveAccent, oneDriveShape)
                                .clickable {
                                    startOneDriveSave(preferShare = false)
                                }
                                .padding(horizontal = 14.dp),
                        ) {
                            Text(
                                "Uložit na OneDrive",
                                color = oneDriveAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                        val countShape = RoundedCornerShape(8.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(40.dp)
                                .widthIn(min = 44.dp)
                                .clip(countShape)
                                .background(MereniColors.Cas.copy(alpha = 0.14f))
                                .border(2.dp, MereniColors.Cas, countShape)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = "$dayRecordNum",
                                color = MereniColors.Cas,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
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
                    dnyFolderLabel = dnyFolderLabel,
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
                    onPickDnyFolder = {
                        pickDnyFolder.launch(onGetDnyTreeUri())
                    },
                    onClearDnyFolder = {
                        onClearDnyTreeFolder()
                        dnyFolderLabel = ""
                        exportMessage = "Složka Dny zrušena"
                    },
                    onShareFallback = {
                        startOneDriveSave(preferShare = true)
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

            // Poznámka + Další záznam uprostřed (nad klávesnicí)
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
                        .height(48.dp)
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (note.isEmpty()) {
                                Text("Poznámka…", color = MereniColors.TextMuted, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
                val dalsiShape = RoundedCornerShape(10.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 168.dp)
                        .clip(dalsiShape)
                        .background(MereniColors.Vyhybka.copy(alpha = 0.12f))
                        .border(2.5.dp, MereniColors.Vyhybka, dalsiShape)
                        .clickable {
                            val cas = if (timeChosen) timeLabel() else ""
                            val noteText = note.trim()
                            val hasContent = pole1.isNotEmpty() || pole2.isNotEmpty() ||
                                cas.isNotBlank() || noteText.isNotBlank()
                            if (!hasContent) return@clickable

                            fun saveFor(
                                station: Station?,
                                p1: List<SelectedToken>,
                                p2: List<SelectedToken>,
                                withMeta: Boolean,
                            ) {
                                if (p1.isEmpty() && p2.isEmpty() && !withMeta) return
                                val st = station
                                val udu = st?.udu.orEmpty()
                                val name = st?.jmeno.orEmpty()
                                if (udu.isBlank() && name.isBlank() && p1.isEmpty() && p2.isEmpty() && !withMeta) return
                                val result = onSave(
                                    name,
                                    udu,
                                    p1.joinToString(", ") { it.label },
                                    p2.joinToString(" - ") { it.label },
                                    if (withMeta) cas else "",
                                    if (withMeta) noteText else "",
                                )
                                recordCount = result.first
                                dayRecordNum = result.second
                                oneDriveSynced = false
                            }

                            if (dualMode) {
                                val nameStation = when {
                                    pole2.isNotEmpty() ->
                                        if (pole2.first().fromSlot == 1) stationB else stationA
                                    pole1.isNotEmpty() ->
                                        if (pole1.first().fromSlot == 1) stationB else stationA
                                    else -> stationA
                                }
                                saveFor(
                                    nameStation,
                                    pole1.toList(),
                                    pole2.toList(),
                                    true,
                                )
                            } else {
                                saveFor(
                                    stationA,
                                    pole1.toList(),
                                    pole2.toList(),
                                    true,
                                )
                            }
                            clearAll()
                            refreshUsedLabels()
                        }
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        "Další záznam",
                        color = MereniColors.Vyhybka,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
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

        if (showOneDriveConfirm) {
            Dialog(
                onDismissRequest = {
                    onCancelOneDriveConfirm()
                    showOneDriveConfirm = false
                    oneDriveSynced = false
                },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 420.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MereniColors.SurfaceAlt)
                        .padding(16.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "✕",
                            color = MereniColors.TextMuted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable {
                                    onCancelOneDriveConfirm()
                                    showOneDriveConfirm = false
                                    oneDriveSynced = false
                                }
                                .padding(4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Uložil jsi denní soubor na OneDrive?",
                        color = MereniColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Cíl: ${MeasurementStore.DNY_HINT_PATH}\n" +
                            "(YYMMDD_N_MD1.xlsx)\n\n" +
                            "✕ — tlačítko zůstane červené.\n" +
                            "ANO — vymazat místní záznamy (zelená).",
                        color = MereniColors.TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val (count, day) = onConfirmOneDriveSaved()
                            recordCount = count
                            dayRecordNum = day
                            showOneDriveConfirm = false
                            oneDriveSynced = true
                            exportMessage = "Uloženo — místní záznamy vymazány"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MereniColors.Vyhybka,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("ANO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
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
