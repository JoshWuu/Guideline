package com.example.ar

import android.Manifest
import android.view.WindowManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class MainActivity : ComponentActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var cameraView: JavaCameraView

    // Constants for normalized breadboard representation
    private val NORMALIZED_WIDTH = 600  // Width for warped perspective
    private val NORMALIZED_HEIGHT = 400 // Height for warped perspective

    // Matrix to store the perspective transformed result
    private var warpedBreadboard: Mat? = null

    // Use the new permission request API
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Log.e(TAG, "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraView = findViewById(R.id.java_camera_view)
        cameraView.setCvCameraViewListener(this)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed")
            return
        }
        cameraView.setCameraPermissionGranted()
        cameraView.enableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        // Initialize warped matrix
        warpedBreadboard = Mat()
    }

    override fun onCameraViewStopped() {
        // Release resources
        warpedBreadboard?.release()
        warpedBreadboard = null
    }

    /**
     * Sort points in order: top-left, top-right, bottom-right, bottom-left
     */
    private fun sortPoints(points: Array<Point>): Array<Point> {
        // First find the center point
        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()

        // Sort points based on their position relative to center
        val sorted = Array(4) { Point() }

        // For each corner, determine which quadrant it's in relative to center
        for (point in points) {
            val index = when {
                point.x < centerX && point.y < centerY -> 0  // top-left
                point.x > centerX && point.y < centerY -> 1  // top-right
                point.x > centerX && point.y > centerY -> 2  // bottom-right
                else -> 3  // bottom-left
            }
            sorted[index] = point
        }

        return sorted
    }

    /**
     * Apply perspective transform to get a normalized top-down view of the breadboard
     */
    private fun applyPerspectiveTransform(source: Mat, corners: Array<Point>): Mat {
        val sortedCorners = sortPoints(corners)

        // Define the 4 corner points of the destination image
        val dstPoints = arrayOf(
            Point(0.0, 0.0),                          // top-left
            Point(NORMALIZED_WIDTH - 1.0, 0.0),       // top-right
            Point(NORMALIZED_WIDTH - 1.0, NORMALIZED_HEIGHT - 1.0), // bottom-right
            Point(0.0, NORMALIZED_HEIGHT - 1.0)       // bottom-left
        )

        // Convert to MatOfPoint2f format
        val srcMat = MatOfPoint2f(*sortedCorners)
        val dstMat = MatOfPoint2f(*dstPoints)

        // Calculate the perspective transform matrix
        val transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)

        // Apply the perspective transformation
        val warped = Mat()
        Imgproc.warpPerspective(
            source,
            warped,
            transformMatrix,
            Size(NORMALIZED_WIDTH.toDouble(), NORMALIZED_HEIGHT.toDouble())
        )

        // Clean up
        transformMatrix.release()
        srcMat.release()
        dstMat.release()

        return warped
    }

    /**
     * Detect horizontal and vertical lines using HoughLines to create a grid
     * representation of the breadboard
     */
    private fun detectBreadboardGrid(warped: Mat): Pair<List<Line>, List<Line>> {
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply adaptive threshold
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            11,
            2.0
        )

        // Detect edges
        val edges = Mat()
        Imgproc.Canny(binary, edges, 50.0, 150.0)

        // Apply HoughLines to detect lines
        val lines = Mat()
        Imgproc.HoughLines(edges, lines, 1.0, Math.PI / 180, 100)

        // Separate horizontal and vertical lines
        val horizontalLines = mutableListOf<Line>()
        val verticalLines = mutableListOf<Line>()

        for (i in 0 until lines.rows()) {
            val data = lines.get(i, 0)
            val rho = data[0]
            val theta = data[1]

            // Classify lines as horizontal or vertical based on theta
            if (theta < Math.PI / 4 || theta > 3 * Math.PI / 4) {
                // Vertical line
                val x = rho / Math.cos(theta)
                verticalLines.add(Line(x.toInt(), 0, x.toInt(), NORMALIZED_HEIGHT))
            } else {
                // Horizontal line
                val y = rho / Math.sin(theta)
                horizontalLines.add(Line(0, y.toInt(), NORMALIZED_WIDTH, y.toInt()))
            }
        }

        // Clean up resources
        gray.release()
        binary.release()
        edges.release()
        lines.release()

        return Pair(horizontalLines, verticalLines)
    }

    /**
     * Line data class to store line endpoints
     */
    data class Line(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    /**
     * Enhanced algorithm to detect breadboards with visualization of detected features
     */
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgba = inputFrame.rgba()

        // 1. Convert to HSV
        val hsv = Mat()
        Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGB2HSV)

        // 2. Threshold for white color (low saturation, high value)
        val lowerWhite = Scalar(0.0, 0.0, 200.0)
        val upperWhite = Scalar(180.0, 50.0, 255.0)
        val mask = Mat()
        Core.inRange(hsv, lowerWhite, upperWhite, mask)

        // 3. Blur and threshold to reduce noise
        val blurred = Mat()
        Imgproc.GaussianBlur(mask, blurred, Size(7.0, 7.0), 1.5)
        Imgproc.threshold(blurred, mask, 128.0, 255.0, Imgproc.THRESH_BINARY)

        // Apply morphological closing to connect nearby regions
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)

        // Erode to remove small noise
        val erosionKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.erode(mask, mask, erosionKernel, Point(-1.0, -1.0), 3)

        // 4. Find contours on mask
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // 5. Find largest contour by area (assumed breadboard)
        var maxContour: MatOfPoint? = null
        var maxArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > maxArea) {
                maxArea = area
                maxContour = contour
            }
        }

        // 6. Process the largest contour (assumed breadboard)
        maxContour?.let {
            val contour2f = MatOfPoint2f(*it.toArray())

            // Approximate polygon with epsilon = 2% of contour perimeter
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

            val points = approx.toArray()

            if (points.size == 4) {
                // Draw green polygon lines between 4 corners
                for (i in points.indices) {
                    Imgproc.line(rgba, points[i], points[(i + 1) % 4], Scalar(0.0, 255.0, 0.0), 3)
                }

                // Add text label for breadboard
                Imgproc.putText(
                    rgba,
                    "Breadboard",
                    Point(points[0].x, points[0].y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    Scalar(0.0, 255.0, 0.0),
                    2
                )

                // Apply perspective transform to get normalized top-down view
                warpedBreadboard?.release()
                warpedBreadboard = applyPerspectiveTransform(rgba, points)

                // Create a small preview of the warped image in the corner
                if (warpedBreadboard != null) {
                    val preview = Mat()
                    val previewSize = Size(rgba.width() * 0.3, rgba.height() * 0.3)
                    Imgproc.resize(warpedBreadboard!!, preview, previewSize)

                    // Create an ROI in the top-right corner of the main image
                    val roi = rgba.submat(
                        0,
                        preview.height().toInt(),
                        rgba.width() - preview.width().toInt(),
                        rgba.width()
                    )

                    // Copy the preview into the ROI
                    preview.copyTo(roi)

                    // Draw a border around the preview
                    Imgproc.rectangle(
                        rgba,
                        Point(rgba.width().toDouble() - preview.width().toDouble(), 0.0),
                        Point(rgba.width().toDouble(), preview.height().toDouble()),
                        Scalar(255.0, 255.0, 255.0),
                        2
                    )

                    // Try to detect grid lines
                    val (horizontalLines, verticalLines) = detectBreadboardGrid(warpedBreadboard!!)

                    // Display grid line count
                    Imgproc.putText(
                        rgba,
                        "H-lines: ${horizontalLines.size}, V-lines: ${verticalLines.size}",
                        Point(10.0, rgba.height() - 20.0),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        Scalar(255.0, 255.0, 255.0),
                        2
                    )

                    // Clean up
                    preview.release()
                }
            } else {
                // Fallback: draw rotated rectangle in red if not 4 points
                val rotatedRect = Imgproc.minAreaRect(contour2f)
                val boxPoints = Array(4) { Point() }
                rotatedRect.points(boxPoints)
                for (i in boxPoints.indices) {
                    Imgproc.line(rgba, boxPoints[i], boxPoints[(i + 1) % 4], Scalar(0.0, 0.0, 255.0), 3)
                }
            }

            // Clean up
            contour2f.release()
            approx.release()
        }

        // Clean up resources
        hsv.release()
        mask.release()
        blurred.release()
        hierarchy.release()
        contours.forEach { it.release() }

        // Return image with overlayed polygon or rectangle
        return rgba
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraView.disableView()
        warpedBreadboard?.release()
    }

    companion object {
        private const val TAG = "BreadboardAR"
    }
}