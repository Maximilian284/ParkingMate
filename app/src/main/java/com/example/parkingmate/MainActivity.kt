package com.example.parkingmate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Trova il contenitore dei fragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        // 2. Prendi il controller della navigazione
        val navController = navHostFragment.navController

        // 3. Trova la barra in basso
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 4. Collega tutto
        NavigationUI.setupWithNavController(bottomNav, navController)
    }
}