package com.example.mangaapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        // We will overwrite this with the REAL WebView User Agent
        var userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        var cookies: String = ""
        var cookieMap: Map<String, String> = emptyMap()
        const val BASE_URL = "https://likemanga.ink"
    }

    private lateinit var webView: WebView
    private lateinit var adapter: MangaAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSearch: LinearLayout
    private lateinit var btnTabSearch: Button
    private lateinit var btnTabLibrary: Button
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    
    private val gson = Gson()
    private val historyList = mutableListOf<MangaItem>()
    private var isSearchTab = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        layoutSearch = findViewById(R.id.layoutSearch)
        btnTabSearch = findViewById(R.id.btnTabSearch)
        btnTabLibrary = findViewById(R.id.btnTabLibrary)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        // Setup WebView FIRST to get the correct User Agent
        setupWebView()

        // Load History
        loadHistory()

        // Setup RecyclerView
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = MangaAdapter(emptyList()) { manga ->
            addToHistory(manga)
            val intent = Intent(this, MangaActivity::class.java)
            intent.putExtra("MANGA_URL", BASE_URL + manga.path)
            intent.putExtra("MANGA_TITLE", manga.title)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Tab Listeners
        btnTabSearch.setOnClickListener { switchTab(true) }
        btnTabLibrary.setOnClickListener { switchTab(false) }

        // Search Listener
        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val query = etSearch.text.toString()
            if (query.isNotEmpty()) searchManga(query)
        }
        
        // Bypass Listener
        findViewById<Button>(R.id.btnBypass).setOnClickListener {
            webView.visibility = View.VISIBLE
            webView.loadUrl(BASE_URL)
        }
    }

    private fun switchTab(search: Boolean) {
        isSearchTab = search
        if (search) {
            btnTabSearch.setTextColor(0xFF00E676.toInt())
            btnTabLibrary.setTextColor(0xFF888888.toInt())
            layoutSearch.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            adapter.updateList(emptyList()) 
        } else {
            btnTabSearch.setTextColor(0xFF888888.toInt())
            btnTabLibrary.setTextColor(0xFF00E676.toInt())
            layoutSearch.visibility = View.GONE
            loadHistory()
            adapter.updateList(historyList)
            tvEmpty.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun addToHistory(manga: MangaItem) {
        historyList.removeAll { it.path == manga.path }
        historyList.add(0, manga)
        if (historyList.size > 50) historyList.removeAt(historyList.size - 1)
        saveHistory()
    }

    private fun saveHistory() {
        val json = gson.toJson(historyList)
        getSharedPreferences("manga_data", Context.MODE_PRIVATE).edit()
            .putString("history", json)
            .apply()
    }

    private fun loadHistory() {
        val json = getSharedPreferences("manga_data", Context.MODE_PRIVATE).getString("history", null)
        if (json != null) {
            val type = object : TypeToken<List<MangaItem>>() {}.type
            historyList.clear()
            historyList.addAll(gson.fromJson(json, type))
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        // CRITICAL FIX: Grab the EXACT User Agent the WebView is using
        userAgent = webView.settings.userAgentString
        
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
                // Auto-hide webview if we detect we passed the check
                if (view?.title?.contains("Just a moment") == false && cookies.contains("cf_clearance")) {
                    Toast.makeText(applicationContext, "Bypass Successful!", Toast.LENGTH_SHORT).show()
                    webView.visibility = View.GONE
                }
            }
        }
    }

    private fun searchManga(query: String) {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        thread {
            try {
                val searchUrl = "$BASE_URL/?act=search&f[keyword]=${URLEncoder.encode(query, "UTF-8")}&f[sortby]=lastest-chap&pageNum=1"
                
                // Use the synced User Agent and Cookies
                val doc = Jsoup.connect(searchUrl)
                    .userAgent(userAgent)
                    .cookies(cookieMap)
                    .timeout(10000)
                    .get()
                
                // Fixed selectFirst logic
                val list = doc.select("div.video").mapNotNull { el ->
                    val title = el.selectFirst(".title-manga")?.text()
                    val path = el.selectFirst("a")?.attr("href")
                    val img = el.selectFirst("img")?.attr("src")
                    
                    if (title != null && path != null) {
                        MangaItem(title, path, if (img?.startsWith("/") == true) BASE_URL + img else img ?: "")
                    } else null
                }
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (isSearchTab) adapter.updateList(list)
                }
            } catch (e: Exception) {
                runOnUiThread { 
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!isSearchTab) {
            loadHistory()
            adapter.updateList(historyList)
        }
    }
}
