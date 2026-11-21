package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.PharmacyApp
import com.example.ui.PharmacyViewModel
import com.example.ui.PharmacyViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val viewModel = ViewModelProvider(
      this, 
      PharmacyViewModelFactory(application)
    )[PharmacyViewModel::class.java]
    
    setContent {
      MyApplicationTheme {
        PharmacyApp(viewModel)
      }
    }
  }
}
