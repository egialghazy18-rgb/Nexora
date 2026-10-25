package com.nexora.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val apiBase = "http://127.0.0.1:8000"
    private val executor = Executors.newSingleThreadExecutor()
    private val imageExecutor = Executors.newFixedThreadPool(3)

    private lateinit var searchInput: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView

    private val bg = Color.rgb(11, 15, 25)
    private val surface = Color.rgb(23, 30, 45)
    private val blue = Color.rgb(75, 139, 255)
    private val white = Color.rgb(244, 247, 255)
    private val muted = Color.rgb(151, 163, 184)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUI()
    }

    private fun buildUI() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = label("N", 24f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(blue, 15)
        }

        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        brand.addView(label("NEXORA", 21f, white, true))
        brand.addView(label("M U S I C", 10f, muted, true))

        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("● ONLINE", 11f, Color.rgb(83, 214, 157), true))
        content.addView(header)

        space(content, 28)
        content.addView(label("Temukan musikmu.", 27f, white, true))

        val subtitle = label(
            "Dengarkan lagu favorit kapan saja.",
            13f,
            muted
        )
        subtitle.setPadding(0, dp(7), 0, 0)
        content.addView(subtitle)

        // FEATURE CARD
        space(content, 22)

        val feature = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(36, 78, 145),
                    Color.rgb(25, 43, 77),
                    Color.rgb(26, 32, 50)
                )
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
        }

        feature.addView(label("NEXORA COLLECTION", 10f, Color.rgb(150, 190, 255), true))

        val headline = label(
            "Your music.\nYour moment.",
            27f,
            white,
            true
        )
        headline.setPadding(0, dp(12), 0, dp(8))
        feature.addView(headline)

        feature.addView(
            label(
                "Cari lagu baru dan nikmati musik favoritmu.",
                12f,
                Color.rgb(210, 220, 239)
            )
        )

        content.addView(
            feature,
            LinearLayout.LayoutParams(-1, -2)
        )

        // SEARCH
        space(content, 26)
        content.addView(label("Cari musik", 19f, white, true))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        searchInput = EditText(this).apply {
            hint = "Judul lagu atau nama artis..."
            textSize = 13f
            maxLines = 1
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(surface, 14)
        }

        searchRow.addView(
            searchInput,
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(8)
            }
        )

        val searchButton = label("Cari", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            background = rounded(blue, 14)
            setOnClickListener {
                searchSongs(searchInput.text.toString().trim())
            }
        }

        searchRow.addView(
            searchButton,
            LinearLayout.LayoutParams(-2, dp(52))
        )

        content.addView(
            searchRow,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            }
        )

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchSongs(searchInput.text.toString().trim())
                true
            } else {
                false
            }
        }

        status = label("Siap mencari musik favoritmu.", 12f, muted)
        status.setPadding(0, dp(20), 0, dp(12))
        content.addView(status)

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(results)

        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(0, 0, 1f))

        // MINI PLAYER
        val player = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(surface, 18)
        }

        val musicIcon = label("♫", 25f, Color.rgb(117, 166, 255)).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(30, 39, 57), 12)
        }

        player.addView(musicIcon, LinearLayout.LayoutParams(dp(46), dp(46)))

        val playerInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        miniTitle = label("Nexora Music", 13f, white, true)
        miniArtist = label("Pilih lagu untuk memutar", 10f, muted)

        playerInfo.addView(miniTitle)
        playerInfo.addView(miniArtist)

        player.addView(playerInfo, LinearLayout.LayoutParams(0, -2, 1f))
        player.addView(label("▶", 19f, Color.rgb(117, 166, 255), true))

        page.addView(
            player,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(12), dp(8), dp(12), dp(10))
            }
        )

        setContentView(page)
    }

    private fun searchSongs(query: String) {
        if (query.isBlank()) {
            status.text = "Masukkan judul lagu atau nama artis."
            return
        }

        val keyboard = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager

        keyboard.hideSoftInputFromWindow(searchInput.windowToken, 0)

        status.text = "Sedang mencari musik..."
        results.removeAllViews()

        executor.execute {
            var connection: HttpURLConnection? = null

            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                connection = URL(
                    "$apiBase/search?q=$encoded&limit=20"
                ).openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream.bufferedReader().use { it.readText() }

                if (code !in 200..299) {
                    throw Exception("HTTP $code")
                }

                val json = JSONObject(response)
                if (!json.optBoolean("success", false)) {
                    throw Exception("Pencarian API gagal.")
                }

                val songs = json.optJSONArray("results")
                    ?: throw Exception("Data hasil tidak tersedia.")

                runOnUiThread {
                    if (songs.length() == 0) {
                        status.text = "Musik tidak ditemukan."
                        return@runOnUiThread
                    }

                    status.text = "${songs.length()} hasil ditemukan"

                    for (i in 0 until songs.length()) {
                        addSong(songs.getJSONObject(i))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Pencarian gagal: ${e.message}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun addSong(song: JSONObject) {
        val title = song.optString("title", "Musik tidak diketahui")
        val artist = song.optString("artist", "Artis tidak diketahui")
        val thumbnail = song.optString("thumbnail", "")
        val streamPath = song.optString("url", "")
        val duration = song.optLong("duration", 0)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(surface, 18)
        }

        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(30, 39, 57))
            background = rounded(Color.rgb(30, 39, 57), 12)
            clipToOutline = true
            setImageResource(android.R.drawable.ic_media_play)
            imageTintList = android.content.res.ColorStateList.valueOf(
                Color.rgb(117, 166, 255)
            )
        }

        card.addView(cover, LinearLayout.LayoutParams(dp(66), dp(66)))

        if (thumbnail.startsWith("https://")) {
            imageExecutor.execute {
                try {
                    val bitmap = URL(thumbnail).openStream().use {
                        BitmapFactory.decodeStream(it)
                    }

                    if (bitmap != null) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                cover.setImageBitmap(bitmap)
                                cover.imageTintList = null
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        info.addView(label(title, 13f, white, true).apply {
            maxLines = 2
        })
        info.addView(label(artist, 11f, muted).apply {
            maxLines = 1
        })

        info.addView(
            label(
                if (duration > 0) formatDuration(duration) else "Nexora Music",
                10f,
                Color.rgb(117, 166, 255)
            )
        )

        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

        val play = label("▶", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(blue, 14)
            setOnClickListener {
                if (streamPath.isBlank()) {
                    status.text = "URL streaming tidak tersedia."
                    return@setOnClickListener
                }

                val streamUrl = if (
                    streamPath.startsWith("http://") ||
                    streamPath.startsWith("https://")
                ) {
                    streamPath
                } else {
                    "$apiBase/${streamPath.trimStart('/')}"
                }

                playSong(streamUrl, title, artist)
            }
        }

        card.addView(play, LinearLayout.LayoutParams(dp(44), dp(44)))

        results.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun playSong(url: String, title: String, artist: String) {
        try {
            val intent = Intent(this, PlaybackService::class.java).apply {
                action = "com.nexora.music.PLAY"
                putExtra("url", url)
                putExtra("title", title)
                putExtra("artist", artist)
            }

            startForegroundService(intent)
            miniTitle.text = title
            miniArtist.text = artist
            status.text = "Memulai pemutaran: $title"
        } catch (e: Exception) {
            status.text = "Gagal memutar: ${e.message}"
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun formatDuration(seconds: Long): String {
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        imageExecutor.shutdownNow()
        super.onDestroy()
    }
}
