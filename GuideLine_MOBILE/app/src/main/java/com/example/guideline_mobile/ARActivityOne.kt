package com.example.guideline_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ARActivityOne(val qrLink:String) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        linkReader(qrLink)
    }

}

private fun linkReader(qrLink:String) {

    var urlConnection:HttpURLConnection? = null
    return try {
        val url = URL(qrLink);
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

            println(response.toString())
        } else {
            println("GET Request Failed :(")
        }


    } catch (e: IOException){
        println(("IOException ${e.message}"))
        e.printStackTrace()
    }
    finally{
        urlConnection?.disconnect()
    }

}
