package com.example.guideline_mobile

import android.Manifest
import android.view.WindowManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ARActivityOne : ComponentActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var cameraView: JavaCameraView
    private lateinit var sbMinDist: SeekBar
    private lateinit var sbParam1: SeekBar
    private lateinit var sbParam2: SeekBar
    private lateinit var sbMinRadius: SeekBar
    private lateinit var sbMaxRadius: SeekBar
    private lateinit var tvMinDist: TextView
    private lateinit var tvParam1: TextView
    private lateinit var tvParam2: TextView
    private lateinit var tvMinRadius: TextView
    private lateinit var tvMaxRadius: TextView
    private lateinit var calibrationPanel: View

    // Constants for normalized breadboard representation
    private val NORMALIZED_WIDTH = 600  // Width for warped perspective
    private val NORMALIZED_HEIGHT = 400 // Height for warped perspective

    // Constants for breadboard grid
    private val EXPECTED_ROWS = 30    // Standard half-size breadboard has ~30 rows
    private val EXPECTED_COLUMNS = 10 // Standard half-size breadboard has ~10 columns per side

    // Grid line tracking for stability
    private val rowPositions = mutableListOf<Int>()
    private val colPositions = mutableListOf<Int>()
    private val HISTORY_SIZE = 5 // Number of frames to use for stabilization

    // HoughCircles parameters with default values
    private var minDist = 10.0
    private var param1 = 50.0    // Higher threshold for Canny edge detector
    private var param2 = 30.0    // Accumulator threshold
    private var minRadius = 3    // Minimum hole radius
    private var maxRadius = 10   // Maximum hole radius

    // Flag to show/hide calibration panel
    private var showCalibration = false

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

        // Initialize UI components
        cameraView = findViewById(R.id.java_camera_view)
        calibrationPanel = findViewById(R.id.calibration_panel)
        sbMinDist = findViewById(R.id.sb_min_dist)
        sbParam1 = findViewById(R.id.sb_param1)
        sbParam2 = findViewById(R.id.sb_param2)
        sbMinRadius = findViewById(R.id.sb_min_radius)
        sbMaxRadius = findViewById(R.id.sb_max_radius)
        tvMinDist = findViewById(R.id.tv_min_dist)
        tvParam1 = findViewById(R.id.tv_param1)
        tvParam2 = findViewById(R.id.tv_param2)
        tvMinRadius = findViewById(R.id.tv_min_radius)
        tvMaxRadius = findViewById(R.id.tv_max_radius)

        // Setup initial values for seekbars
        setupSeekBars()

        // Set button click listener for toggling calibration panel
        findViewById<View>(R.id.btn_calibrate).setOnClickListener {
            showCalibration = !showCalibration
            calibrationPanel.visibility = if (showCalibration) View.VISIBLE else View.GONE
        }

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

    private fun setupSeekBars() {
        // Initial values
        sbMinDist.progress = minDist.toInt()
        sbParam1.progress = param1.toInt()
        sbParam2.progress = param2.toInt()
        sbMinRadius.progress = minRadius
        sbMaxRadius.progress = maxRadius

        // Update text views
        updateParameterDisplay()

        // Set listeners for all seekbars
        sbMinDist.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                minDist = progress.toDouble().coerceAtLeast(1.0)
                updateParameterDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        sbParam1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                param1 = progress.toDouble().coerceAtLeast(1.0)
                updateParameterDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        sbParam2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                param2 = progress.toDouble().coerceAtLeast(1.0)
                updateParameterDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        sbMinRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                minRadius = progress.coerceAtLeast(1)
                // Ensure minRadius is smaller than maxRadius
                if (minRadius >= maxRadius) {
                    maxRadius = minRadius + 1
                    sbMaxRadius.progress = maxRadius
                }
                updateParameterDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        sbMaxRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                maxRadius = progress.coerceAtLeast(minRadius + 1)
                updateParameterDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateParameterDisplay() {
        tvMinDist.text = "Min Distance: ${minDist.toInt()}"
        tvParam1.text = "Param1 (Canny): ${param1.toInt()}"
        tvParam2.text = "Param2 (Threshold): ${param2.toInt()}"
        tvMinRadius.text = "Min Radius: $minRadius"
        tvMaxRadius.text = "Max Radius: $maxRadius"
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
            11.0
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
            minDist,
            param1,
            param2,
            minRadius,
            maxRadius
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

    /**
     * Draw grid lines on the given Mat
     */
    private fun drawGridLines(mat: Mat, horizontalLines: List<Line>, verticalLines: List<Line>) {
        // Draw horizontal lines in blue
        for (line in horizontalLines) {
            Imgproc.line(
                mat,
                Point(line.x1.toDouble(), line.y1.toDouble()),
                Point(line.x2.toDouble(), line.y2.toDouble()),
                Scalar(255.0, 0.0, 0.0),  // Blue color (BGR)
                1
            )
        }

        // Draw vertical lines in green
        for (line in verticalLines) {
            Imgproc.line(
                mat,
                Point(line.x1.toDouble(), line.y1.toDouble()),
                Point(line.x2.toDouble(), line.y2.toDouble()),
                Scalar(0.0, 255.0, 0.0),  // Green color (BGR)
                1
            )
        }
    }

    /**
     * Draw detected holes on the given Mat
     */
    private fun drawHoles(mat: Mat, holes: List<BreadboardHole>) {
        for (hole in holes) {
            // Draw circle outline - red for occupied, green for empty
            val color = if (hole.isOccupied) {
                Scalar(0.0, 0.0, 255.0)  // Red for occupied holes
            } else {
                Scalar(0.0, 255.0, 0.0)  // Green for empty holes
            }

            Imgproc.circle(
                mat,
                Point(hole.x.toDouble(), hole.y.toDouble()),
                hole.radius,
                color,
                1
            )

            // Draw circle center point
            Imgproc.circle(
                mat,
                Point(hole.x.toDouble(), hole.y.toDouble()),
                1,
                color,
                1
            )
        }
    }

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
                    // Use the enhanced robust grid detection (HoughLines approach)
                    val (horizontalLines, verticalLines) = detectBreadboardGridRobust(warpedBreadboard!!)

                    // Use HoughCircles to detect holes and their occupancy
                    val holes = detectBreadboardHoles(warpedBreadboard!!)

                    // Draw the grid lines and holes on the warped breadboard
                    val warpedWithOverlays = warpedBreadboard!!.clone()
                    drawGridLines(warpedWithOverlays, horizontalLines, verticalLines)
                    drawHoles(warpedWithOverlays, holes)

                    // Create the preview image
                    val preview = Mat()
                    val previewSize = Size(rgba.width() * 0.3, rgba.height() * 0.3)
                    Imgproc.resize(warpedWithOverlays, preview, previewSize)

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

                    // Display grid line and hole detection stats
                    Imgproc.putText(
                        rgba,
                        "Grid: ${horizontalLines.size}r x ${verticalLines.size}c",
                        Point(10.0, rgba.height() - 40.0),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.5,
                        Scalar(255.0, 255.0, 255.0),
                        1
                    )

                    Imgproc.putText(
                        rgba,
                        "Holes: ${holes.size} (${holes.count { it.isOccupied }} occupied)",
                        Point(10.0, rgba.height() - 20.0),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.5,
                        Scalar(255.0, 255.0, 255.0),
                        1
                    )

                    // Clean up
                    preview.release()
                    warpedWithOverlays.release()
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