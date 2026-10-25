package com.kkps.kdesktopsuite.paint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.util.AtomicFile
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal data class LoadedImage(val raster: Raster, val name: String, val mime: String, val fingerprint: String)
internal data class StagedImage(val file: File, val fingerprint: String)
internal class ImageChangedException : IOException("The external image changed. Choose Overwrite, Save as, or Cancel.")

internal class ImageRepository(private val context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val resolver get() = context.contentResolver
    suspend fun read(uri: String, flatten: Boolean = true): LoadedImage = withContext(io) {
        val parsed = uri.toUri()
        val staged = File.createTempFile("incoming-", ".image", context.cacheDir)
        try {
            resolver.openInputStream(parsed)?.use { input ->
                staged.outputStream().use { output ->
                    val buffer = ByteArray(32768); var total = 0L
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        total += read
                        require(total <= 128L * 1024 * 1024) { "Encoded images must be smaller than 128 MiB." }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("This provider did not supply image data.")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staged.path, bounds)
            require(bounds.outMimeType in setOf("image/png", "image/jpeg")) { "Choose a PNG or JPEG image." }
            checkImageSize(bounds.outWidth, bounds.outHeight)
            val decoded = BitmapFactory.decodeFile(staged.path, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }) ?: throw IOException("This image could not be decoded.")
            val exif = runCatching { ExifInterface(staged).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (exif) {
                    2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(90f); postScale(1f, -1f) }
                    8 -> setRotate(270f)
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, false)
            val opaque = createBitmap(oriented.width, oriented.height)
            Canvas(opaque).apply { if (flatten) drawColor(WHITE); drawBitmap(oriented, 0f, 0f, null) }
            val raster = opaque.raster()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle(); opaque.recycle()
            val name = resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Image"
            LoadedImage(raster, name, bounds.outMimeType, fingerprint(staged))
        } finally { staged.delete() }
    }
    suspend fun stage(raster: Raster, mime: String, quality: Int): StagedImage = withContext(io) {
        val directory = File(context.filesDir, "shared-images").apply { mkdirs() }
        // Keep recent clipboard/share outputs readable; prune only files older than seven days.
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 7L * 86400000 }?.forEach { it.delete() }
        val file = File(directory, "${UUID.randomUUID()}.${if (mime == "image/jpeg") "jpg" else "png"}")
        try {
            val bitmap = raster.bitmap()
            try {
                file.outputStream().use {
                    check(bitmap.compress(if (mime == "image/jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, quality, it))
                }
            } finally { bitmap.recycle() }
            StagedImage(file, fingerprint(file))
        } catch (error: Exception) { file.delete(); throw error }
    }
    suspend fun write(uri: String, staged: StagedImage, expected: String?): String = withContext(io) {
        if (expected != null && externalFingerprint(uri) != expected) throw ImageChangedException()
        resolver.openOutputStream(uri.toUri(), "wt")?.use { output ->
            staged.file.inputStream().use { it.copyTo(output) }
        } ?: throw IOException("This image cannot be written. Use Save as.")
        val actual = externalFingerprint(uri)
        check(actual == staged.fingerprint) { "The provider did not retain the complete image. The private draft is safe." }
        staged.file.name
    }
    private fun externalFingerprint(uri: String): String = resolver.openInputStream(uri.toUri())?.use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32768)
        var size = 0L
        while (true) { val read = input.read(buffer); if (read < 0) break; size += read
            require(size <= 128L * 1024 * 1024) { "The external file is too large to compare safely." }
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } ?: throw IOException("Cannot check the existing image. Use Save as.")
    private fun fingerprint(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32768)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun name(uri: String): String? = withContext(io) {
        resolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
    suspend fun clipboardImage(uri: String): Raster = read(uri,flatten = false).raster
}

private val Context.paintPreferences by preferencesDataStore("paint-preferences")
internal enum class PaintStartupBehavior { ReopenLastEdit, NewImage, AskEveryTime }
internal data class PaintPreferences(val appearance: String = "System", val jpegQuality: Int = 90,
    val customColors: List<Int> = List(10) { WHITE }, val recent: List<String> = emptyList(),
    val defaultCanvasWidth: Int = 800, val defaultCanvasHeight: Int = 600,
    val startupBehavior: PaintStartupBehavior = PaintStartupBehavior.ReopenLastEdit)

internal class PaintSettings(context: Context) {
    private val store = context.paintPreferences
    private val key = stringPreferencesKey("settings")
    val preferences = store.data.map { data ->
        val json = runCatching { JSONObject(data[key] ?: "{}") }.getOrDefault(JSONObject())
        val width = json.optInt("canvasWidth",800).coerceIn(1,8192)
        val height = json.optInt("canvasHeight",600).coerceIn(1,8192)
        val validSize = width.toLong() * height <= MAX_PIXELS
        PaintPreferences(json.optString("appearance", "System"), json.optInt("quality", 90).coerceIn(1, 100),
            List(10) { json.optJSONArray("colors")?.optInt(it, WHITE) ?: WHITE },
            json.optJSONArray("recent")?.let { array -> List(array.length()) { array.getString(it) } } ?: emptyList(),
            if (validSize) width else 800,if (validSize) height else 600,
            PaintStartupBehavior.entries.firstOrNull { it.name == json.optString("startupBehavior") }
                ?: PaintStartupBehavior.ReopenLastEdit)
    }
    suspend fun save(value: PaintPreferences) {
        store.edit { it[key] = JSONObject().put("appearance", value.appearance).put("quality", value.jpegQuality)
            .put("canvasWidth",value.defaultCanvasWidth).put("canvasHeight",value.defaultCanvasHeight)
            .put("startupBehavior",value.startupBehavior.name)
            .put("colors", JSONArray(value.customColors)).put("recent", JSONArray(value.recent)).toString() }
    }
}

internal data class RecoverySnapshot(val raster: Raster, val options: ToolOptions, val preview: EditPreview?,
    val selection: PixelRect?, val viewport: Viewport, val uri: String?, val name: String,
    val mime: String, val fingerprint: String?, val dirty: Boolean, val sequence: Long = 0L)

/** PNG generations are durable; an atomic manifest switches the entire snapshot together. */
internal class PaintRecovery(private val root: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutex = Mutex()
    private var latestSequence = -1L
    private val sequence = AtomicLong()
    private var validManifest: ByteArray? = null
    fun nextSequence(): Long = sequence.incrementAndGet()
    suspend fun write(snapshot: RecoverySnapshot) = withContext(io) { mutex.withLock {
        if (snapshot.sequence < latestSequence) return@withLock
        root.mkdirs()
        val generation = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        fun saveRaster(raster: Raster, name: String) {
            val bitmap = raster.bitmap()
            try { java.io.FileOutputStream(File(generation, name)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync()
            } } finally { bitmap.recycle() }
        }
        saveRaster(snapshot.raster, "image.png")
        snapshot.preview?.payload?.let { saveRaster(it, "selection.png") }
        snapshot.preview?.textRaster?.let { saveRaster(it, "text.png") }
        val json = snapshot.json().put("generation", generation.name).put("version", 1)
        val current = AtomicFile(File(root, "current.json"))
        validManifest?.let { writeAtomic(File(root, "previous.json"), it) }
        val bytes = json.toString().toByteArray()
        writeAtomic(current.baseFile, bytes)
        validManifest = bytes
        latestSequence = snapshot.sequence
        val keep = setOf(generation.name, runCatching { JSONObject(File(root, "previous.json").readText()).getString("generation") }.getOrNull())
        root.listFiles()?.filter { it.isDirectory && it.name !in keep }?.forEach { it.deleteRecursively() }
    } }
    suspend fun read(): RecoverySnapshot? = withContext(io) { mutex.withLock {
        val candidates = listOf("current.json", "previous.json")
        var failure: Exception? = null
        for (name in candidates) {
            val file = AtomicFile(File(root, name))
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) continue
            try {
                val json = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
                require(json.getInt("version") == 1)
                val directory = File(root, json.getString("generation"))
                require(directory.canonicalFile.parentFile == root.canonicalFile)
                fun readRaster(name: String): Raster {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(File(directory, name).path, bounds); checkImageSize(bounds.outWidth, bounds.outHeight)
                    val bitmap = BitmapFactory.decodeFile(File(directory, name).path) ?: error("Incomplete recovery image")
                    return bitmap.raster().also { bitmap.recycle() }
                }
                val previewJson = json.optJSONObject("preview")
                val snapshot = snapshotFromJson(json, readRaster("image.png"),
                    if (previewJson?.optBoolean("payload") == true) readRaster("selection.png") else null,
                    if (previewJson?.optBoolean("textRaster") == true) readRaster("text.png") else null)
                validManifest = json.toString().toByteArray()
                return@withLock snapshot
            } catch (error: Exception) { failure = error }
        }
        if (failure != null) throw IOException("Recovery could not be read. Its files have been preserved.", failure)
        null
    } }
    private fun writeAtomic(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file); val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) } catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}

internal fun PixelRect.json() = JSONArray(listOf(x, y, width, height))
private fun JSONArray.rect() = PixelRect(getInt(0), getInt(1), getInt(2), getInt(3))
private fun RecoverySnapshot.json(): JSONObject {
    val opts = JSONObject().put("tool", options.tool.name).put("foreground", options.foreground).put("background", options.background)
        .put("width", options.width).put("eraserWidth", options.eraserWidth).put("outline", options.outline).put("fill", options.fill)
        .put("constrain", options.constrain).put("font", options.font).put("fontSize", options.fontSize).put("bold", options.bold)
        .put("italic", options.italic).put("underline", options.underline).put("strike", options.strike).put("opaqueText", options.opaqueText)
        .put("brushStyle", options.brushStyle.name)
        .put("brushWidths", JSONObject(options.brushWidths.mapKeys { it.key.name }))
    val json = JSONObject().put("options", opts).put("uri", uri).put("name", name).put("mime", mime)
        .put("fingerprint", fingerprint).put("dirty", dirty).put("selection", selection?.json())
        .put("scale", viewport.scale).put("cx", viewport.center.x).put("cy", viewport.center.y).put("fit", viewport.fit)
    preview?.let { p -> json.put("preview", JSONObject().put("tool", p.tool.name).put("bounds", p.bounds.json())
        .put("startX", p.start.x).put("startY", p.start.y).put("endX", p.end.x).put("endY", p.end.y)
        .put("source", p.source?.json()).put("payload", p.payload != null).put("text", p.text)
        .put("textSelectionStart", p.textSelectionStart).put("textSelectionEnd", p.textSelectionEnd)
        .put("textRaster", p.textRaster != null && p.textLayoutKey == textLayoutKey(p.text,p.bounds.width,options))
        .put("outputWidth", p.outputWidth).put("outputHeight", p.outputHeight)) }
    return json
}
private fun snapshotFromJson(json: JSONObject, raster: Raster, payload: Raster?, textRaster: Raster?): RecoverySnapshot {
    val o = json.getJSONObject("options")
    val options = ToolOptions(Tool.valueOf(o.getString("tool")), o.getInt("foreground"), o.getInt("background"),
        o.getInt("width").coerceIn(1,100), o.getInt("eraserWidth").coerceIn(1,100), o.getBoolean("outline"),
        o.getBoolean("fill"), o.getBoolean("constrain"), o.getString("font"), o.getInt("fontSize").coerceIn(1,512),
        o.getBoolean("bold"), o.getBoolean("italic"), o.getBoolean("underline"), o.getBoolean("strike"), o.getBoolean("opaqueText"),
        brushStyle = BrushStyle.entries.firstOrNull { it.name == o.optString("brushStyle") } ?: BrushStyle.Solid,
        brushWidths = BrushStyle.entries.mapNotNull { style ->
            o.optJSONObject("brushWidths")?.optInt(style.name,0)?.takeIf { it in 1..100 }?.let { style to it }
        }.toMap())
    val preview = json.optJSONObject("preview")?.let { p -> EditPreview(Tool.valueOf(p.getString("tool")), p.getJSONArray("bounds").rect(),
        Point(p.getDouble("startX").toFloat(), p.getDouble("startY").toFloat()), Point(p.getDouble("endX").toFloat(), p.getDouble("endY").toFloat()),
        p.optJSONArray("source")?.rect(), payload, p.optString("text"),
        if (p.has("outputWidth")) p.getInt("outputWidth") else null, if (p.has("outputHeight")) p.getInt("outputHeight") else null,
        textSelectionStart = p.optInt("textSelectionStart"),textSelectionEnd = p.optInt("textSelectionEnd"),
        textRaster = textRaster,textLayoutKey = textRaster?.let { textLayoutKey(p.optString("text"),p.getJSONArray("bounds").rect().width,options) }) }
    return RecoverySnapshot(raster, options, preview, json.optJSONArray("selection")?.rect(),
        Viewport(imageWidth = raster.width, imageHeight = raster.height, scale = json.getDouble("scale").toFloat().coerceIn(0.1f,5f),
            center = Point(json.getDouble("cx").toFloat(), json.getDouble("cy").toFloat()), fit = json.getBoolean("fit")),
        json.optString("uri").takeIf { it.isNotEmpty() }, json.getString("name"), json.getString("mime"),
        json.optString("fingerprint").takeIf { it.isNotEmpty() }, json.getBoolean("dirty"))
}
