package com.example.taller2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.taller2.databinding.ActivityMapaBinding

class MapaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}