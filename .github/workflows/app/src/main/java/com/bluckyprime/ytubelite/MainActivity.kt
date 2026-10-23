
package com.bluckyprime.ytubelite

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.content.ContentValues
import android.graphics.Color
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.FFmpeg
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val processId = "ytube-lite-download"

    private lateinit var urlInput: EditText
    private lateinit var mp4Radio: RadioButton
    private lateinit var mp3Radio: RadioButton
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    @Volatile
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        statusText.text = "Initializing downloader..."
        downloadButton.isEnabled = false

        executor.execute {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)

                runOnUiThread {
                    ready = true
                    statusText.text = "Ready to download"
                    downloadButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Initialization failed:\n${e.message}"
                }
            }
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(24))
            setBackgroundColor(Color.rgb(248, 249, 252))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "▶ YTube Lite"
            textSize = 29f
            setTextColor(Color.rgb(30, 50, 90))
        }, params())

        root.addView(TextView(this).apply {
            text = "Video & audio downloader"
            textSize = 16f
        }, params())

        urlInput = EditText(this).apply {
            hint = "Paste video URL here"
            singleLine = true
        }
        root.addView(urlInput, params())

        val formats = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        mp4Radio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "MP4 Video — Best available"
            isChecked = true
        }

        mp3Radio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "MP3 Audio — Best quality"
        }

        formats.addView(mp4Radio)
        formats.addView(mp3Radio)
        root.addView(formats, params())

        downloadButton = Button(this).apply {
            text = "DOWNLOAD"
            setOnClickListener { startDownload() }
        }
        root.addView(downloadButton, params())

        progressBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
        }
        root.addView(progressBar, params())

        statusText = TextView(this).apply {
            text = "Starting..."
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        root.addView(statusText, params())

        root.addView(TextView(this).apply {
            text = "Downloads are saved to Downloads/YTube Lite."
            textSize = 13f
            setTextColor(Color.GRAY)
        }, params())
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()

        if (!ready) {
            statusText.text = "Downloader is still initializing."
            return
        }

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            urlInput.error = "Enter a valid video URL"
            return
        }

        val audioOnly = mp3Radio.isChecked
        downloadButton.isEnabled = false
        progressBar.progress = 0
        statusText.text = "Preparing download..."

        executor.execute {
            try {
                val base = getExternalFilesDir(
                    Environment.DIRECTORY_DOWNLOADS
                ) ?: cacheDir

                val outputDir = File(base, "YTubeLite")
                if (!outputDir.exists() && !outputDir.mkdirs()) {
                    throw IOException("Cannot create download folder.")
                }

                outputDir.listFiles()?.forEach { it.delete() }

                val template = File(
                    outputDir, "%(title).150B [%(id)s].%(ext)s"
                ).absolutePath

                val request = YoutubeDLRequest(url)
                request.addOption("--no-mtime")
                request.addOption("--force-overwrites")
                request.addOption("-o", template)
                request.addOption(
                    "--extractor-args",
                    "youtube:player_client=android"
                )

                if (audioOnly) {
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                } else {
                    request.addOption("-f", "b/bv*+ba")
                    request.addOption("--merge-output-format", "mp4")
                }

                runOnUiThread {
                    statusText.text = "Connecting to source..."
                }

                YoutubeDL.getInstance().execute(
                    request,
                    processId
                ) { progress, eta, line ->
                    runOnUiThread {
                        progressBar.progress =
                            progress.toInt().coerceIn(0, 100)
                        statusText.text = line.take(180).ifBlank {
                            "Downloading... ETA: ${eta}s"
                        }
                    }
                }

                val extensions = if (audioOnly) {
                    setOf("mp3")
                } else {
                    setOf("mp4", "mkv", "webm", "m4v")
                }

                val output = outputDir.listFiles()
                    ?.filter {
                        it.isFile &&
                            it.extension.lowercase() in extensions &&
                            !it.name.endsWith(".part")
                    }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IOException("No media file was created.")

                val mime = if (audioOnly) "audio/mpeg" else "video/mp4"

                publishToDownloads(output, mime)

                runOnUiThread {
                    progressBar.progress = 100
                    statusText.text = "Saved: ${output.name}"
                    downloadButton.isEnabled = true
                    Toast.makeText(
                        this, "Download completed", Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text =
                        "Download failed:\n${e.message ?: e.javaClass.simpleName}"
                    downloadButton.isEnabled = true
                }
            }
        }
    }

    private fun publishToDownloads(source: File, mimeType: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/YTube Lite"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: throw IOException("Could not create Downloads file.")

        try {
            val destination = contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open output file.")

            destination.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            val finished = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(uri, finished, null, null)

        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun params() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}
