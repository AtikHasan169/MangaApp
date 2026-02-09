package com.example.mangaapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class MangaAdapter(
    private var mangaList: List<MangaItem>,
    private val onClick: (MangaItem) -> Unit
) : RecyclerView.Adapter<MangaAdapter.MangaViewHolder>() {

    class MangaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.itemTitle)
        val image: ImageView = view.findViewById(R.id.itemImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
        return MangaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        val manga = mangaList[position]
        holder.title.text = manga.title
        val glideUrl = if (manga.coverUrl.isNotEmpty()) {
            GlideUrl(manga.coverUrl, LazyHeaders.Builder()
                .addHeader("User-Agent", MainActivity.userAgent)
                .addHeader("Cookie", MainActivity.cookies)
                .build())
        } else null
        Glide.with(holder.itemView.context).load(glideUrl).into(holder.image)
        holder.itemView.setOnClickListener { onClick(manga) }
    }

    override fun getItemCount() = mangaList.size
    fun updateList(newList: List<MangaItem>) {
        mangaList = newList
        notifyDataSetChanged()
    }
}