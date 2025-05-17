package com.example.guideline_mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guideline_mobile.ui.theme.GuideLine_MOBILETheme
import androidx.compose.ui.text.font.FontWeight


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            GuideLine_MOBILETheme {

                TheMain()

            }
        }
    }
}

@Composable
fun TheMain(){
    val logoDesc: String = stringResource(id = R.string.logo_desc)
    val context = LocalContext.current
    var scannedResult by remember {mutableStateOf<String?>(null)}

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val scanContent = data?.getStringExtra("SCAN_RESULT")
            scannedResult = scanContent
            Toast.makeText(context, "Scanned: $scanContent", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Scan canceled", Toast.LENGTH_SHORT).show()
        }
    }

    var visible by remember { mutableStateOf(false) }

    // Trigger the fade-in when the screen first appears
    LaunchedEffect(Unit) {
        visible = true
    }

    // Alpha value animated from 0f to 1f
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.hsl(310f, 0.059f, 0.076f)) //set to HEX Later or something
    ){

        Image(
            painter = painterResource(id = R.drawable.threepxtile),
            contentDescription = logoDesc,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.2f)

        )

        Image(
            painter = painterResource(id = R.drawable.guidelinelogo),
            contentDescription = logoDesc,
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .offset(y = (-50).dp)
                .alpha(alpha)

        )
        Text(
            text ="GuideLine" ,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,

            modifier = Modifier
                .align(Alignment.Center)
                .offset(y=(30).dp)
        )


        Button(
            onClick = {
                val intent = Intent(context, ScanActivity::class.java)
                scanLauncher.launch(intent)

                //input the next activity here and pass the JSON

            },

            shape = RoundedCornerShape(25),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 80.dp)


        ){
            Text(
                text = "Scan Schematic",
                color = Color.White,
                fontFamily = FontFamily.Monospace

            )
        }



    }

}
@Preview(showBackground = true)
@Composable
fun MainPreview() {
    GuideLine_MOBILETheme {
       TheMain()
    }
}