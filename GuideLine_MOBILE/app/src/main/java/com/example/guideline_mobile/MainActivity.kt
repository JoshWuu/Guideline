package com.example.guideline_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.guideline_mobile.ui.theme.GuideLine_MOBILETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuideLine_MOBILETheme {
                // Make sure to call your main composable here to display it
                TheMain()
            }
        }
    }
}

@Composable
fun TheMain() {
    // Assume R.string.logo_desc is defined in your strings.xml
    val logoDesc: String = stringResource(id = R.string.logo_desc)

    // State for controlling the info modal visibility
    var showInfoModal by remember { mutableStateOf(false) }

    // Define colors for the gradient
    val vibrantPurple = Color(0xFF9C27B0) // Your specified vibrant purple
    val veryDarkEndColor = Color(0xFF1A001F) // A very dark color for the end of the gradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        vibrantPurple,    // Starts with your vibrant purple at the top
                        veryDarkEndColor  // Transitions to a very dark color at the bottom
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
            painter = painterResource(id = R.drawable.guidelinelogo), // Assume R.drawable.guidelinelogo exists
            contentDescription = logoDesc,
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .offset(y = (-50).dp)
        )

        Text(
            text = "GuideLine",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White, // White text should contrast well with the new gradient
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 30.dp)
        )

        Button(
            onClick = { /*TODO: Implement scan action*/ },
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 80.dp)
        ) {
            Text(
                text = "Scan Schematic",
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }

        // Info Modal Dialog
        if (showInfoModal) {
            InfoModal(onDismiss = { showInfoModal = false })
        }
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