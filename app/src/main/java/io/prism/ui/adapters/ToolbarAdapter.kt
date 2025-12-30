package io.prism.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.prism.R
import io.prism.ui.BottomSheetType

class ToolbarAdapter(
    private val onItemClick: (BottomSheetType) -> Unit
) : RecyclerView.Adapter<ToolbarAdapter.ToolbarViewHolder>() {

    private val items = listOf(
        ToolbarItem(BottomSheetType.TEXT, R.drawable.ic_text, R.string.toolbar_text),
        ToolbarItem(BottomSheetType.STYLE, R.drawable.ic_style, R.string.toolbar_style),
        ToolbarItem(BottomSheetType.LOGO, R.drawable.ic_logo, R.string.toolbar_logo),
        ToolbarItem(BottomSheetType.FONTS, R.drawable.ic_font, R.string.toolbar_fonts),
        ToolbarItem(BottomSheetType.POSITION, R.drawable.ic_position, R.string.toolbar_position),
        ToolbarItem(BottomSheetType.SCALE, R.drawable.ic_scale, R.string.toolbar_scale),
        ToolbarItem(BottomSheetType.THEME, R.drawable.ic_theme, R.string.toolbar_theme),
        ToolbarItem(BottomSheetType.EXIF, R.drawable.ic_exif, R.string.toolbar_exif),
        ToolbarItem(BottomSheetType.COLORS, R.drawable.ic_color, R.string.toolbar_colors)
    )

    data class ToolbarItem(
        val type: BottomSheetType,
        val iconRes: Int,
        val labelRes: Int
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolbarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_toolbar, parent, false)
        return ToolbarViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolbarViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ToolbarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.toolbarIcon)
        private val label: TextView = itemView.findViewById(R.id.toolbarLabel)

        fun bind(item: ToolbarItem) {
            icon.setImageResource(item.iconRes)
            label.setText(item.labelRes)

            itemView.setOnClickListener {
                onItemClick(item.type)
            }
        }
    }
}