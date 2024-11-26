package com.example.musicplayer

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private var musicList = mutableListOf<MusicData>()
    private lateinit var originalList: ArrayList<MusicData>
    private lateinit var tvNoSongs: TextView
    private lateinit var musicAdapter: RecyclerViewMusicAdapter
    private var mediaPlayer: MediaPlayer? = null
    private val handler = android.os.Handler()


    private companion object {
        const val AUDIO_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        recyclerView = findViewById(R.id.recyclerView)
        searchView = findViewById(R.id.musicSearchView)
        tvNoSongs = findViewById(R.id.tvNoSongs)

        // Initialize RecyclerView and Adapter
        musicAdapter = RecyclerViewMusicAdapter(musicList) { music, action ->
            when (action) {
                "share" -> shareMusic(music)
                "delete" -> deleteMusic(music)
                "play" -> showMusicPlayerDialog(music)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = musicAdapter

        // Request Permissions and Fetch Files
        requestAudioPermission()

        //Setup SearchView
        searchView.setOnClickListener {
            searchView.isIconified = false // Expand the SearchView to let the user type
        }

        // Handle SearchView interactions
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d("SEARCH_QUERY", "Query: $newText")

                // Use a safe call and handle empty or null queries to show the full list
                val filteredList = if (newText.isNullOrEmpty()) {
                    originalList // Display the full list if the search text is empty or null
                } else {
                    musicList.filter { music ->
                        music.title.contains(newText, ignoreCase = true)
                    }
                }

                // Update adapter with the filtered or full list
                musicAdapter.updateList(filteredList)

                // Toggle visibility based on the filtered list
                val isListEmpty = filteredList.isEmpty()
                tvNoSongs.visibility = if (isListEmpty) View.VISIBLE else View.GONE
                recyclerView.visibility = if (isListEmpty) View.GONE else View.VISIBLE

                return true
            }
        })


    }

    private fun requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO),
                    AUDIO_PERMISSION_REQUEST_CODE
                )
            } else {
                fetchMusicFiles()
            }
        } else { // Below Android 13
            fetchMusicFiles()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchMusicFiles()
            } else {
                Log.e("PERMISSION", "Permission denied.")
            }
        }
    }

    //Fun For Get Audio Files
    private fun fetchMusicFiles() {
        musicList.clear() // Clear list to avoid duplicates

        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val title =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val path =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))

                musicList.add(MusicData(title, path))
            } while (cursor.moveToNext())
            cursor.close()
        }

        // Initialize originalList as a copy of musicList
        originalList = ArrayList(musicList)

        Log.d("FETCH_MUSIC", "Fetched ${musicList.size} music files.")
        musicAdapter.notifyDataSetChanged()
    }

    //Fun For Share Song
    private fun shareMusic(music: MusicData) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(music.path))
        }
        startActivity(Intent.createChooser(shareIntent, "Share Music"))
    }

    //Fun For Delete Song
    private fun deleteMusic(musicData: MusicData) {
        // Remove from list and update RecyclerView
        removeSongFromList(musicData)

        // Optionally, delete the file from the device's storage
        val filePath = musicData.path
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
            Log.d("DELETE_MUSIC", "File successfully deleted from device storage.")
        }
    }

    private fun removeSongFromList(musicData: MusicData) {
        val positionToDelete = musicList.indexOf(musicData)
        if (positionToDelete != -1) {
            musicList.removeAt(positionToDelete)
            musicAdapter.notifyItemRemoved(positionToDelete)
        }
    }


    //DialogBox Setup
    private fun showMusicPlayerDialog(music: MusicData) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.music_player_dialog)

        // Initialize views from the dialog layout
        val tvMusicTitle: TextView = dialog.findViewById(R.id.tvMusicTitle)
        val btnPlay: ImageButton = dialog.findViewById(R.id.btnPlay)
        val btnPrev: ImageButton = dialog.findViewById(R.id.btnPrev)
        val btnNext: ImageButton = dialog.findViewById(R.id.btnNext)
        val seekBar: SeekBar = dialog.findViewById(R.id.seekBar)
        val tvStartTime: TextView = dialog.findViewById(R.id.tvStartTime)
        val tvEndTime: TextView = dialog.findViewById(R.id.tvEndTime)


        var currentSongIndex = musicList.indexOf(music) // Get the index of the current song

        // Set up the music title
        tvMusicTitle.text = music.title

        // Get the dialog's window
        val window = dialog.window

        // Set the dialog position (e.g., bottom of the screen with custom height)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, // Width
            ViewGroup.LayoutParams.WRAP_CONTENT  // Height
        )

        // Optionally, set custom margins to move the dialog up or down
        val params = window?.attributes
        params?.y = 700  // Adjust this to move the dialog vertically (positive value moves down)
        window?.attributes = params

        // Start playing the current music
        playMusic(music, seekBar, tvStartTime, tvEndTime)

        // Set up MediaPlayer to play music
        mediaPlayer = MediaPlayer().apply {
            setDataSource(music.path)
            prepare()  // Prepare the media player
            start()    // Start playing music immediately
        }

        // Set total duration to the SeekBar and end time text view
        seekBar.max = mediaPlayer?.duration ?: 0
        tvEndTime.text = formatTime(mediaPlayer?.duration ?: 0)


        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // If the change is from the user, update the MediaPlayer position
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optional: Pause the music while the user is dragging the SeekBar
                mediaPlayer?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Resume the music after the user releases the SeekBar
                mediaPlayer?.start()
            }
        })

        // Update the SeekBar and time labels as the music plays
        val updateSeekBarRunnable = object : Runnable {
            override fun run() {
                val currentPosition = mediaPlayer?.currentPosition ?: 0
                seekBar.progress = currentPosition
                tvStartTime.text = formatTime(currentPosition)
                handler.postDelayed(this, 1000)  // Update every second
            }
        }
        handler.postDelayed(updateSeekBarRunnable, 1000)  // Start the update loop

        // Play/Pause button logic
        setPlayPauseIcon(btnPlay)
        btnPlay.setImageResource(R.drawable.pause)  // Initially set to "Pause" as the music is playing
        btnPlay.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                btnPlay.setImageResource(R.drawable.play)  // Set the button to "Play" when paused
            } else {
                mediaPlayer?.start()
                btnPlay.setImageResource(R.drawable.pause)  // Set the button to "Pause" when playing
            }
        }

        // Previous song button logic
        btnPrev.setOnClickListener {
            // Play previous song if there is one
            if (currentSongIndex > 0) {
                currentSongIndex -= 1
                val previousMusic = musicList[currentSongIndex]
                playMusic(previousMusic, seekBar, tvStartTime, tvEndTime) // Call playMusic() method
                tvMusicTitle.text = previousMusic.title
                setPlayPauseIcon(btnPlay)
            }
        }

        // Next song button logic
        btnNext.setOnClickListener {
            // Play next song if there is one
            if (currentSongIndex < musicList.size - 1) {
                currentSongIndex += 1
                val nextMusic = musicList[currentSongIndex]
                playMusic(nextMusic, seekBar, tvStartTime, tvEndTime) // Call playMusic() method
                tvMusicTitle.text = nextMusic.title
                setPlayPauseIcon(btnPlay)
            }
        }

        // Dismiss listener to stop the music when the dialog is dismissed
        dialog.setOnDismissListener {
            mediaPlayer?.stop()  // Stop the media player when the dialog is dismissed
            mediaPlayer?.release()  // Release the media player resources
            mediaPlayer = null
            handler.removeCallbacksAndMessages(null)  // Remove any pending updates
        }

        // Show the dialog
        dialog.show()
    }

    // Format time in mm:ss format
    private fun formatTime(ms: Int): String {
        val minutes = (ms / 1000) / 60
        val seconds = (ms / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // playMusic is a separate method defined outside showMusicPlayerDialog
    private fun playMusic(
        music: MusicData,
        seekBar: SeekBar,
        tvStartTime: TextView,
        tvEndTime: TextView
    ) {
        mediaPlayer?.apply {
            reset()  // Reset the media player to release previous data
            setDataSource(music.path)  // Set the new music path
            prepare()  // Prepare the media player
            start()    // Start the music

            // Update the SeekBar and the end time
            seekBar.max = duration
            tvEndTime.text = formatTime(duration)
            tvStartTime.text = getString(R.string.start_time) // Reset the start time
        }
    }

    //Fun For Set Play/Pause Button When Song Change
    private fun setPlayPauseIcon(btnPlay: ImageButton) {
        if (mediaPlayer?.isPlaying == true) {
            btnPlay.setImageResource(R.drawable.pause)  // Set to Pause icon when playing
        } else {
            btnPlay.setImageResource(R.drawable.play)  // Set to Play icon when paused
        }
    }
}












