package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Local high-resolution media store (Android equivalent of IndexedDB) for garment photos,
 * cutouts, virtual model portraits, try-on renders, and high-res PNG Lookbook exports.
 */
class LocalImageStore(private val context: Context) {

    private val mediaDir: File
        get() = File(context.filesDir, "atelier_media").apply { if (!exists()) mkdirs() }

    private val exportCacheDir: File
        get() = File(context.cacheDir, "lookbooks").apply { if (!exists()) mkdirs() }

    suspend fun getExportCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        exportCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clearExportCache(): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        exportCacheDir.listFiles()?.forEach { f ->
            if (f.delete()) deleted++
        }
        deleted
    }

    suspend fun importFromUri(uri: Uri, prefix: String = "garment", maxDimension: Int = 960): String? =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodeSampledFromUri(uri, maxDimension) ?: return@withContext null
                val scaled = scaleDown(decoded, maxDimension)
                if (scaled !== decoded) decoded.recycle()
                val saved = saveBitmap(scaled, prefix, asPng = false)
                scaled.recycle()
                saved
            } catch (_: Exception) {
                null
            }
        }

    suspend fun saveBitmap(bitmap: Bitmap, prefix: String = "img", asPng: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val ext = if (asPng) "png" else "jpg"
            val file = File(mediaDir, "${prefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
            FileOutputStream(file).use { out ->
                if (asPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
            file.absolutePath
        }

    suspend fun loadBitmap(path: String?, maxDimension: Int = 1024): Bitmap? =
        withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                var sample = 1
                while (opts.outWidth / sample > maxDimension || opts.outHeight / sample > maxDimension) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@withContext null
                val scaled = scaleDown(decoded, maxDimension)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } catch (_: Exception) {
                null
            }
        }

    suspend fun bitmapFromUri(uri: Uri, maxDimension: Int = 960): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val raw = decodeSampledFromUri(uri, maxDimension) ?: return@withContext null
                val scaled = scaleDown(raw, maxDimension)
                if (scaled !== raw) raw.recycle()
                scaled
            } catch (_: Exception) {
                null
            }
        }

    private fun decodeSampledFromUri(uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sample = 1
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
                sample *= 2
            }
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Client-side Canvas Studio Cutout: flood-fills perimeter background pixels within
     * threshold and applies edge feathering for crisp studio isolation.
     */
    suspend fun applyStudioCutout(
        source: Bitmap,
        threshold: Int = 36,
        edgeSmoothing: Float = 0.25f
    ): Bitmap = withContext(Dispatchers.Default) {
        val working = scaleDown(source, 640).copy(Bitmap.Config.ARGB_8888, true)
        val w = working.width
        val h = working.height
        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)

        // Sample average of 4 corners as background reference
        val corners = listOf(
            pixels[min(2, w - 1) + min(2, h - 1) * w],
            pixels[max(0, w - 3) + min(2, h - 1) * w],
            pixels[min(2, w - 1) + max(0, h - 3) * w],
            pixels[max(0, w - 3) + max(0, h - 3) * w]
        )
        val bgR = corners.map { Color.red(it) }.average()
        val bgG = corners.map { Color.green(it) }.average()
        val bgB = corners.map { Color.blue(it) }.average()

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>(w * 2 + h * 2)
        val maxDist = threshold * 2.2
        val featherStart = maxDist * (1.0 - edgeSmoothing.coerceIn(0.05f, 0.45f))

        for (x in 0 until w) {
            queue.add(x)
            queue.add((h - 1) * w + x)
        }
        for (y in 0 until h) {
            queue.add(y * w)
            queue.add(y * w + (w - 1))
        }

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (visited[pos]) continue
            visited[pos] = true
            val px = pixels[pos]
            val dr = Color.red(px) - bgR
            val dg = Color.green(px) - bgG
            val db = Color.blue(px) - bgB
            val dist = sqrt(dr * dr + dg * dg + db * db)
            if (dist <= maxDist) {
                val newAlpha = if (dist > featherStart && maxDist > featherStart) {
                    (((dist - featherStart) / (maxDist - featherStart)) * 210.0).roundToInt().coerceIn(0, 255)
                } else {
                    0
                }
                pixels[pos] = Color.argb(newAlpha, Color.red(px), Color.green(px), Color.blue(px))
                val x = pos % w
                val y = pos / w
                if (x > 0 && !visited[pos - 1]) queue.add(pos - 1)
                if (x < w - 1 && !visited[pos + 1]) queue.add(pos + 1)
                if (y > 0 && !visited[pos - w]) queue.add(pos - w)
                if (y < h - 1 && !visited[pos + w]) queue.add(pos + w)
            }
        }
        working.setPixels(pixels, 0, w, 0, 0, w, h)
        working
    }

    /**
     * Unlimited, free on-device Studio Flat-Lay: isolates the garment via cutout,
     * auto-crops tight to non-transparent bounds, adds a soft studio drop shadow,
     * and centers it on a crisp Warm Alabaster (#FAF8F5) square studio card.
     */
    suspend fun createOnDeviceStudioFlatLay(
        source: Bitmap,
        threshold: Int = 36,
        edgeSmoothing: Float = 0.25f
    ): Bitmap = withContext(Dispatchers.Default) {
        val cutout = applyStudioCutout(source, threshold, edgeSmoothing)
        val cw = cutout.width
        val ch = cutout.height
        val px = IntArray(cw * ch)
        cutout.getPixels(px, 0, cw, 0, 0, cw, ch)

        var minX = cw
        var minY = ch
        var maxX = 0
        var maxY = 0
        for (y in 0 until ch) {
            val row = y * cw
            for (x in 0 until cw) {
                if (Color.alpha(px[row + x]) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val cropped = if (maxX > minX + 16 && maxY > minY + 16) {
            Bitmap.createBitmap(cutout, minX, minY, maxX - minX + 1, maxY - minY + 1)
        } else {
            cutout
        }

        val outSize = 840
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#F6F9FC"))

        // Subtle editorial mat border
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#D2E3F4")
        }
        canvas.drawRect(22f, 22f, outSize - 22f, outSize - 22f, framePaint)

        // Soft studio ground shadow oval
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(24, 20, 20, 20)
        }
        canvas.drawOval(RectF(outSize * 0.22f, outSize * 0.82f, outSize * 0.78f, outSize * 0.89f), shadowPaint)

        // Center cropped garment cleanly with editorial whitespace
        val maxContent = outSize * 0.76f
        val sc = min(maxContent / cropped.width.toFloat(), maxContent / cropped.height.toFloat())
        val dw = cropped.width * sc
        val dh = cropped.height * sc
        val left = (outSize - dw) / 2f
        val top = (outSize - dh) / 2f - 10f
        canvas.drawBitmap(cropped, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))

        if (cropped !== cutout) cropped.recycle()
        cutout.recycle()
        out
    }

    /**
     * Unlimited, free on-device Editorial Look Preview Board:
     * composites the user's saved Virtual Model photo (if present) alongside the styled
     * garment ensemble on a scene-matched editorial backdrop, ready for A/B comparison.
     */
    suspend fun createEditorialLookBoard(
        title: String,
        sceneBackdrop: String,
        harmony: ColorHarmonyAnalysis,
        items: List<GarmentItem>,
        virtualModelPath: String?
    ): Bitmap = withContext(Dispatchers.IO) {
        val w = 960
        val h = 1180
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val isDarkScene = sceneBackdrop == "Evening"
        val bgHex = when (sceneBackdrop) {
            "Street" -> "#EBF2FA"
            "Office" -> "#F0F5FB"
            "Evening" -> "#121216"
            else -> "#F6F9FC"
        }
        val cardHex = if (isDarkScene) "#1D1D23" else "#FFFFFF"
        val inkHex = if (isDarkScene) "#F6F9FC" else "#1B2B3E"
        val subInkHex = if (isDarkScene) "#A4AAB2" else "#5A7188"

        canvas.drawColor(Color.parseColor(bgHex))

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.parseColor("#6DA6DA")
        }
        canvas.drawRect(32f, 32f, w - 32f, h - 32f, borderPaint)

        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6DA6DA")
            textSize = 20f
            letterSpacing = 0.16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(inkHex)
            textSize = 44f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(subInkHex)
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor(cardHex)
        }

        canvas.drawText("CRIEL LOOKBOARD  ·  ${sceneBackdrop.uppercase()} EDIT", 64f, 92f, eyebrowPaint)
        canvas.drawText(title.take(28), 64f, 150f, titlePaint)
        canvas.drawText("${harmony.scheme} · Harmony ${harmony.score}/100 · ${harmony.seasonMatchBadge}", 64f, 188f, subPaint)

        val contentTop = 224f
        val contentBottom = 1010f
        val modelBmp = loadBitmap(virtualModelPath, 840)

        if (modelBmp != null) {
            // Left panel: User's Virtual Model Portrait
            val modelRect = RectF(64f, contentTop, 488f, contentBottom)
            canvas.drawRoundRect(modelRect, 24f, 24f, cardPaint)
            val inner = RectF(modelRect.left + 14f, modelRect.top + 14f, modelRect.right - 14f, modelRect.bottom - 14f)
            val sc = min(inner.width() / modelBmp.width, inner.height() / modelBmp.height)
            val dw = modelBmp.width * sc
            val dh = modelBmp.height * sc
            canvas.drawBitmap(
                modelBmp,
                null,
                RectF(inner.centerX() - dw / 2f, inner.centerY() - dh / 2f, inner.centerX() + dw / 2f, inner.centerY() + dh / 2f),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            modelBmp.recycle()

            // Right panel: Layered outfit stack (2-column grid)
            val gridLeft = 514f
            val gridRight = w - 64f
            val cols = 2
            val rows = max(1, (items.take(6).size + 1) / 2)
            val cellW = (gridRight - gridLeft - 14f) / cols
            val cellH = (contentBottom - contentTop - (rows - 1) * 14f) / rows

            items.take(6).forEachIndexed { idx, item ->
                val c = idx % cols
                val r = idx / cols
                val x0 = gridLeft + c * (cellW + 14f)
                val y0 = contentTop + r * (cellH + 14f)
                val cellRect = RectF(x0, y0, x0 + cellW, y0 + cellH)
                canvas.drawRoundRect(cellRect, 20f, 20f, cardPaint)
                drawGarmentIntoRect(canvas, item, cellRect, subPaint)
            }
        } else {
            // Full-width runway editorial grid (up to 6 garments)
            val ordered = items.take(6)
            val cols = if (ordered.size <= 2) ordered.size.coerceAtLeast(1) else 2
            val rows = max(1, (ordered.size + cols - 1) / cols)
            val cellW = (w - 128f - (cols - 1) * 18f) / cols
            val cellH = (contentBottom - contentTop - (rows - 1) * 18f) / rows

            ordered.forEachIndexed { idx, item ->
                val c = idx % cols
                val r = idx / cols
                val x0 = 64f + c * (cellW + 18f)
                val y0 = contentTop + r * (cellH + 18f)
                val cellRect = RectF(x0, y0, x0 + cellW, y0 + cellH)
                canvas.drawRoundRect(cellRect, 22f, 22f, cardPaint)
                drawGarmentIntoRect(canvas, item, cellRect, subPaint)
            }
        }

        // Bottom color swatch strip & editorial footer
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#6DA6DA")
        }
        items.take(8).forEachIndexed { idx, item ->
            val cx = 86f + idx * 58f
            val cy = 1072f
            swatchPaint.color = try { Color.parseColor(item.colorHex) } catch (_: Exception) { Color.DKGRAY }
            canvas.drawCircle(cx, cy, 20f, swatchPaint)
            canvas.drawCircle(cx, cy, 20f, ringPaint)
        }
        canvas.drawText(
            "${items.size} PIECES  ·  ${formatInr(items.sumOf { it.price })} TOTAL",
            64f,
            1132f,
            eyebrowPaint.apply { textSize = 18f }
        )

        bmp
    }

    private suspend fun drawGarmentIntoRect(
        canvas: Canvas,
        item: GarmentItem,
        rect: RectF,
        labelPaint: Paint
    ) {
        val maxVisual = min(rect.width() - 28f, rect.height() - 56f).coerceAtLeast(48f)
        val cx = rect.centerX()
        val cy = rect.top + (rect.height() - 34f) / 2f
        val gBmp = loadBitmap(item.localImagePath, 420)
        if (gBmp != null) {
            val sc = min(maxVisual / gBmp.width, maxVisual / gBmp.height)
            val dw = gBmp.width * sc
            val dh = gBmp.height * sc
            canvas.drawBitmap(gBmp, null, RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
            gBmp.recycle()
        } else {
            drawGarmentVectorOnAndroidCanvas(canvas, item, cx, cy, maxVisual * 0.9f)
        }
        val capPaint = Paint(labelPaint).apply {
            textSize = 17f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(item.name.take(20), rect.centerX(), rect.bottom - 14f, capPaint)
    }

    /**
     * Crops normalized [0..1000] Gemini bounding box region from full-body photo.
     */
    suspend fun cropNormalizedBox(
        source: Bitmap,
        ymin: Int,
        xmin: Int,
        ymax: Int,
        xmax: Int,
        prefix: String = "extract"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val left = ((xmin.coerceIn(0, 990) / 1000f) * source.width).roundToInt()
            val top = ((ymin.coerceIn(0, 990) / 1000f) * source.height).roundToInt()
            val right = ((xmax.coerceIn(xmin + 10, 1000) / 1000f) * source.width).roundToInt()
            val bottom = ((ymax.coerceIn(ymin + 10, 1000) / 1000f) * source.height).roundToInt()
            val cropW = max(24, min(source.width - left, right - left))
            val cropH = max(24, min(source.height - top, bottom - top))
            val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
            val savedPath = saveBitmap(cropped, prefix, asPng = false)
            cropped.recycle()
            savedPath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun encodeImageToBase64(path: String?, maxDim: Int = 360): String? =
        withContext(Dispatchers.IO) {
            val bmp = loadBitmap(path, maxDim) ?: return@withContext null
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            bmp.recycle()
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

    suspend fun decodeBase64ToFile(base64: String, prefix: String = "restore"): String? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                val path = saveBitmap(bmp, prefix, asPng = false)
                bmp.recycle()
                path
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Multi-Template High-Resolution PNG Lookbook Exporter:
     * 1080x1350 Feed Card, 1080x1920 IG Story, or 1200x1200 Minimalist Grid Sheet.
     * Saves both to device Pictures/AtelierAI (via MediaStore) and returns a shareable cache File.
     */
    suspend fun exportLookbookPng(
        template: LookbookExportTemplate,
        title: String,
        weatherLabel: String,
        harmony: ColorHarmonyAnalysis,
        items: List<GarmentItem>,
        tryOnImagePath: String? = null
    ): File = withContext(Dispatchers.IO) {
        val w = template.width
        val h = template.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Porcelain off-white background + Sky Blue frame
        canvas.drawColor(Color.parseColor("#F6F9FC"))
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#6DA6DA")
        }
        canvas.drawRect(44f, 44f, w - 44f, h - 44f, borderPaint)

        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A72A8")
            textSize = 22f
            letterSpacing = 0.16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B2B3E")
            textSize = if (template == LookbookExportTemplate.IG_STORY) 62f else 52f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5A7188")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val fillIvory = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EAF2FA")
            style = Paint.Style.FILL
        }

        // Masthead
        canvas.drawText("CRIEL  ·  ${weatherLabel.uppercase()}", 84f, 114f, eyebrowPaint)
        canvas.drawText(title.take(32), 84f, 186f, titlePaint)

        // Harmony pill
        canvas.drawRoundRect(RectF(84f, 212f, 590f, 260f), 24f, 24f, fillIvory)
        canvas.drawText("${harmony.scheme.uppercase()}  ·  ${harmony.score}/100  ·  ${harmony.seasonMatchBadge}", 104f, 244f, eyebrowPaint.apply { textSize = 18f })

        // Showcase canvas area
        val showcaseTop = 290f
        val showcaseBottom = when (template) {
            LookbookExportTemplate.IG_STORY -> 1320f
            LookbookExportTemplate.FEED_CARD -> 890f
            LookbookExportTemplate.MINIMALIST_GRID -> 820f
        }
        canvas.drawRoundRect(RectF(84f, showcaseTop, w - 84f, showcaseBottom), 28f, 28f, fillIvory)

        val tryOnBmp = if (!tryOnImagePath.isNullOrBlank() && template != LookbookExportTemplate.MINIMALIST_GRID) {
            loadBitmap(tryOnImagePath, 1080)
        } else null

        if (tryOnBmp != null) {
            val dest = RectF(108f, showcaseTop + 24f, w - 108f, showcaseBottom - 24f)
            val scale = min(dest.width() / tryOnBmp.width, dest.height() / tryOnBmp.height)
            val dw = tryOnBmp.width * scale
            val dh = tryOnBmp.height * scale
            val drawRect = RectF(
                dest.centerX() - dw / 2f,
                dest.centerY() - dh / 2f,
                dest.centerX() + dw / 2f,
                dest.centerY() + dh / 2f
            )
            canvas.drawBitmap(tryOnBmp, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
            tryOnBmp.recycle()
        } else {
            val cols = min(max(1, items.size), 3)
            val rows = max(1, (items.size + cols - 1) / cols)
            val cellW = (w - 168f) / cols
            val cellH = (showcaseBottom - showcaseTop) / rows

            items.take(6).forEachIndexed { idx, item ->
                val col = idx % cols
                val row = idx / cols
                val cx = 84f + col * cellW + cellW / 2f
                val cy = showcaseTop + row * cellH + cellH / 2f
                val maxBox = min(cellW, cellH) * 0.72f
                val bmp = loadBitmap(item.localImagePath, 420)
                if (bmp != null) {
                    val sc = min(maxBox / bmp.width, maxBox / bmp.height)
                    val dw = bmp.width * sc
                    val dh = bmp.height * sc
                    canvas.drawBitmap(bmp, null, RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
                    bmp.recycle()
                } else {
                    drawGarmentVectorOnAndroidCanvas(canvas, item, cx, cy, maxBox * 0.85f)
                }
            }
        }

        // Swatches
        var y = showcaseBottom + 58f
        eyebrowPaint.textSize = 18f
        canvas.drawText("COLOR PALETTE", 84f, y, eyebrowPaint)
        y += 42f
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#33141414")
        }
        items.take(8).forEachIndexed { idx, item ->
            val sx = 108f + idx * 62f
            swatchPaint.color = try { Color.parseColor(item.colorHex) } catch (_: Exception) { Color.DKGRAY }
            canvas.drawCircle(sx, y, 22f, swatchPaint)
            canvas.drawCircle(sx, y, 22f, ringPaint)
        }

        // Ensemble Breakdown
        y += 66f
        val totalVal = items.sumOf { it.price }.toInt()
        canvas.drawText("ENSEMBLE BREAKDOWN", 84f, y, eyebrowPaint)
        canvas.drawText("TOTAL: ${formatInr(totalVal)}", w - 310f, y, eyebrowPaint)
        y += 40f

        val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B2B3E")
            textSize = 24f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        items.take(6).forEach { item ->
            if (y < h - 90f) {
                canvas.drawText(item.name.take(30), 84f, y, itemTitlePaint)
                canvas.drawText("${item.brand} · ${item.colorName}", w * 0.54f, y, bodyPaint.apply { textSize = 20f })
                canvas.drawText(formatInr(item.price), w - 200f, y, itemTitlePaint)
                y += 38f
            }
        }

        canvas.drawText("CURATED WITH CRIEL", 84f, h - 68f, bodyPaint.apply {
            textSize = 16f
            letterSpacing = 0.18f
        })

        // Save to cache file for instant share intent
        val outFile = File(exportCacheDir, "criel_${template.name.lowercase()}_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 96, fos)
        }

        // Also save to device Pictures/Criel via MediaStore on Q+ (zero permissions required!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, outFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Criel")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 96, os)
                    }
                }
            } catch (_: Exception) {
            }
        }
        bitmap.recycle()
        outFile
    }

    private fun drawGarmentVectorOnAndroidCanvas(
        canvas: Canvas,
        item: GarmentItem,
        cx: Float,
        cy: Float,
        size: Float
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = try { Color.parseColor(item.colorHex) } catch (_: Exception) { Color.parseColor("#141414") }
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = if (ColorTheoryEngine.isLightColor(item.colorHex)) Color.parseColor("#8C8478") else Color.parseColor("#44FFFFFF")
        }
        val left = cx - size / 2f
        val top = cy - size / 2f
        val s = size / 120f
        fun px(x: Float) = left + x * s
        fun py(y: Float) = top + y * s

        val path = Path()
        when (GarmentCategory.fromLabel(item.category)) {
            GarmentCategory.OUTERWEAR -> {
                path.moveTo(px(40f), py(16f))
                path.lineTo(px(60f), py(30f))
                path.lineTo(px(80f), py(16f))
                path.lineTo(px(98f), py(28f))
                path.lineTo(px(106f), py(76f))
                path.lineTo(px(88f), py(78f))
                path.lineTo(px(84f), py(46f))
                path.lineTo(px(86f), py(104f))
                path.lineTo(px(34f), py(104f))
                path.lineTo(px(36f), py(46f))
                path.lineTo(px(32f), py(78f))
                path.lineTo(px(14f), py(76f))
                path.lineTo(px(22f), py(28f))
                path.close()
            }
            GarmentCategory.TOPS -> {
                path.moveTo(px(44f), py(20f))
                path.lineTo(px(60f), py(30f))
                path.lineTo(px(76f), py(20f))
                path.lineTo(px(100f), py(36f))
                path.lineTo(px(90f), py(58f))
                path.lineTo(px(80f), py(50f))
                path.lineTo(px(80f), py(102f))
                path.lineTo(px(40f), py(102f))
                path.lineTo(px(40f), py(50f))
                path.lineTo(px(30f), py(58f))
                path.lineTo(px(20f), py(36f))
                path.close()
            }
            GarmentCategory.BOTTOMS -> {
                path.moveTo(px(34f), py(18f))
                path.lineTo(px(86f), py(18f))
                path.lineTo(px(90f), py(106f))
                path.lineTo(px(66f), py(106f))
                path.lineTo(px(60f), py(50f))
                path.lineTo(px(54f), py(106f))
                path.lineTo(px(30f), py(106f))
                path.close()
            }
            GarmentCategory.FOOTWEAR -> {
                path.moveTo(px(28f), py(34f))
                path.lineTo(px(64f), py(34f))
                path.lineTo(px(64f), py(66f))
                path.lineTo(px(98f), py(74f))
                path.lineTo(px(102f), py(90f))
                path.lineTo(px(24f), py(90f))
                path.close()
            }
            GarmentCategory.BAGS -> {
                path.moveTo(px(26f), py(42f))
                path.lineTo(px(94f), py(42f))
                path.lineTo(px(88f), py(98f))
                path.lineTo(px(32f), py(98f))
                path.close()
            }
            GarmentCategory.ACCESSORIES -> {
                path.addRoundRect(RectF(px(18f), py(50f), px(102f), py(70f)), 10f, 10f, Path.Direction.CW)
            }
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (w * ratio).roundToInt()),
            max(1, (h * ratio).roundToInt()),
            true
        )
    }
}
