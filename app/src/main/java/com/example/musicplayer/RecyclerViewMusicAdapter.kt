package com.example.musicplayer


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecyclerViewMusicAdapter(
    private var musicList: MutableList<MusicData>, // Mutable list that will be displayed in RecyclerView
    private val actionCallback: (MusicData, String) -> Unit // Callback for share, delete, etc.
) : RecyclerView.Adapter<RecyclerViewMusicAdapter.ViewHolder>() {

    private val originalList = musicList.toMutableList() // Store the original list for restoration

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val img: ImageView = itemView.findViewById(R.id.ivMore)
        val llMain: LinearLayout = itemView.findViewById(R.id.llMain)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.music_item_raw, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return musicList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val music = musicList[position]
        holder.title.text = music.title

        holder.img.setOnClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, it)
            popupMenu.menuInflater.inflate(R.menu.music_options, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menuShare -> actionCallback(music, "share")  // Share action
                    R.id.menuDelete -> actionCallback(music, "delete")  // Delete action
                }
                true
            }
            popupMenu.show()
        }

        // Trigger play action when the item is clicked
        holder.llMain.setOnClickListener {
            actionCallback(music, "play")
        }
    }

    // Method to filter the list based on the query
    fun updateList(newList: List<MusicData>?) {
        musicList.apply {
            clear()
            addAll(newList ?: originalList) // Use originalList if newList is null or empty
        }
        notifyDataSetChanged()
    }


}
