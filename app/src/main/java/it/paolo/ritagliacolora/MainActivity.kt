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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

// Modalità di lavoro dell'app
private enum class Modalita { NESSUNA, RITAGLIO, MATITA }

// Un tratto disegnato a matita: colore, spessore e lista di punti (in coordinate del bitmap)
private data class Tratto(val colore: Color, val spessore: Float, val punti: List<Offset>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SchermataPrincipale()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchermataPrincipale() {
    val context = LocalContext.current

    // Bitmap correntemente caricato/modificato
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var modalita by remember { mutableStateOf(Modalita.NESSUNA) }

    // --- Stato per il ritaglio ---
    // Rettangolo di ritaglio in coordinate schermo (relative all'area immagine)
    var cropRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // --- Stato per la matita ---
    val tratti = remember { mutableStateListOf<Tratto>() }
    var trattoCorrente by remember { mutableStateOf<List<Offset>?>(null) }
    var coloreMatita by remember { mutableStateOf(Color.Red) }
    var spessoreMatita by remember { mutableStateOf(10f) }

    // Dimensioni dell'area in cui viene disegnata l'immagine (per convertire coordinate schermo -> bitmap)
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    // --- Stato per il ridimensionamento in fase di salvataggio (percentuale) ---
    var percentualeSalvataggio by remember { mutableStateOf(100f) }
    var dimensioneOriginaleMB by remember { mutableStateOf<Double?>(null) }
    var dimensioneRidottaMB by remember { mutableStateOf<Double?>(null) }

    // Calcola quanto pesa l'immagine originale (100%) ogni volta che cambia il bitmap
    LaunchedEffect(bitmap) {
        val b = bitmap
        if (b != null) {
            val bytes = withContext(Dispatchers.Default) { calcolaDimensioneBytes(b, 1f) }
            dimensioneOriginaleMB = bytes / (1024.0 * 1024.0)
        } else {
            dimensioneOriginaleMB = null
            dimensioneRidottaMB = null
        }
    }

    // Calcola quanto peserà l'immagine alla percentuale scelta (con una piccola attesa
    // per non ricalcolare ad ogni minimo movimento dello slider)
    LaunchedEffect(percentualeSalvataggio, bitmap) {
        val b = bitmap
        if (b != null) {
            delay(250)
            val fattore = (percentualeSalvataggio / 100f).coerceIn(0.1f, 1f)
            val bytes = withContext(Dispatchers.Default) { calcolaDimensioneBytes(b, fattore) }
            dimensioneRidottaMB = bytes / (1024.0 * 1024.0)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val input = context.contentResolver.openInputStream(it)
            val bmp = android.graphics.BitmapFactory.decodeStream(input)
            input?.close()
            if (bmp != null) {
                bitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                tratti.clear()
                cropRect = null
                modalita = Modalita.NESSUNA
                percentualeSalvataggio = 100f
            }
        }
    }

    // Selettore di sistema "salva con nome": l'utente sceglie dove salvare il file
    val saveImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val bmpCorrente = bitmap
        if (uri != null && bmpCorrente != null) {
            val ok = salvaSuUri(context, bmpCorrente, uri, percentualeSalvataggio)
            Toast.makeText(
                context,
                if (ok) "Immagine salvata" else "Errore nel salvataggio",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Barra superiore con i pulsanti principali ---
        TopAppBar(title = { Text("Ritaglia e Colora") })

        Row(
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
                }) {
                    Text(if (modalita == Modalita.RITAGLIO) "Annulla ritaglio" else "Ritaglia")
                }
                Button(onClick = {
                    modalita = if (modalita == Modalita.MATITA) Modalita.NESSUNA else Modalita.MATITA
                }) {
                    Text(if (modalita == Modalita.MATITA) "Fine matita" else "Matita")
                }
            }
        }

        // --- Barra opzioni matita (colore e spessore) ---
        if (modalita == Modalita.MATITA) {
            val coloriDisponibili = listOf(
                Color.Black, Color.Red, Color.Blue, Color.Green,
                Color.Yellow, Color(0xFFFF8000), Color(0xFF8000FF), Color.White
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(coloriDisponibili) { c ->
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
                            .clickableSimple { coloreMatita = c }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        // --- Area immagine ---
        val bmp = bitmap
        if (bmp != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .onSizeChanged { areaSize = it }
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay per disegnare ritaglio o tratti a matita
                ComposeCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(modalita, bmp) {
                            if (modalita == Modalita.MATITA) {
                                detectDragGestures(
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
                            } else if (modalita == Modalita.RITAGLIO) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        cropRect = androidx.compose.ui.geometry.Rect(offset, offset)
                                    },
                                    onDrag = { change, _ ->
                                        val start = cropRect?.topLeft ?: change.position
                                        cropRect = androidx.compose.ui.geometry.Rect(start, change.position)
                                    },
                                    onDragEnd = {}
                                )
                            }
                        }
                ) {
                    // Disegna i tratti già confermati
                    tratti.forEach { t ->
                        for (i in 0 until t.punti.size - 1) {
                            drawLine(
                                color = t.colore,
                                start = t.punti[i],
                                end = t.punti[i + 1],
                                strokeWidth = t.spessore
                            )
                        }
                    }
                    // Disegna il tratto in corso
                    trattoCorrente?.let { punti ->
                        for (i in 0 until punti.size - 1) {
                            drawLine(
                                color = coloreMatita,
                                start = punti[i],
                                end = punti[i + 1],
                                strokeWidth = spessoreMatita
                            )
                        }
                    }
                    // Disegna il rettangolo di ritaglio
                    cropRect?.let { r ->
                        drawRect(
                            color = Color.White,
                            topLeft = r.topLeft,
                            size = r.size,
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            // --- Ridimensionamento in percentuale (per ridurre i MB) ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dimensione: ${percentualeSalvataggio.toInt()}%")
                Slider(
                    value = percentualeSalvataggio,
                    onValueChange = { percentualeSalvataggio = it },
                    valueRange = 10f..100f,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
            // Peso stimato del file: prima (100%) e dopo il ridimensionamento scelto
            val testoOriginale = dimensioneOriginaleMB?.let {
                String.format(Locale.ITALY, "%.2f MB", it)
            } ?: "…"
            val testoRidotto = dimensioneRidottaMB?.let {
                String.format(Locale.ITALY, "%.2f MB", it)
            } ?: "…"
            Text(
                text = "Prima: $testoOriginale   →   Dopo: $testoRidotto",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            // --- Pulsanti di conferma/salvataggio ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (modalita == Modalita.RITAGLIO && cropRect != null) {
                    Button(onClick = {
                        val nuovo = applicaRitaglio(bmp, cropRect!!, areaSize)
                        if (nuovo != null) {
                            bitmap = nuovo
                            tratti.clear()
                            cropRect = null
                            modalita = Modalita.NESSUNA
                        }
                    }) { Text("Conferma ritaglio") }
                }
                if (modalita == Modalita.MATITA && tratti.isNotEmpty()) {
                    Button(onClick = {
                        bitmap = applicaMatita(bmp, tratti, areaSize)
                        tratti.clear()
                    }) { Text("Applica colore") }
                }
                Button(onClick = {
                    val nomeFile = "ritagliacolora_${System.currentTimeMillis()}.png"
                    saveImageLauncher.launch(nomeFile)
                }) { Text("Salva") }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nessuna immagine caricata")
            }
        }
    }
}

// Piccolo helper per rendere cliccabile un Box senza importare foundation.clickable con ripple custom
@Composable
private fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

// Converte il rettangolo di ritaglio (coordinate area/schermo) in coordinate bitmap e ritaglia
private fun applicaRitaglio(
    bmp: Bitmap,
    rect: androidx.compose.ui.geometry.Rect,
    areaSize: IntSize
): Bitmap? {
    if (areaSize.width == 0 || areaSize.height == 0) return null

    // L'immagine è mostrata con ContentScale.Fit: calcolo scala e offset reali
    val scale = minOf(
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

    val x = minOf(left, right).toInt()
    val y = minOf(top, bottom).toInt()
    val w = (kotlin.math.abs(right - left)).toInt().coerceAtLeast(1)
    val h = (kotlin.math.abs(bottom - top)).toInt().coerceAtLeast(1)
    if (x + w > bmp.width || y + h > bmp.height) return bmp

    return Bitmap.createBitmap(bmp, x, y, w, h)
}

// "Brucia" i tratti a matita disegnati sull'overlay direttamente nel bitmap
private fun applicaMatita(bmp: Bitmap, tratti: List<Tratto>, areaSize: IntSize): Bitmap {
    if (areaSize.width == 0 || areaSize.height == 0) return bmp
    val risultato = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(risultato)

    val scale = minOf(
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
                (p1.x - offsetX) / scale, (p1.y - offsetY) / scale,
                (p2.x - offsetX) / scale, (p2.y - offsetY) / scale,
                paint
            )
        }
    }
    return risultato
}

// Calcola quanti byte occuperebbe il PNG del bitmap alla percentuale indicata (senza salvarlo)
private fun calcolaDimensioneBytes(bmp: Bitmap, fattore: Float): Long {
    val f = fattore.coerceIn(0.1f, 1f)
    val nuovaLarghezza = (bmp.width * f).toInt().coerceAtLeast(1)
    val nuovaAltezza = (bmp.height * f).toInt().coerceAtLeast(1)
    val scaled = if (f < 1f) {
        Bitmap.createScaledBitmap(bmp, nuovaLarghezza, nuovaAltezza, true)
    } else {
        bmp
    }
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return baos.size().toLong()
}

// Ridimensiona il bitmap in base alla percentuale e lo scrive sull'Uri scelto dall'utente
private fun salvaSuUri(context: android.content.Context, bmp: Bitmap, uri: Uri, percentuale: Float): Boolean {
    return try {
        val fattore = (percentuale / 100f).coerceIn(0.1f, 1f)
        val nuovaLarghezza = (bmp.width * fattore).toInt().coerceAtLeast(1)
        val nuovaAltezza = (bmp.height * fattore).toInt().coerceAtLeast(1)

        val bmpDaSalvare = if (fattore < 1f) {
            Bitmap.createScaledBitmap(bmp, nuovaLarghezza, nuovaAltezza, true)
        } else {
            bmp
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            bmpDaSalvare.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    } catch (e: Exception) {
        false
    }
}
