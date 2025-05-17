package com.example.guideline_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ARActivityOne() : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        val json = intent.getStringExtra("jsonData")
    }

}
