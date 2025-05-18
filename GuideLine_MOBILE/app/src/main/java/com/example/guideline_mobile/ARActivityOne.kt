package com.example.guideline_mobile

import kotlinx.serialization.json.Json

import android.Manifest
import android.view.WindowManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

typealias GridPoint = Pair<Int, Int>

@Serializable
data class ComponentPlacement(
    val ref: String,
    val positions: List<GridPoint>
)

class ARActivityOne : ComponentActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var cameraView: JavaCameraView
    private lateinit var placements: List<ComponentPlacement>

    private var inverseTransformMatrix: Mat? = null
    private var pathPoints = listOf<Pair<Int, Int>>()
    private var pathLineColor: Scalar = Scalar(0.0, 165.0, 255.0)

    // Constants for normalized breadboard representation
    private val NORMALIZED_WIDTH = 600  // Width for warped perspective
    private val NORMALIZED_HEIGHT = 300 // Height for warped perspective

    // Constants for breadboard grid
    private val EXPECTED_ROWS = 10    // Standard half-size breadboard has ~30 rows
    private val EXPECTED_COLUMNS = 30 // Standard half-size breadboard has ~10 columns per side

    // Grid line tracking for stability
    private val rowPositions = mutableListOf<Int>()
    private val colPositions = mutableListOf<Int>()
    private val HISTORY_SIZE = 5 // Number of frames to use for stabilization

    private var currentIntersectionMatrix: Array<Array<Point?>> = Array(0) { Array(0) { null } }
    private var highlightedPoints = mutableListOf<Pair<Int, Int>>() // Store row,col pairs to highlight


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


        val jsonData = intent.getStringExtra("jsonData")
        if (jsonData.isNullOrEmpty()) {
            Log.e("ARActivityOne", "No JSON data received in intent extras")
            Toast.makeText(this, "Failed to load component data", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d("ARActivityOne", "Raw JSON data received (${jsonData.length} chars)")

        // 2) Deserialize the JSON into Kotlin objects
        placements = try {
            Json.decodeFromString(jsonData)
        } catch (e: SerializationException) {
            Log.e("ARActivityOne", "Error parsing JSON: ${e.localizedMessage}", e)
            emptyList()
        }
        Log.d("ARActivityOne", "Parsed placements count: ${placements.size}")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize UI components
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

        // Store the inverse transformation matrix for mapping points back
        inverseTransformMatrix?.release()
        inverseTransformMatrix = Imgproc.getPerspectiveTransform(dstMat, srcMat)

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
     * Transforms a point from the warped view back to the original view
     */
    private fun mapPointFromWarpedToOriginal(warpedPoint: Point): Point {
        if (inverseTransformMatrix == null) {
            Log.e("BreadboardDetector", "Inverse transform matrix is null")
            return warpedPoint
        }

        // Create a MatOfPoint2f with a single point
        val warpedPointMat = MatOfPoint2f(warpedPoint)
        val originalPointMat = MatOfPoint2f()

        // Apply the inverse perspective transform
        Core.perspectiveTransform(warpedPointMat, originalPointMat, inverseTransformMatrix)

        // Extract the transformed point
        val originalPoint = originalPointMat.toArray()[0]

        // Clean up
        warpedPointMat.release()
        originalPointMat.release()

        return originalPoint
    }

    /**
     * Draws dots in the original view based on highlighted points in the warped view
     */
    private fun drawHighlightedPointsInOriginalView(rgba: Mat) {
        if (inverseTransformMatrix == null || currentIntersectionMatrix.isEmpty()) {
            return
        }

        highlightedPoints.forEach { (row, col) ->
            try {
                // Check if the point is within the grid
                if (row >= 0 && row < currentIntersectionMatrix.size &&
                    col >= 0 && col < currentIntersectionMatrix[0].size) {

                    // Get the intersection point in warped view
                    val warpedPoint = currentIntersectionMatrix[row][col]

                    warpedPoint?.let {
                        // Map the point from warped view to original view
                        val originalPoint = mapPointFromWarpedToOriginal(it)

                        // Draw a circle at the mapped point in the original view
                        Imgproc.circle(
                            rgba,
                            originalPoint,
                            8, // Larger radius for better visibility
                            Scalar(0.0, 255.0, 255.0), // Cyan
                            -1 // Filled circle
                        )

                        // Add a border for better visibility
                        Imgproc.circle(
                            rgba,
                            originalPoint,
                            14,
                            Scalar(0.0, 0.0, 0.0), // Black border
                            2
                        )

                        // Add label with coordinates
                        val label = "($row,$col)"
                        val textSize = Imgproc.getTextSize(
                            label,
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            0.5,
                            1,
                            null
                        )

                        // Create a background for the text
                        Imgproc.rectangle(
                            rgba,
                            Point(originalPoint.x - 2, originalPoint.y - textSize.height - 4),
                            Point(originalPoint.x + textSize.width + 2, originalPoint.y),
                            Scalar(0.0, 0.0, 0.0),
                            -1 // Filled rectangle
                        )

                        // Draw the text
                        Imgproc.putText(
                            rgba,
                            label,
                            Point(originalPoint.x, originalPoint.y - 4),
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            0.5,
                            Scalar(255.0, 255.0, 255.0), // White text
                            1
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("BreadboardDetector", "Error drawing point in original view: ${e.message}")
            }
        }
    }

    /**
     * Line data class to store line endpoints
     */
    data class Line(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    /**
     * Data class to store hole information
     */
    data class BreadboardHole(val x: Int, val y: Int, val radius: Int, val isOccupied: Boolean)

    /**
     * Robust grid detection using histogram analysis, which is more consistent
     * than Hough lines for detecting breadboard rows and columns
     */
    private fun detectBreadboardGridRobust(warped: Mat): Pair<List<Line>, List<Line>> {
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY)

        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 2.0)

        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blur,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            11,
            11.0
        )

        // Morphological operations
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)

        // Create horizontal and vertical projections (histograms)
        val horizontalProjection = IntArray(binary.height()) { 0 }
        val verticalProjection = IntArray(binary.width()) { 0 }

        // Calculate projections - count white pixels in each row/column
        for (y in 0 until binary.height()) {
            for (x in 0 until binary.width()) {
                if (binary.get(y, x)[0] > 0) {
                    horizontalProjection[y]++
                    verticalProjection[x]++
                }
            }
        }

        // Apply Gaussian smoothing to the projections to reduce noise
        val smoothedHorizontal = smoothArray(horizontalProjection)
        val smoothedVertical = smoothArray(verticalProjection)

        // Detect peaks in the projections
        val horizontalPeaks = detectPeaks(smoothedHorizontal, binary.height())
        val verticalPeaks = detectPeaks(smoothedVertical, binary.width())

        // Enforce consistent number of rows/columns if we have a good estimate
        val horizontalLines = enforceConsistentRowSpacing(horizontalPeaks, binary.height())
        val verticalLines = enforceConsistentColumnSpacing(verticalPeaks, binary.width())

        // Convert peak positions to Line objects
        val hLines = horizontalLines.map { y ->
            Line(0, y, NORMALIZED_WIDTH, y)
        }
        val vLines = verticalLines.map { x ->
            Line(x, 0, x, NORMALIZED_HEIGHT)
        }

        // Clean up
        gray.release()
        binary.release()

        return Pair(hLines, vLines)
    }

    /**
     * Detect breadboard holes using HoughCircles
     */
    private fun detectBreadboardHoles(warped: Mat): List<BreadboardHole> {
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY)

        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 2.0)

        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blur,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            11,
            9.0
        )

        // Morphological operations
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)

        // Use HoughCircles to detect circles
        val circles = Mat()
        Imgproc.HoughCircles(
            blur,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0,
            5.0,
            150.0,
            50.0,
            2,
            25
        )

        // Process detected circles
        val holes = mutableListOf<BreadboardHole>()
        if (!circles.empty()) {
            for (i in 0 until circles.cols()) {
                val data = circles.get(0, i)
                val center = Point(data[0], data[1])
                val radius = data[2].toInt()

                // Determine if hole is occupied (darker center)
                val centerX = center.x.toInt().coerceIn(0, warped.width() - 1)
                val centerY = center.y.toInt().coerceIn(0, warped.height() - 1)
                val pixelValue = gray.get(centerY, centerX)[0]

                // A hole is considered occupied if its center is darker than a threshold
                // You may need to adjust this threshold based on lighting conditions
                val isOccupied = pixelValue < 100.0

                holes.add(BreadboardHole(
                    center.x.toInt(),
                    center.y.toInt(),
                    radius,
                    isOccupied
                ))
            }
        }

        // Clean up
        gray.release()
        blur.release()
        circles.release()

        return holes
    }

    /**
     * Smooth an array using a Gaussian-like kernel to reduce noise
     */
    private fun smoothArray(array: IntArray): DoubleArray {
        val kernelSize = 9
        val sigma = 2.0
        val kernel = DoubleArray(kernelSize) { i ->
            val x = i - kernelSize/2
            Math.exp(-(x*x)/(2*sigma*sigma))
        }
        val sum = kernel.sum()
        return DoubleArray(array.size) { i ->
            var acc = 0.0
            for (k in 0 until kernelSize) {
                val idx = i + k - kernelSize/2
                if (idx in array.indices) acc += array[idx] * kernel[k]
            }
            acc / sum
        }
    }

    /**
     * Detect peaks in a projection by looking for local maxima
     */
    private fun detectPeaks(projection: DoubleArray, maxSize: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        val minPeakDistance = maxSize / 40  // Minimum distance between peaks
        val minPeakHeight = projection.average() * 0.8  // Threshold for peak detection

        for (i in 1 until projection.size - 1) {
            if (projection[i] > minPeakHeight &&
                projection[i] > projection[i - 1] &&
                projection[i] > projection[i + 1]) {

                // Check if this peak is far enough from previously detected peaks
                val farEnough = peaks.all { Math.abs(it - i) > minPeakDistance }
                if (farEnough) {
                    peaks.add(i)
                }
            }
        }

        return peaks
    }

    /**
     * Ensure consistent row spacing based on expected number of rows
     */
    private fun enforceConsistentRowSpacing(detectedRows: List<Int>, height: Int): List<Int> {
        // If we have too few rows, try to estimate the spacing and add missing rows
        if (detectedRows.size < EXPECTED_ROWS / 2 || detectedRows.isEmpty()) {
            // If we have no detected rows, estimate rows with even spacing
            val estimatedSpacing = height / EXPECTED_ROWS
            return List(EXPECTED_ROWS) { i -> i * estimatedSpacing }
        }

        // Sort rows by position
        val sortedRows = detectedRows.sorted()

        // Calculate average spacing between rows
        var totalSpacing = 0
        for (i in 1 until sortedRows.size) {
            totalSpacing += sortedRows[i] - sortedRows[i - 1]
        }
        val avgSpacing = totalSpacing / (sortedRows.size - 1).coerceAtLeast(1)

        // Return the detected rows as they are sufficient
        return sortedRows
    }

    /**
     * Ensure consistent column spacing based on expected number of columns
     */
    private fun enforceConsistentColumnSpacing(detectedColumns: List<Int>, width: Int): List<Int> {
        // If we have too few columns, try to estimate the spacing and add missing columns
        if (detectedColumns.size < EXPECTED_COLUMNS / 2 || detectedColumns.isEmpty()) {
            // If we have no detected columns, estimate columns with even spacing
            val estimatedSpacing = width / EXPECTED_COLUMNS
            return List(EXPECTED_COLUMNS) { i -> i * estimatedSpacing }
        }

        // Sort columns by position
        val sortedColumns = detectedColumns.sorted()

        // Calculate average spacing between columns
        var totalSpacing = 0
        for (i in 1 until sortedColumns.size) {
            totalSpacing += sortedColumns[i] - sortedColumns[i - 1]
        }
        val avgSpacing = totalSpacing / (sortedColumns.size - 1).coerceAtLeast(1)

        // Return the detected columns as they are sufficient
        return sortedColumns
    }

    private fun highlightGridIntersection(
        warped: Mat,
        intersectionMatrix: Array<Array<Point?>>,
        rowIndex: Int,
        colIndex: Int,
        color: Scalar = Scalar(0.0, 0.0, 255.0), // Default: Red
        radius: Int = 5
    ): Boolean {
        try {
            // Validate indices
            if (rowIndex < 0 || rowIndex >= intersectionMatrix.size ||
                colIndex < 0 || colIndex >= intersectionMatrix[0].size) {
                Log.w("BreadboardDetector", "Invalid grid indices: $rowIndex, $colIndex")
                return false
            }

            // Get the intersection point
            val intersection = intersectionMatrix[rowIndex][colIndex]

            // Check if intersection exists
            if (intersection == null) {
                Log.w("BreadboardDetector", "No intersection at: $rowIndex, $colIndex")
                return false
            }

            // Draw a prominent marker at the intersection
            // Outer circle (border)
            Imgproc.circle(
                warped,
                intersection,
                radius + 2,
                Scalar(0.0, 0.0, 0.0), // Black border
                2
            )

            // Inner circle (fill)
            Imgproc.circle(
                warped,
                intersection,
                radius,
                color,
                -1 // Filled circle
            )

            // Add pulsing effect (optional - draws concentric circles)
            Imgproc.circle(
                warped,
                intersection,
                radius + 4,
                color,
                1
            )

            // Add label with coordinates
            val label = "($rowIndex,$colIndex)"

            // Create a background box for better text visibility
            val textSize = Imgproc.getTextSize(
                label,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                1,
                null
            )

            Imgproc.rectangle(
                warped,
                Point(intersection.x - 2, intersection.y - textSize.height - 4),
                Point(intersection.x + textSize.width + 2, intersection.y),
                Scalar(0.0, 0.0, 0.0),
                -1 // Filled rectangle
            )

            // Draw the text
            Imgproc.putText(
                warped,
                label,
                Point(intersection.x, intersection.y - 4),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                Scalar(255.0, 255.0, 255.0), // White text
                1
            )

            return true
        } catch (e: Exception) {
            Log.e("BreadboardDetector", "Error highlighting grid intersection: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    data class GridIntersection(
        val rowIndex: Int,
        val colIndex: Int,
        val point: Point,
        var isOccupied: Boolean = false
    )

    /**
     * Finds the intersection point of two lines
     */
    private fun findIntersection(line1: Line, line2: Line): Point? {
        try {
            // Convert lines to equations: ax + by + c = 0
            val a1 = line1.y2 - line1.y1
            val b1 = line1.x1 - line1.x2
            val c1 = line1.x2 * line1.y1 - line1.x1 * line1.y2

            val a2 = line2.y2 - line2.y1
            val b2 = line2.x1 - line2.x2
            val c2 = line2.x2 * line2.y1 - line2.x1 * line2.y2

            val determinant = a1 * b2 - a2 * b1

            // Lines are parallel if determinant is zero (or very close to zero)
            if (Math.abs(determinant) < 1e-6) {
                return null
            }

            val x = (b1 * c2 - b2 * c1) / determinant
            val y = (a2 * c1 - a1 * c2) / determinant

            return Point(x.toDouble(), y.toDouble())
        } catch (e: Exception) {
            Log.e("BreadboardDetector", "Error finding intersection: ${e.message}")
            return null
        }
    }

    /**
     * Creates a matrix from the intersections of horizontal and vertical lines
     * and displays it on the image with improved visualization
     */
    private fun createIntersectionMatrix(
        warped: Mat,
        horizontalLines: List<Line>,
        verticalLines: List<Line>
    ): Array<Array<Point?>> {
        try {
            // Safety check for empty lines
            if (horizontalLines.isEmpty() || verticalLines.isEmpty()) {
                Log.w("BreadboardDetector", "No grid lines detected")
                return Array(0) { Array<Point?>(0) { null } }
            }

            // Create a matrix to store intersection points
            val matrix = Array(horizontalLines.size) { Array<Point?>(verticalLines.size) { null } }

            // Find all intersections
            for (rowIndex in horizontalLines.indices) {
                val hLine = horizontalLines[rowIndex]
                for (colIndex in verticalLines.indices) {
                    val vLine = verticalLines[colIndex]
                    val intersection = findIntersection(hLine, vLine)

                    // Store intersection point in matrix
                    matrix[rowIndex][colIndex] = intersection

                    // Draw the intersection point on the warped image for visualization
                    intersection?.let {
                        // Make sure coordinates are within image bounds
                        if (it.x >= 0 && it.x < warped.width() && it.y >= 0 && it.y < warped.height()) {
                            // Calculate dynamic circle color based on position
                            // This creates a heat map effect across the grid
                            val hFactor = rowIndex.toDouble() / horizontalLines.size.coerceAtLeast(1)
                            val vFactor = colIndex.toDouble() / verticalLines.size.coerceAtLeast(1)

                            val blue = (255 * (1 - hFactor)).toInt()
                            val green = (255 * vFactor * hFactor).toInt()
                            val red = (255 * vFactor).toInt()

                            // Draw circle at intersection with varying colors
                            Imgproc.circle(
                                warped,
                                Point(it.x, it.y),
                                2,  // smaller radius for less clutter
                                Scalar(blue.toDouble(), green.toDouble(), red.toDouble()),
                                -1  // filled circle
                            )

                            // Only draw labels for some intersections to avoid clutter
                            if ((rowIndex % 5 == 0 || rowIndex == horizontalLines.size - 1) &&
                                (colIndex % 5 == 0 || colIndex == verticalLines.size - 1)) {

                                // Draw a background box for the text to improve readability
                                val textSize = Imgproc.getTextSize(
                                    "$rowIndex,$colIndex",
                                    Imgproc.FONT_HERSHEY_PLAIN,
                                    0.7,
                                    1,
                                    null
                                )

                                Imgproc.rectangle(
                                    warped,
                                    Point(it.x + 2, it.y - textSize.height - 2),
                                    Point(it.x + textSize.width + 6, it.y),
                                    Scalar(0.0, 0.0, 0.0),
                                    -1
                                )

                                Imgproc.putText(
                                    warped,
                                    "$rowIndex,$colIndex",
                                    Point(it.x + 4, it.y - 2),
                                    Imgproc.FONT_HERSHEY_PLAIN,
                                    0.7,
                                    Scalar(255.0, 255.0, 255.0),
                                    1
                                )
                            }
                        }
                    }
                }
            }

            return matrix
        } catch (e: Exception) {
            Log.e("BreadboardDetector", "Error creating intersection matrix: ${e.message}")
            e.printStackTrace()
            return Array(0) { Array<Point?>(0) { null } }
        }
    }

    /**
     * Displays the grid and matrix on the warped image with color coding
     */
    /**
     * Modified displayGridMatrix function that returns the generated intersection matrix
     * for use with the highlighting function
     */
    private fun displayGridMatrix(warped: Mat, horizontalLines: List<Line>, verticalLines: List<Line>): Array<Array<Point?>> {
        try {
            // Safety check for null or empty inputs
            if (warped.empty() || horizontalLines.isEmpty() || verticalLines.isEmpty()) {
                Log.w("BreadboardDetector", "Cannot display grid matrix: Invalid inputs")
                return Array(0) { Array<Point?>(0) { null } }
            }

            // Draw horizontal lines with color gradient for better visualization
            for (i in horizontalLines.indices) {
                val line = horizontalLines[i]
                // Generate color based on index (gradient from blue to cyan)
                val blueComponent = 255
                val greenComponent = (255.0 * i / horizontalLines.size).toInt().coerceIn(0, 255)

                Imgproc.line(
                    warped,
                    Point(line.x1.toDouble(), line.y1.toDouble()),
                    Point(line.x2.toDouble(), line.y2.toDouble()),
                    Scalar(blueComponent.toDouble(), greenComponent.toDouble(), 0.0), // blue-cyan gradient
                    1
                )

                // Add row numbers on the left side
                Imgproc.putText(
                    warped,
                    "$i",
                    Point(5.0, line.y1.toDouble()),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.4,
                    Scalar(blueComponent.toDouble(), greenComponent.toDouble(), 0.0),
                    1
                )
            }

            // Draw vertical lines with color gradient
            for (i in verticalLines.indices) {
                val line = verticalLines[i]
                // Generate color based on index (gradient from red to yellow)
                val redComponent = 255
                val greenComponent = (255.0 * i / verticalLines.size).toInt().coerceIn(0, 255)

                Imgproc.line(
                    warped,
                    Point(line.x1.toDouble(), line.y1.toDouble()),
                    Point(line.x2.toDouble(), line.y2.toDouble()),
                    Scalar(0.0, greenComponent.toDouble(), redComponent.toDouble()), // red-yellow gradient
                    1
                )

                // Add column numbers at the top
                Imgproc.putText(
                    warped,
                    "$i",
                    Point(line.x1.toDouble(), 12.0),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.4,
                    Scalar(0.0, greenComponent.toDouble(), redComponent.toDouble()),
                    1
                )
            }

            // Create and display intersection matrix
            val intersectionMatrix = createIntersectionMatrix(warped, horizontalLines, verticalLines)

            // Display number of rows and columns in the intersection matrix
            Log.d("BreadboardDetector", "Intersection matrix size: ${intersectionMatrix.size} rows x ${if (intersectionMatrix.isNotEmpty()) intersectionMatrix[0].size else 0} columns")

            // Draw grid dimensions on warped image
            val textBgPts = MatOfPoint()
            textBgPts.fromArray(
                Point(5.0, warped.height() - 35.0),
                Point(150.0, warped.height() - 35.0),
                Point(150.0, warped.height() - 5.0),
                Point(5.0, warped.height() - 5.0)
            )

            Imgproc.fillConvexPoly(
                warped,
                textBgPts,
                Scalar(0.0, 0.0, 0.0, 200.0)  // semi-transparent black
            )

            Imgproc.putText(
                warped,
                "Grid: ${horizontalLines.size}x${verticalLines.size}",
                Point(10.0, warped.height() - 15.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                Scalar(255.0, 255.0, 255.0),
                1
            )

            textBgPts.release()

            // Return the intersection matrix for use with the highlighting function
            return intersectionMatrix
        } catch (e: Exception) {
            Log.e("BreadboardDetector", "Error displaying grid matrix: ${e.message}")
            e.printStackTrace()
            return Array(0) { Array<Point?>(0) { null } }
        }
    }


    /**
     * Enhanced algorithm with grid matrix detection and improved visualization
     */
    fun highlightGridPoint(rowIndex: Int, colIndex: Int): Boolean {
        // Validate indices against current matrix size
        val isValid = rowIndex >= 0 &&
                colIndex >= 0 &&
                currentIntersectionMatrix.isNotEmpty() &&
                rowIndex < currentIntersectionMatrix.size &&
                colIndex < currentIntersectionMatrix[0].size

        if (isValid) {
            // Add to points to highlight list
            highlightedPoints.add(Pair(rowIndex, colIndex))
            Log.d("BreadboardDetector", "Added grid point to highlight: $rowIndex, $colIndex")
        } else {
            Log.w("BreadboardDetector", "Invalid grid point: $rowIndex, $colIndex. " +
                    "Grid size: ${currentIntersectionMatrix.size}x" +
                    "${if (currentIntersectionMatrix.isNotEmpty()) currentIntersectionMatrix[0].size else 0}")
        }

        return isValid
    }

    /**
     * Clears all highlighted points
     */
    fun clearHighlightedPoints() {
        highlightedPoints.clear()
        Log.d("BreadboardDetector", "Cleared all highlighted grid points")
    }
    fun highlightMultipleGridPoints(
        points: List<Pair<Int, Int>>,
        colors: List<Scalar>? = null
    ): Int {
        // Clear previous highlights if requested
        highlightedPoints.clear()

        var validPoints = 0

        // Default color cycle if colors not provided
        val defaultColors = listOf(
            Scalar(0.0, 0.0, 255.0),    // Red
            Scalar(0.0, 255.0, 255.0),  // Yellow
            Scalar(255.0, 0.0, 0.0),    // Blue
            Scalar(255.0, 0.0, 255.0),  // Magenta
            Scalar(0.0, 255.0, 0.0),    // Green
            Scalar(128.0, 0.0, 128.0)   // Purple
        )

        // Process each point
        points.forEachIndexed { index, (rowIndex, colIndex) ->
            // Validate indices against current matrix size
            val isValid = rowIndex >= 0 &&
                    colIndex >= 0 &&
                    currentIntersectionMatrix.isNotEmpty() &&
                    rowIndex < currentIntersectionMatrix.size &&
                    colIndex < currentIntersectionMatrix[0].size

            if (isValid) {
                // Determine color for this point
                val color = if (colors != null && index < colors.size) {
                    colors[index]
                } else {
                    defaultColors[index % defaultColors.size]
                }

                // Add to points to highlight list with the selected color
                highlightedPoints.add(Pair(rowIndex, colIndex))
                validPoints++

                Log.d("BreadboardDetector", "Added grid point to highlight: $rowIndex, $colIndex")
            } else {
                Log.w("BreadboardDetector", "Invalid grid point: $rowIndex, $colIndex. " +
                        "Grid size: ${currentIntersectionMatrix.size}x" +
                        "${if (currentIntersectionMatrix.isNotEmpty()) currentIntersectionMatrix[0].size else 0}")
            }
        }

        return validPoints
    }
    fun highlightGridPath(
        points: List<Pair<Int, Int>>,
        pathColor: Scalar = Scalar(0.0, 165.0, 255.0), // Orange
        pointColor: Scalar = Scalar(0.0, 0.0, 255.0)   // Red
    ): Int {
        // Store points for regular highlighting
        val validPoints = highlightMultipleGridPoints(points, List(points.size) { pointColor })

        // Also store the path information to draw connecting lines
        // (We'll need to modify the onCameraFrame method to use this)
        pathPoints = points.filter { (row, col) ->
            row >= 0 &&
                    col >= 0 &&
                    currentIntersectionMatrix.isNotEmpty() &&
                    row < currentIntersectionMatrix.size &&
                    col < currentIntersectionMatrix[0].size
        }

        pathLineColor = pathColor

        return validPoints
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        try {
            val rgba = inputFrame.rgba()
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

            // Variables to store detected rows and columns count for display
            var rowCount = 0
            var colCount = 0
            var detectedCorners = 0

            // 6. Process the largest contour (assumed breadboard)
            maxContour?.let {
                // Draw full contour outline in yellow for debugging
                Imgproc.drawContours(
                    rgba,
                    listOf(it),
                    0,
                    Scalar(255.0, 255.0, 0.0), // Yellow
                    2
                )

                val contour2f = MatOfPoint2f(*it.toArray())

                // Approximate polygon with epsilon = 2% of contour perimeter
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                val points = approx.toArray()
                detectedCorners = points.size

                // Draw dots at all corners of the approximate polygon
                for (point in points) {
                    Imgproc.circle(
                        rgba,
                        point,
                        8,
                        Scalar(255.0, 0.0, 255.0), // Magenta
                        -1
                    )
                }

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

            // After processing breadboard and applying perspective transform
            warpedBreadboard?.let { warped ->
                try {
                    // Safety check for warped image
                    if (warped.empty()) {
                        Log.w("BreadboardDetector", "Warped breadboard is empty")
                        displayDebugInfo(rgba, detectedCorners, 0, 0)
                        return rgba
                    }

                    // Detect grid lines
                    val gridInfo = detectBreadboardGridRobust(warped)
                    val horizontalLines = gridInfo.first
                    val verticalLines = gridInfo.second

                    // Store line counts for debug display
                    rowCount = horizontalLines.size
                    colCount = verticalLines.size

                    // Check if we have valid grid lines
                    if (horizontalLines.isNotEmpty() && verticalLines.isNotEmpty()) {
                        // Create a copy of warped for display
                        val warpedForDisplay = warped.clone()

                        // Create and display the intersection matrix, store the result
                        currentIntersectionMatrix = displayGridMatrix(warpedForDisplay, horizontalLines, verticalLines)

                        // Apply highlights for all requested grid points
                        highlightedPoints.forEach { (row, col) ->
                            highlightGridIntersection(
                                warpedForDisplay,
                                currentIntersectionMatrix,
                                row,
                                col,
                                Scalar(0.0, 255.0, 255.0), // Cyan highlight
                                6 // Slightly larger radius for visibility
                            )
                        }

                        // Show the warped view with grid and intersections in the corner
                        try {
                            // Resize for display
                            val warpedDisplay = warpedForDisplay.clone()
                            Imgproc.resize(warpedDisplay, warpedDisplay, Size(rgba.width() / 4.0, rgba.height() / 4.0))

                            // Draw the warped display in the corner of the main frame
                            if (warpedDisplay.width() > 0 && warpedDisplay.height() > 0 &&
                                warpedDisplay.width() <= rgba.width() && warpedDisplay.height() <= rgba.height()) {
                                val roi = Mat(rgba, Rect(0, 0, warpedDisplay.width(), warpedDisplay.height()))
                                warpedDisplay.copyTo(roi)

                                // Draw border around the preview
                                Imgproc.rectangle(
                                    rgba,
                                    Point(0.0, 0.0),
                                    Point(warpedDisplay.width().toDouble(), warpedDisplay.height().toDouble()),
                                    Scalar(255.0, 255.0, 255.0),
                                    2
                                )
                            }

                            warpedDisplay.release()
                        } catch (e: Exception) {
                            Log.e("BreadboardDetector", "Error displaying warped image: ${e.message}")
                        }

                        warpedForDisplay.release()
                    } else {
                        // Reset intersection matrix if no grid lines detected
                        currentIntersectionMatrix = Array(0) { Array(0) { null } }

                        Log.w("BreadboardDetector", "No grid lines detected")
                        // Display warped image without grid
                        try {
                            val warpedDisplay = warped.clone()
                            Imgproc.resize(warpedDisplay, warpedDisplay, Size(rgba.width() / 4.0, rgba.height() / 4.0))
                            val roi = Mat(rgba, Rect(0, 0, warpedDisplay.width(), warpedDisplay.height()))
                            warpedDisplay.copyTo(roi)
                            warpedDisplay.release()
                        } catch (e: Exception) {
                            Log.e("BreadboardDetector", "Error displaying raw warped image: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BreadboardDetector", "Error processing warped breadboard: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Check if there's a valid grid
            if (currentIntersectionMatrix.isNotEmpty() && currentIntersectionMatrix[0].isNotEmpty()) {
                // Get the dimensions of the grid
                val lastRowIndex = currentIntersectionMatrix.size - 1
                val lastColIndex = currentIntersectionMatrix[0].size - 1

                // Create a list with first and last point
                val pointsToHighlight = listOf(
                    Pair(1, 1),                      // First point (0,0)
                    Pair(lastRowIndex, lastColIndex) // Last point (max row, max col)
                )

                // Define colors for the points (first=green, last=red)
                val colors = listOf(
                    Scalar(0.0, 255.0, 0.0),       // Green for first point
                    Scalar(0.0, 0.0, 255.0)        // Red for last point
                )

                // Use existing function to highlight the points
                highlightMultipleGridPoints(pointsToHighlight, colors)

                Log.d("BreadboardDetector", "Highlighted first point (0,0) and last point ($lastRowIndex,$lastColIndex)")

                // INTEGRATION: Draw the highlighted points in the original view
                drawHighlightedPointsInOriginalView(rgba)
            }

            // Display debug info in top corner
            displayDebugInfo(rgba, detectedCorners, rowCount, colCount)

            // Clean up resources
            hsv.release()
            mask.release()
            blurred.release()
            hierarchy.release()
            contours.forEach { it.release() }

            // Don't clear highlighted points here, to keep them visible
            // This lets the user see the points until they are deliberately cleared elsewhere
            // clearHighlightedPoints()

            return rgba
        } catch (e: Exception) {
            Log.e("BreadboardDetector", "Critical error in onCameraFrame: ${e.message}")
            e.printStackTrace()
            // Return an empty frame or the original frame in case of error
            return inputFrame.rgba()
        }
    }

    /**
     * Displays debug information on the frame
     */
    private fun displayDebugInfo(frame: Mat, corners: Int, rows: Int, cols: Int) {
        try {
            // Create background for text
            Imgproc.rectangle(
                frame,
                Point(frame.width() - 220.0, 10.0),
                Point(frame.width() - 10.0, 100.0),
                Scalar(0.0, 0.0, 0.0, 150.0), // semi-transparent black
                -1
            )

            // Draw debug information text
            Imgproc.putText(
                frame,
                "Detected Corners: $corners",
                Point(frame.width() - 210.0, 30.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                Scalar(255.0, 255.0, 255.0),
                1
            )

            Imgproc.putText(
                frame,
                "Rows (H-Lines): $rows",
                Point(frame.width() - 210.0, 50.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                Scalar(255.0, 255.0, 255.0),
                1
            )

            Imgproc.putText(
                frame,
                "Cols (V-Lines): $cols",
                Point(frame.width() - 210.0, 70.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.5,
                Scalar(255.0, 255.0, 255.0),
                1
            )

        } catch (e: Exception) {
            Log.e("BreadboardDetector", "Error displaying debug info: ${e.message}")
        }
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