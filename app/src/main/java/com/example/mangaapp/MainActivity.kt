package com.example.mangaapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        var userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        var cookies: String = ""
        var cookieMap: Map<String, String> = emptyMap()
    }
    private lateinit var webView: WebView
    private lateinit var appContent: LinearLayout
    private lateinit var adapter: MangaAdapter
    private lateinit var progressBar: ProgressBar
    private val BASE_URL = "https://likemanga.ink"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        appContent = findViewById(R.id.appContent)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = MangaAdapter(emptyList()) { manga ->
            val intent = Intent(this, MangaActivity::class.java)
            intent.putExtra("MANGA_URL", BASE_URL + manga.path)
            intent.putExtra("MANGA_TITLE", manga.title)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val cookieStr = CookieManager.getInstance().getCookie(url)
                if (cookieStr != null) {
                    cookies = cookieStr
                    cookieMap = cookies.split(";").associate { 
                        val (k, v) = it.trim().split("=", limit=2) + listOf("")
                        k to v 
                    }
                }
                if (view?.title?.contains("Just a moment") == false && cookies.contains("cf_clearance")) {
                    Toast.makeText(applicationContext, "Bypass Successful!", Toast.LENGTH_SHORT).show()
                    webView.visibility = View.GONE
                    appContent.visibility = View.VISIBLE
                }
            }
        }

        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val query = etSearch.text.toString()
            if (query.isNotEmpty()) searchManga(query)
        }
        findViewById<Button>(R.id.btnBypass).setOnClickListener {
            webView.visibility = View.VISIBLE
            appContent.visibility = View.GONE
            webView.loadUrl(BASE_URL)
        }
    }

    private fun searchManga(query: String) {
        progressBar.visibility = View.VISIBLE
        thread {
            try {
                val searchUrl = "$BASE_URL/?act=search&f[keyword]=${URLEncoder.encode(query, "UTF-8")}&f[sortby]=lastest-chap&pageNum=1"
                val doc = Jsoup.connect(searchUrl).userAgent(userAgent).cookies(cookieMap).timeout(10000).get()
                val list = doc.select("div.video").mapNotNull { el ->
                    val title = el.selectOne(".title-manga")?.text()
                    val path = el.selectOne("a")?.attr("href")
                    val img = el.selectOne("img")?.attr("src")
                    if (title != null && path != null) {
                        MangaItem(title, path, if (img?.startsWith("/") == true) BASE_URL + img else img ?: "")
                    } else null
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    adapter.updateList(list)
                }
            } catch (e: Exception) {
                runOnUiThread { progressBar.visibility = View.GONE }
            }
        }
    }
}