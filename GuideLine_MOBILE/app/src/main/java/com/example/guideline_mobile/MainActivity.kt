package com.example.guideline_mobile


import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.guideline_mobile.ui.theme.GuideLine_MOBILETheme
// New imports for animation
import androidx.compose.animation.core.*
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat.startActivity


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
fun TheMain() {
    // Assume R.string.logo_desc is defined in your strings.xml
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
            Log.d("result", scannedResult.toString())
            linkReader(scannedResult,context)
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

    // State for controlling the info modal visibility
    var showInfoModal by remember { mutableStateOf(false) }

    // Define colors for the gradient animation
    val vibrantPurple = Color(0xFF1A001F ) // Your specified vibrant purple
    val deepPurple = Color(0xFF6A0080) // A deeper purple for animation
    val veryDarkEndColor = Color(0xFF1A001F) // A very dark color for the end of the gradient
    val darkPurple = Color(0xFF38006B) // Another dark purple shade

    // Create infinite transitions for animating gradient colors
    val infiniteTransition = rememberInfiniteTransition(label = "gradientTransition")

    // First animation - transitions between primary colors
    val primaryColorAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "primaryColor"
    )

    // Second animation - transitions between secondary colors
    val secondaryColorAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondaryColor"
    )

    // Convert float animation values to interpolated colors
    val primaryColorAnimation = lerp(vibrantPurple, deepPurple, primaryColorAnim)
    val secondaryColorAnimation = lerp(veryDarkEndColor, darkPurple, secondaryColorAnim)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primaryColorAnimation,    // Animated top color
                        secondaryColorAnimation   // Animated bottom color
                    )
                )
            )
    ) {
        // Info button in the top right corner
        IconButton(
            onClick = { showInfoModal = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color.White
            )
        }

        Image(
            painter = painterResource(id = R.drawable.threepxtile),
            contentDescription = logoDesc,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)

        )

        Image(
            painter = painterResource(id = R.drawable.guidelinelogo),

            contentDescription = logoDesc,
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)

                .offset(y = (-75).dp)
                .alpha(alpha)

        )

        Text(
            text = "GuideLine",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,

            fontSize = 36.sp, // Increased from default (typically 14.sp) to 36.sp for larger text
            color = Color.White, // White text should contrast well with the new gradient

            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 25.dp)
        )


        // Animated Scan Schematic Button


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
                fontFamily = FontFamily.Monospace)



        }


        AnimatedButton(
            text = "View Web Application",


            onClick = { /*TODO: Implement scan action*/ },


            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 130.dp)
        )
        AnimatedButton(
            text = "Project Submission",
            onClick = { /*TODO: Implement scan action*/ },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 180.dp)
        )

        Text(
            text = "v0.1.0",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp)
                .alpha(0.4f)
        )

        // Info Modal Dialog
        if (showInfoModal) {
            InfoModal(onDismiss = { showInfoModal = false })
        }
    }
}



fun linkReader (qrCode:String?, context: Context): Any {
    var urlConnection:HttpURLConnection? = null
    return try {

        val urlString : String = "https://guideline-jam.vercel.app/$qrCode"


        val url = URL(urlString);
        urlConnection = url.openConnection() as HttpURLConnection
        urlConnection.requestMethod = "GET"
        var responseCode: Int = urlConnection.getResponseCode();
        println("GET Code:: $responseCode")
        if (responseCode == HttpURLConnection.HTTP_OK){
            var input = BufferedReader(InputStreamReader(urlConnection.inputStream))
            var inputLine: String
            var response = StringBuffer()

            while ((`input`.readLine().also { inputLine = it }) != null) {
                response.append(inputLine)
            }
            input.close()

            Log.d("response",response.toString())

            val arPage = Intent(context,ARActivityOne::class.java)
            arPage.putExtra("jsonData",response.toString())
             // context.startActivity(arPage)

        } else {

        }


    } catch (e: IOException){
        println(("IOException ${e.message}"))
        e.printStackTrace()
    }
    finally{
        urlConnection?.disconnect()
    }
}


@Composable
fun AnimatedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Create an interaction source to detect hover state
    val interactionSource = remember { MutableInteractionSource() }
    // Collect hover state
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Create an animated scale based on hover state
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(25),

        colors = ButtonDefaults.buttonColors(
            containerColor = Color.DarkGray
        ),
        modifier = modifier
            .scale(scale) // Apply the animated scale
            .hoverable(interactionSource) // Make it hoverable
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            color = Color.White
        )
    }
}


@Composable
fun InfoModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "About GuideLine",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color(0xFF9C27B0)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "GuideLine is an augmented reality circuit assistant that helps you visualize, build, and troubleshoot electronic circuits.",
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Simply scan your circuit schematic using your camera, and GuideLine will provide interactive AR guidance, component identification, and step-by-step assembly instructions.",
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Perfect for students, hobbyists, and professionals working with electronic circuits.",
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    ),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text("Got it!")
                }
            }
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

@Preview
@Composable
fun InfoModalPreview() {
    GuideLine_MOBILETheme {
        InfoModal(onDismiss = {})
    }
}