package com.example.guideline_mobile

import android.graphics.fonts.FontStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guideline_mobile.ui.theme.GuideLine_MOBILETheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            GuideLine_MOBILETheme {

            }
        }
    }
}

@Composable
fun TheMain(){
    val logoDesc: String = stringResource(id = R.string.logo_desc)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.hsl(310f, 0.059f, 0.076f)) //set to HEX Later or something
    ){

        Image(
            painter = painterResource(id = R.drawable.guidelinelogo),
            contentDescription = logoDesc,
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .offset(y = (-50).dp)

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
            onClick = {/*todo*/},
            shape = RoundedCornerShape(25),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 80.dp)


        ){
            Text(
                text = "Scan Schematic",
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