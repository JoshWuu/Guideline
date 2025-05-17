package com.example.guideline_mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.CaptureActivity

class ScanActivity : ComponentActivity(){
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null){
            val intent = Intent()
            intent.putExtra("SCAN_RESULT",result.contents)
            setResult(Activity.RESULT_OK, intent)
            finish()
        } else{
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        val options = ScanOptions().apply{
            setPrompt("Scan A QR Code")
            setBeepEnabled(true)
            setOrientationLocked(true)
            setCaptureActivity(CaptureActivity::class.java)

        }
        barcodeLauncher.launch(options)
    }
}