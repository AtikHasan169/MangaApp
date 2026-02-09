package com.example.mangaapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup
import kotlin.concurrent.thread

class MangaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manga)
        val url = intent.getStringExtra("MANGA_URL") ?: return
        val listView = findViewById<ListView>(R.id.chapterListView)

        thread {
            try {
                val doc = Jsoup.connect(url).userAgent(MainActivity.userAgent).cookies(MainActivity.cookieMap).get()
                val chapters = doc.select("li.wp-manga-chapter a").map { 
                    it.text() to it.attr("href") 
                }
                runOnUiThread {
                    listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chapters.map { it.first })
                    listView.setOnItemClickListener { _, _, i, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(chapters[i].second)))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}