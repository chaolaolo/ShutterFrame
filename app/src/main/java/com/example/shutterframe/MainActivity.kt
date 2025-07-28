package com.example.shutterframe

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.shutterframe.databinding.ActivityMainBinding
import com.example.shutterframe.fragments.ImagesFragment
import com.example.shutterframe.fragments.TrashFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        if (savedInstanceState == null){
            loadFragment(ImagesFragment())
        }

        binding.bottomNavigation.setOnItemSelectedListener { item->
            when(item.itemId){
                R.id.navigation_images -> {
                    loadFragment(ImagesFragment())
                    true
                }
                R.id.navigation_trash -> {
                    loadFragment(TrashFragment())
                    true
                }
                else -> false
            }
        }

        binding.fabTakePhoto.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadFragment(fragment: Fragment){
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}