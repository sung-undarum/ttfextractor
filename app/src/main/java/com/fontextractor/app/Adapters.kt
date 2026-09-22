package com.fontextractor.app

import android.graphics.Typeface
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.fontextractor.app.databinding.ItemAppBinding
import com.fontextractor.app.databinding.ItemFontBinding

class AppAdapter(private val onClick: (AppEntry) -> Unit) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppEntry> = emptyList()

    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val ctx = holder.b.root.context
        holder.b.label.text = app.label
        holder.b.pkg.text = app.packageName
        holder.b.meta.text = ctx.getString(
            R.string.app_meta,
            app.fontCount,
            Formatter.formatShortFileSize(ctx, app.totalSize)
        )
        holder.b.badge.isVisible = app.isFontLike
        val icon = try { ctx.packageManager.getApplicationIcon(app.packageName) } catch (e: Exception) { null }
        holder.b.icon.setImageDrawable(icon)
        holder.b.root.setOnClickListener { onClick(app) }
    }
}

class FontAdapter(
    private val items: List<FontItem>,
    private val onToggle: (Int) -> Unit,
    private val onShare: (FontItem) -> Unit
) : RecyclerView.Adapter<FontAdapter.VH>() {

    class VH(val b: ItemFontBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFontBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.b.root.context
        val names = item.names

        holder.b.checkbox.isChecked = item.checked

        val title = names?.fullName ?: names?.family ?: item.entry.fileName
        holder.b.name.text = title

        val sub = listOfNotNull(
            names?.family?.takeIf { it != title },
            names?.subfamily
        ).joinToString(" · ")
        val size = Formatter.formatShortFileSize(ctx, item.entry.size.coerceAtLeast(0))
        holder.b.meta.text = buildString {
            if (sub.isNotEmpty()) append(sub).append('\n')
            append(item.entry.entryName).append(" · ").append(size)
        }

        holder.b.preview.typeface = item.typeface ?: Typeface.DEFAULT
        holder.b.preview.text = when {
            !item.ready -> ctx.getString(R.string.loading)
            item.typeface == null -> ctx.getString(R.string.preview_unavailable)
            else -> ctx.getString(R.string.preview_text)
        }

        holder.b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onToggle(pos)
        }
        holder.b.share.setOnClickListener { onShare(item) }
    }
}
