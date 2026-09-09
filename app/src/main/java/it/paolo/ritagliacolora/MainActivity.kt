package it.paolo.ritagliacolora

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Modalita { NESSUNA, RITAGLIO, MATITA }
private enum class FormatoSalvataggio { PNG, JPG }

private data class Tratto(
    val colore: Color,
    val spessore: Float,
    val punti: List<Offset>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFA98BFF),
                    secondary = Color(0xFF8F7AD8),
                    background = Color(0xFF101218),
                    surface = Color(0xFF171A22),
                    surfaceVariant = Color(0xFF222631),
                    onBackground = Color(0xFFF2EEFA),
                    onSurface = Color(0xFFF2EEFA)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FotoLabScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FotoLabScreen() {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var modalita by remember { mutableStateOf(Modalita.NESSUNA) }

    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var cropStart by remember { mutableStateOf<Offset?>(null) }
    var rapportoRitaglio by remember { mutableStateOf<Float?>(null) }

    val tratti = remember { mutableStateListOf<Tratto>() }
    var trattoCorrente by remember { mutableStateOf<List<Offset>?>(null) }
    var coloreMatita by remember { mutableStateOf(Color.Red) }
    var spessoreMatita by remember { mutableStateOf(10f) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    val undoStack = remember { mutableStateListOf<Bitmap>() }
    val redoStack = remember { mutableStateListOf<Bitmap>() }

    var larghezzaTesto by remember { mutableStateOf("") }
    var altezzaTesto by remember { mutableStateOf("") }
    var bloccaProporzioni by remember { mutableStateOf(true) }
    var percentuale by remember { mutableStateOf(100f) }

    var formato by remember { mutableStateOf(FormatoSalvataggio.JPG) }
    var qualitaJpg by remember { mutableStateOf(90f) }
    var dimensioneStimataMB by remember { mutableStateOf<Double?>(null) }
    var ridimensionaAperto by remember { mutableStateOf(false) }
    val editorAperto = modalita != Modalita.NESSUNA

    fun impostaDimensioniDaBitmap(b: Bitmap) {
        larghezzaTesto = b.width.toString()
        altezzaTesto = b.height.toString()
        percentuale = 100f
    }

    fun salvaPerUndo(current: Bitmap) {
        undoStack.add(current.copy(Bitmap.Config.ARGB_8888, true))
        if (undoStack.size > 10) undoStack.removeAt(0)
        redoStack.clear()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val copy = bmp.copy(Bitmap.Config.ARGB_8888, true)
                        bitmap = copy
                        impostaDimensioniDaBitmap(copy)
                        tratti.clear()
                        cropRect = null
                        cropStart = null
                        modalita = Modalita.NESSUNA
                        undoStack.clear()
                        redoStack.clear()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Errore nel caricamento", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun salvaImmagine(uri: Uri, format: FormatoSalvataggio) {
        val b = bitmap ?: return
        val w = larghezzaTesto.toIntOrNull()?.coerceAtLeast(1) ?: b.width
        val h = altezzaTesto.toIntOrNull()?.coerceAtLeast(1) ?: b.height
        val ok = salvaSuUri(
            context = context,
            bmp = b,
            uri = uri,
            larghezza = w,
            altezza = h,
            formato = format,
            qualitaJpg = qualitaJpg.roundToInt()
        )
        Toast.makeText(
            context,
            if (ok) "Immagine salvata" else "Errore nel salvataggio",
            Toast.LENGTH_SHORT
        ).show()
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> if (uri != null) salvaImmagine(uri, FormatoSalvataggio.PNG) }

    val saveJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri -> if (uri != null) salvaImmagine(uri, FormatoSalvataggio.JPG) }

    LaunchedEffect(bitmap, larghezzaTesto, altezzaTesto, formato, qualitaJpg) {
        val b = bitmap ?: return@LaunchedEffect
        delay(250)
        val w = larghezzaTesto.toIntOrNull()?.coerceAtLeast(1) ?: b.width
        val h = altezzaTesto.toIntOrNull()?.coerceAtLeast(1) ?: b.height
        val bytes = withContext(Dispatchers.Default) {
            calcolaDimensioneBytes(
                b, w, h, formato, qualitaJpg.roundToInt()
            )
        }
        dimensioneStimataMB = bytes / (1024.0 * 1024.0)
    }

    Scaffold(
        topBar = {
            if (!editorAperto) TopAppBar(
                title = { Text("FotoLab") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    TextButton(
                        enabled = undoStack.isNotEmpty() && bitmap != null,
                        onClick = {
                            val current = bitmap ?: return@TextButton
                            val previous = undoStack.removeAt(undoStack.lastIndex)
                            redoStack.add(current.copy(Bitmap.Config.ARGB_8888, true))
                            bitmap = previous
                            impostaDimensioniDaBitmap(previous)
                            cropRect = null
                            cropStart = null
                            tratti.clear()
                            modalita = Modalita.NESSUNA
                        }
                    ) { Text("Annulla") }

                    TextButton(
                        enabled = redoStack.isNotEmpty() && bitmap != null,
                        onClick = {
                            val current = bitmap ?: return@TextButton
                            val next = redoStack.removeAt(redoStack.lastIndex)
                            undoStack.add(current.copy(Bitmap.Config.ARGB_8888, true))
                            bitmap = next
                            impostaDimensioniDaBitmap(next)
                            cropRect = null
                            cropStart = null
                            tratti.clear()
                            modalita = Modalita.NESSUNA
                        }
                    ) { Text("Ripristina") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (!editorAperto) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { pickImageLauncher.launch("image/*") }) {
                    Text("Carica")
                }
                if (bitmap != null) {
                    Button(onClick = {
                        modalita = if (modalita == Modalita.RITAGLIO) Modalita.NESSUNA else Modalita.RITAGLIO
                        cropRect = null
                        cropStart = null
                        tratti.clear()
                    }) {
                        Text(if (modalita == Modalita.RITAGLIO) "Chiudi ritaglio" else "Ritaglia")
                    }
                    Button(onClick = {
                        modalita = if (modalita == Modalita.MATITA) Modalita.NESSUNA else Modalita.MATITA
                        cropRect = null
                        cropStart = null
                    }) {
                        Text(if (modalita == Modalita.MATITA) "Fine matita" else "Matita")
                    }
                }
            }

            val bmp = bitmap
            if (bmp == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Carica una foto per iniziare")
                }
                return@Column
            }

            if (false && modalita == Modalita.RITAGLIO) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rapporti = listOf(
                        "Libero" to null,
                        "1:1" to 1f,
                        "4:3" to (4f / 3f),
                        "3:2" to (3f / 2f),
                        "16:9" to (16f / 9f)
                    )
                    items(rapporti) { item ->
                        FilterChip(
                            selected = rapportoRitaglio == item.second,
                            onClick = {
                                rapportoRitaglio = item.second
                                cropStart = null
                                cropRect = if (item.second == null) {
                                    null
                                } else {
                                    creaRitaglioCentrato(
                                        bmp = bmp,
                                        areaSize = areaSize,
                                        ratio = item.second!!
                                    )
                                }
                            },
                            label = { Text(item.first) }
                        )
                    }
                }
            }

            if (false && modalita == Modalita.MATITA) {
                val colori = listOf(
                    Color.Black, Color.Red, Color.Blue, Color.Green,
                    Color.Yellow, Color(0xFFFF8000), Color(0xFF8000FF), Color.White
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colori) { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .then(
                                    if (c == coloreMatita)
                                        Modifier.border(3.dp, Color.Gray, CircleShape)
                                    else Modifier
                                )
                                .background(c)
                                .clickable { coloreMatita = c }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spessore")
                    Slider(
                        value = spessoreMatita,
                        onValueChange = { spessoreMatita = it },
                        valueRange = 2f..40f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (editorAperto) 0.dp else 12.dp)
                    .clip(if (editorAperto) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp))
                    .background(if (editorAperto) Color.Black else Color(0xFF1B1E27))
                    .onSizeChanged { areaSize = it }
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                ComposeCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(modalita, bmp, rapportoRitaglio) {
                            when (modalita) {
                                Modalita.MATITA -> detectDragGestures(
                                    onDragStart = { offset ->
                                        trattoCorrente = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        trattoCorrente = trattoCorrente.orEmpty() + change.position
                                    },
                                    onDragEnd = {
                                        val punti = trattoCorrente
                                        if (punti != null && punti.size > 1) {
                                            tratti.add(Tratto(coloreMatita, spessoreMatita, punti))
                                        }
                                        trattoCorrente = null
                                    }
                                )
                                Modalita.RITAGLIO -> detectDragGestures(
                                    onDragStart = { start ->
                                        val bounds = calcolaRettangoloImmagine(bmp, areaSize)
                                        val punto = Offset(
                                            start.x.coerceIn(bounds.left, bounds.right),
                                            start.y.coerceIn(bounds.top, bounds.bottom)
                                        )
                                        cropStart = punto
                                        cropRect = Rect(punto, punto)
                                    },
                                    onDrag = { change, _ ->
                                        val start = cropStart ?: change.position
                                        val bounds = calcolaRettangoloImmagine(bmp, areaSize)
                                        cropRect = creaRettangoloRitaglio(
                                            start,
                                            change.position,
                                            rapportoRitaglio,
                                            bounds
                                        )
                                    }
                                )
                                else -> Unit
                            }
                        }
                ) {
                    tratti.forEach { t ->
                        for (i in 0 until t.punti.size - 1) {
                            drawLine(t.colore, t.punti[i], t.punti[i + 1], t.spessore)
                        }
                    }
                    trattoCorrente?.let { punti ->
                        for (i in 0 until punti.size - 1) {
                            drawLine(coloreMatita, punti[i], punti[i + 1], spessoreMatita)
                        }
                    }
                    cropRect?.let { r ->
                        drawRect(
                            color = Color.White,
                            topLeft = r.topLeft,
                            size = r.size,
                            style = Stroke(width = 4f)
                        )
                    }
                }

                if (editorAperto) {
                    EditorControls(
                        modalita = modalita,
                        rapportoRitaglio = rapportoRitaglio,
                        coloreMatita = coloreMatita,
                        spessoreMatita = spessoreMatita,
                        puoConfermare = if (modalita == Modalita.RITAGLIO) cropRect != null else tratti.isNotEmpty(),
                        onRapporto = { ratio ->
                            rapportoRitaglio = ratio
                            cropStart = null
                            cropRect = if (ratio == null) null else creaRitaglioCentrato(bmp, areaSize, ratio)
                        },
                        onColore = { coloreMatita = it },
                        onSpessore = { spessoreMatita = it },
                        onAnnulla = {
                            cropRect = null
                            cropStart = null
                            tratti.clear()
                            trattoCorrente = null
                            modalita = Modalita.NESSUNA
                        },
                        onConferma = {
                            if (modalita == Modalita.RITAGLIO) {
                                val rect = cropRect
                                if (rect != null) {
                                    val nuovo = applicaRitaglio(bmp, rect, areaSize)
                                    if (nuovo != null) {
                                        salvaPerUndo(bmp)
                                        bitmap = nuovo
                                        impostaDimensioniDaBitmap(nuovo)
                                        cropRect = null
                                        cropStart = null
                                        modalita = Modalita.NESSUNA
                                    }
                                }
                            } else if (tratti.isNotEmpty()) {
                                salvaPerUndo(bmp)
                                val nuovo = applicaMatita(bmp, tratti, areaSize)
                                bitmap = nuovo
                                impostaDimensioniDaBitmap(nuovo)
                                tratti.clear()
                                trattoCorrente = null
                                modalita = Modalita.NESSUNA
                            }
                        }
                    )
                }
            }

            if (false && modalita == Modalita.RITAGLIO && cropRect != null) {
                Button(
                    onClick = {
                        val nuovo = applicaRitaglio(bmp, cropRect!!, areaSize)
                        if (nuovo != null) {
                            salvaPerUndo(bmp)
                            bitmap = nuovo
                            impostaDimensioniDaBitmap(nuovo)
                            cropRect = null
                            cropStart = null
                            modalita = Modalita.NESSUNA
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) { Text("Conferma ritaglio") }
            }

            if (false && modalita == Modalita.MATITA && tratti.isNotEmpty()) {
                Button(
                    onClick = {
                        salvaPerUndo(bmp)
                        val nuovo = applicaMatita(bmp, tratti, areaSize)
                        bitmap = nuovo
                        impostaDimensioniDaBitmap(nuovo)
                        tratti.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) { Text("Applica disegno") }
            }

            if (!editorAperto) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ridimensionaAperto = !ridimensionaAperto },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222631)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ridimensiona", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$larghezzaTesto × $altezzaTesto px  •  ${percentuale.roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBDB6CC)
                            )
                        }
                        Text(
                            if (ridimensionaAperto) "Chiudi  ▲" else "Apri  ▼",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (ridimensionaAperto) Column(
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    ) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                    OutlinedTextField(
                        value = larghezzaTesto,
                        onValueChange = { value ->
                            val filtered = value.filter(Char::isDigit)
                            larghezzaTesto = filtered
                            val newW = filtered.toIntOrNull()
                            if (bloccaProporzioni && newW != null && bmp.width > 0) {
                                altezzaTesto =
                                    (newW * bmp.height.toFloat() / bmp.width)
                                        .roundToInt().coerceAtLeast(1).toString()
                            }
                        },
                        label = { Text("Larghezza px") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = altezzaTesto,
                        onValueChange = { value ->
                            val filtered = value.filter(Char::isDigit)
                            altezzaTesto = filtered
                            val newH = filtered.toIntOrNull()
                            if (bloccaProporzioni && newH != null && bmp.height > 0) {
                                larghezzaTesto =
                                    (newH * bmp.width.toFloat() / bmp.height)
                                        .roundToInt().coerceAtLeast(1).toString()
                            }
                        },
                        label = { Text("Altezza px") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = bloccaProporzioni,
                        onCheckedChange = { bloccaProporzioni = it }
                    )
                    Text("Mantieni proporzioni")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${percentuale.roundToInt()}%")
                    Slider(
                        value = percentuale,
                        onValueChange = { p ->
                            percentuale = p
                            larghezzaTesto =
                                (bmp.width * p / 100f).roundToInt().coerceAtLeast(1).toString()
                            altezzaTesto =
                                (bmp.height * p / 100f).roundToInt().coerceAtLeast(1).toString()
                        },
                        valueRange = 10f..100f,
                        modifier = Modifier.weight(1f)
                    )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1D2029))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                Text("Esportazione", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = formato == FormatoSalvataggio.JPG,
                        onClick = { formato = FormatoSalvataggio.JPG },
                        label = { Text("JPG") }
                    )
                    FilterChip(
                        selected = formato == FormatoSalvataggio.PNG,
                        onClick = { formato = FormatoSalvataggio.PNG },
                        label = { Text("PNG") }
                    )
                }

                if (formato == FormatoSalvataggio.JPG) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Qualità ${qualitaJpg.roundToInt()}%")
                        Slider(
                            value = qualitaJpg,
                            onValueChange = { qualitaJpg = it },
                            valueRange = 40f..100f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val stima = dimensioneStimataMB?.let {
                    String.format(Locale.ITALY, "%.2f MB", it)
                } ?: "…"
                Text("Dimensione stimata: $stima")

                Button(
                    onClick = {
                        val stamp = System.currentTimeMillis()
                        if (formato == FormatoSalvataggio.PNG) {
                            savePngLauncher.launch("FotoLab_$stamp.png")
                        } else {
                            saveJpgLauncher.launch("FotoLab_$stamp.jpg")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) { Text("Salva immagine") }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.EditorControls(
    modalita: Modalita,
    rapportoRitaglio: Float?,
    coloreMatita: Color,
    spessoreMatita: Float,
    puoConfermare: Boolean,
    onRapporto: (Float?) -> Unit,
    onColore: (Color) -> Unit,
    onSpessore: (Float) -> Unit,
    onAnnulla: () -> Unit,
    onConferma: () -> Unit
) {
    val sfondo = Color.Black.copy(alpha = 0.58f)

    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(10.dp),
        color = sfondo,
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    ) {
        if (modalita == Modalita.RITAGLIO) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Libero" to null,
                    "1:1" to 1f,
                    "4:3" to (4f / 3f),
                    "3:2" to (3f / 2f),
                    "16:9" to (16f / 9f)
                ).forEach { (testo, ratio) ->
                    val selezionato = rapportoRitaglio == ratio
                    Text(
                        text = testo,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selezionato) Color(0xFF7656B7).copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable { onRapporto(ratio) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Color.Black, Color.Red, Color.Blue, Color.Green,
                        Color.Yellow, Color(0xFFFF8000), Color(0xFF8000FF), Color.White
                    ).forEach { colore ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(colore)
                                .then(
                                    if (colore == coloreMatita) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { onColore(colore) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spessore", color = Color.White)
                    Slider(
                        value = spessoreMatita,
                        onValueChange = onSpessore,
                        valueRange = 2f..40f,
                        modifier = Modifier.width(210.dp)
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onAnnulla,
            colors = ButtonDefaults.buttonColors(containerColor = sfondo, contentColor = Color.White)
        ) { Text("Annulla") }
        Button(
            onClick = onConferma,
            enabled = puoConfermare,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6E4EAD).copy(alpha = 0.82f),
                contentColor = Color.White,
                disabledContainerColor = sfondo,
                disabledContentColor = Color.White.copy(alpha = 0.45f)
            )
        ) { Text(if (modalita == Modalita.RITAGLIO) "Conferma ritaglio" else "Applica disegno") }
    }
}

private fun calcolaRettangoloImmagine(bmp: Bitmap, areaSize: IntSize): Rect {
    if (areaSize.width <= 0 || areaSize.height <= 0) return Rect.Zero
    val scale = min(
        areaSize.width.toFloat() / bmp.width,
        areaSize.height.toFloat() / bmp.height
    )
    val dispW = bmp.width * scale
    val dispH = bmp.height * scale
    val left = (areaSize.width - dispW) / 2f
    val top = (areaSize.height - dispH) / 2f
    return Rect(left, top, left + dispW, top + dispH)
}


private fun creaRitaglioCentrato(
    bmp: Bitmap,
    areaSize: IntSize,
    ratio: Float
): Rect? {
    if (areaSize.width <= 0 || areaSize.height <= 0 || ratio <= 0f) return null

    val scale = min(
        areaSize.width.toFloat() / bmp.width,
        areaSize.height.toFloat() / bmp.height
    )

    val dispW = bmp.width * scale
    val dispH = bmp.height * scale
    val offsetX = (areaSize.width - dispW) / 2f
    val offsetY = (areaSize.height - dispH) / 2f

    val margin = min(dispW, dispH) * 0.06f
    val maxW = (dispW - margin * 2f).coerceAtLeast(1f)
    val maxH = (dispH - margin * 2f).coerceAtLeast(1f)

    var cropW = maxW
    var cropH = cropW / ratio

    if (cropH > maxH) {
        cropH = maxH
        cropW = cropH * ratio
    }

    val left = offsetX + (dispW - cropW) / 2f
    val top = offsetY + (dispH - cropH) / 2f

    return Rect(
        left = left,
        top = top,
        right = left + cropW,
        bottom = top + cropH
    )
}

private fun creaRettangoloRitaglio(
    start: Offset,
    current: Offset,
    ratio: Float?,
    bounds: Rect
): Rect {
    var dx = current.x - start.x
    var dy = current.y - start.y

    if (ratio != null && abs(dx) > 1f && abs(dy) > 1f) {
        val signX = if (dx >= 0f) 1f else -1f
        val signY = if (dy >= 0f) 1f else -1f
        if (abs(dx / dy) > ratio) {
            dx = abs(dy) * ratio * signX
        } else {
            dy = abs(dx) / ratio * signY
        }
    }

    val endX = (start.x + dx).coerceIn(bounds.left, bounds.right)
    val endY = (start.y + dy).coerceIn(bounds.top, bounds.bottom)

    return Rect(
        min(start.x, endX),
        min(start.y, endY),
        max(start.x, endX),
        max(start.y, endY)
    )
}

private fun applicaRitaglio(bmp: Bitmap, rect: Rect, areaSize: IntSize): Bitmap? {
    if (areaSize.width == 0 || areaSize.height == 0) return null

    val scale = min(
        areaSize.width.toFloat() / bmp.width,
        areaSize.height.toFloat() / bmp.height
    )
    val dispW = bmp.width * scale
    val dispH = bmp.height * scale
    val offsetX = (areaSize.width - dispW) / 2f
    val offsetY = (areaSize.height - dispH) / 2f

    val left = ((rect.left - offsetX) / scale).coerceIn(0f, bmp.width.toFloat())
    val top = ((rect.top - offsetY) / scale).coerceIn(0f, bmp.height.toFloat())
    val right = ((rect.right - offsetX) / scale).coerceIn(0f, bmp.width.toFloat())
    val bottom = ((rect.bottom - offsetY) / scale).coerceIn(0f, bmp.height.toFloat())

    val x = min(left, right).roundToInt().coerceIn(0, bmp.width - 1)
    val y = min(top, bottom).roundToInt().coerceIn(0, bmp.height - 1)
    val x2 = max(left, right).roundToInt().coerceIn(x + 1, bmp.width)
    val y2 = max(top, bottom).roundToInt().coerceIn(y + 1, bmp.height)

    return Bitmap.createBitmap(bmp, x, y, x2 - x, y2 - y)
}

private fun applicaMatita(
    bmp: Bitmap,
    tratti: List<Tratto>,
    areaSize: IntSize
): Bitmap {
    if (areaSize.width == 0 || areaSize.height == 0) return bmp

    val risultato = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(risultato)

    val scale = min(
        areaSize.width.toFloat() / bmp.width,
        areaSize.height.toFloat() / bmp.height
    )
    val dispW = bmp.width * scale
    val dispH = bmp.height * scale
    val offsetX = (areaSize.width - dispW) / 2f
    val offsetY = (areaSize.height - dispH) / 2f

    val paint = Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    tratti.forEach { t ->
        paint.color = android.graphics.Color.argb(
            (t.colore.alpha * 255).toInt(),
            (t.colore.red * 255).toInt(),
            (t.colore.green * 255).toInt(),
            (t.colore.blue * 255).toInt()
        )
        paint.strokeWidth = t.spessore / scale

        for (i in 0 until t.punti.size - 1) {
            val p1 = t.punti[i]
            val p2 = t.punti[i + 1]
            canvas.drawLine(
                (p1.x - offsetX) / scale,
                (p1.y - offsetY) / scale,
                (p2.x - offsetX) / scale,
                (p2.y - offsetY) / scale,
                paint
            )
        }
    }
    return risultato
}

private fun calcolaDimensioneBytes(
    bmp: Bitmap,
    larghezza: Int,
    altezza: Int,
    formato: FormatoSalvataggio,
    qualitaJpg: Int
): Long {
    val scaled = if (larghezza != bmp.width || altezza != bmp.height) {
        Bitmap.createScaledBitmap(bmp, larghezza, altezza, true)
    } else bmp

    val baos = ByteArrayOutputStream()
    if (formato == FormatoSalvataggio.PNG) {
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
    } else {
        scaled.compress(Bitmap.CompressFormat.JPEG, qualitaJpg.coerceIn(40, 100), baos)
    }
    return baos.size().toLong()
}

private fun salvaSuUri(
    context: android.content.Context,
    bmp: Bitmap,
    uri: Uri,
    larghezza: Int,
    altezza: Int,
    formato: FormatoSalvataggio,
    qualitaJpg: Int
): Boolean {
    return try {
        val scaled = if (larghezza != bmp.width || altezza != bmp.height) {
            Bitmap.createScaledBitmap(bmp, larghezza, altezza, true)
        } else bmp

        val out = context.contentResolver.openOutputStream(uri) ?: return false
        out.use {
            if (formato == FormatoSalvataggio.PNG) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
            } else {
                scaled.compress(
                    Bitmap.CompressFormat.JPEG,
                    qualitaJpg.coerceIn(40, 100),
                    it
                )
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}
