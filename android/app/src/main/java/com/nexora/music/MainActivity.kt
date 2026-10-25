package com.nexora.music

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // Ganti dengan URL backend Flask HTTPS milikmu.
    private val apiBase = "https://YOUR_API_HOST"
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var results: LinearLayout
    private lateinit var status: TextView

    private val blue = Color.rgb(55, 132, 255)
    private val background = Color.rgb(8, 11, 18)
    private val white = Color.rgb(240, 244, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = background
        window.navigationBarColor = background

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 12)
            setBackgroundColor(background)
        }

        root.addView(TextView(this).apply {
            text = "NEXORA"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
        })

        root.addView(TextView(this).apply {
            text = "MUSIC PLAYER"
            textSize = 11f
            letterSpacing = 0.25f
            setTextColor(white)
        })

        val search = EditText(this).apply {
            hint = "Cari lagu atau artis..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(white)
            singleLine = true
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.rgb(25, 31, 45))
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 58
            ).apply { topMargin = 28 }
        )

        val searchButton = Button(this).apply {
            text = "CARI LAGU"
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            setOnClickListener { searchSongs(search.text.toString().trim()) }
        }
        root.addView(searchButton)

        status = TextView(this).apply {
            text = "Cari lagu favoritmu"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 20, 0, 12)
        }
        root.addView(status)

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(results)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        setContentView(root)
    }

    private fun searchSongs(query: String) {
        if (query.isBlank()) {
            status.text = "Masukkan judul lagu terlebih dahulu."
            return
        }

        if (apiBase.contains("YOUR_API_HOST")) {
            status.text = "Atur alamat server Flask di MainActivity.kt."
            return
        }

        status.text = "Mencari lagu..."
        results.removeAllViews()

        executor.execute {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val connection = URL("$apiBase/search?q=$encoded&limit=20")
                    .openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                try {
                    val response = connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                    val json = JSONObject(response)
                    val songs = json.getJSONArray("results")

                    runOnUiThread {
                        status.text = "${songs.length()} hasil pencarian"

                        for (i in 0 until songs.length()) {
                            val song = songs.getJSONObject(i)
                            val title = song.optString("title", "Unknown")
                            val artist = song.optString("artist", "Unknown artist")
                            val stream = song.optString("url", "")

                            val item = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(14, 14, 14, 14)
                                setBackgroundColor(Color.rgb(20, 26, 38))
                            }

                            item.addView(TextView(this).apply {
                                text = title
                                textSize = 16f
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(white)
                            })

                            item.addView(TextView(this).apply {
                                text = artist
                                textSize = 12f
                                setTextColor(Color.LTGRAY)
                            })

                            item.addView(Button(this).apply {
                                text = "▶ PUTAR"
                                setTextColor(Color.WHITE)
                                setBackgroundColor(blue)
                                setOnClickListener {
                                    playSong(
                                        "$apiBase$stream",
                                        title,
                                        artist
                                    )
                                }
                            })

                            results.addView(
                                item,
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply { bottomMargin = 12 }
                            )
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Gagal mencari lagu: periksa server dan koneksi."
                }
            }
        }
    }

    private fun playSong(url: String, title: String, artist: String) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = "com.nexora.music.PLAY"
            putExtra("url", url)
            putExtra("title", title)
            putExtra("artist", artist)
        }
        startForegroundService(intent)
        status.text = "Memutar: $title"
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
