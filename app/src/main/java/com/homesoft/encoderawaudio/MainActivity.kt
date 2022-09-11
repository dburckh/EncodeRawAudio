package com.homesoft.encoderawaudio

import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var record : ImageButton
    private lateinit var play : ImageButton
    private lateinit var error : TextView
    private val requestRecordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            //Do nothing, picked up in onResume
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        record = findViewById(R.id.record)
        play = findViewById(R.id.play)
        error = findViewById(R.id.error)

        val model: EncodeViewModel by viewModels()
        val mp4File = model.mp4File

        record.setOnClickListener{
            when(model.getRecordState()) {
                EncodeViewModel.State.STOPPED, EncodeViewModel.State.ERROR -> model.startRecord()
                else -> model.stopRecord()
            }
        }
        play.setOnClickListener{
            when(model.getPlayState()) {
                EncodeViewModel.State.STOPPED, EncodeViewModel.State.ERROR -> model.startPlay()
                else -> model.stopPlay()
            }
        }
        model.recordStateData.observe(this) { state ->
            val resId : Int
            when (state) {
                EncodeViewModel.State.STOPPED -> {
                    resId = R.drawable.ic_record_start_button
                    if (mp4File.exists()) {
                        enablePlay()
                    }
                }
                EncodeViewModel.State.ERROR -> {
                    resId = R.drawable.ic_record_start_button
                    error.text = "${model.recordErrorCode}"
                    error.visibility = View.VISIBLE
                }
                EncodeViewModel.State.STARTING -> {
                    error.visibility = View.INVISIBLE
                    resId = R.drawable.ic_record_stop_button
                }
                else -> resId = R.drawable.ic_record_stop_button
            }
            record.setImageResource(resId)
        }
        model.playStateData.observe(this) { state->
            val resId = when (state) {
                EncodeViewModel.State.STOPPED, EncodeViewModel.State.ERROR -> R.drawable.ic_play_start_button
                else -> R.drawable.ic_play_stop_button
            }
            play.setImageResource(resId)
        }
        requestRecordAudioPermissionLauncher.launch(RECORD_AUDIO)
        if (mp4File.exists()) {
            enablePlay()
        }
    }

    private fun enablePlay() {
        play.visibility = View.VISIBLE
    }
    override fun onResume() {
        super.onResume()
        record.isEnabled =  ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}