package fi.villivisio.kuhmonaali

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.result.transformer.scaled
import io.fotoapparat.selector.*
import io.fotoapparat.view.CameraView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener {
    private val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    private lateinit var fotoapparat: Fotoapparat
    private val dest = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    private var fotoapparatState : FotoapparatState? = null
    private var cameraStatus : CameraState? = null
    private var flashState: FlashState? = null
    private var timeformat = "yyyyMMddHHmmss"
    private var mSensorManager : SensorManager ?= null
    private var mAccelerometer : Sensor ?= null
    private var shootAllowed: Boolean = false
    private var mediaPlayer: MediaPlayer? = null
    private var notEnoughCounter = 0
    private var notEnoughMax = 7
    private var enoughCounter = 0
    private var enoughMax = 5
    private var scaleFactor = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab_camera.setOnClickListener {
            takePhoto()
        }

        fab_switch_camera.setOnClickListener {
            switchCamera()
        }

        fab_flash.setOnClickListener {
            changeFlashState()
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            scaleFactor = 0.7f
        }

        createFotoapparat()
        cameraStatus = CameraState.BACK
        flashState = FlashState.OFF
        fotoapparatState = FotoapparatState.OFF

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // focus in accelerometer
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStop() {
        super.onStop()
        fotoapparat.stop()
        FotoapparatState.OFF
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onStart() {
        super.onStart()
        if (hasNoPermissions()) {
            requestPermission()
        } else{
            fotoapparat.start()
            fotoapparatState = FotoapparatState.ON
            mediaPlayer = MediaPlayer.create(this, R.raw.shutter)
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager!!.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME)
        if(!hasNoPermissions() && fotoapparatState == FotoapparatState.OFF){
            val intent = Intent(baseContext, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        fotoapparat.switchTo(if (cameraStatus == CameraState.BACK) back() else front(), CameraConfiguration())
    }

    override fun onPause() {
        super.onPause()
        mSensorManager!!.unregisterListener(this)
    }

    private fun takePhoto() {
        if (hasNoPermissions()) {
            requestPermission()
        } else if (!shootAllowed) {
            val redLayer = findViewById<TextView>(R.id.red_layer_text)
            redLayer.text = resources.getString(resources.getIdentifier("not_enough_${notEnoughCounter}", "string", packageName))
            notEnoughCounter = (notEnoughCounter + 1) % notEnoughMax
            Thread {
                Thread.sleep(1500)
                runOnUiThread {
                    findViewById<TextView>(R.id.red_layer_text).text = ""
                }
            }.start()
        } else {
            val blackLayer = findViewById<ConstraintLayout>(R.id.black_layer)
            blackLayer.visibility = View.VISIBLE
            Thread {
                Thread.sleep(100)
                runOnUiThread {
                    blackLayer.visibility = View.GONE
                }
            }.start()

            mediaPlayer?.start()

            val redLayer = findViewById<TextView>(R.id.red_layer_text)
            redLayer.text = resources.getString(resources.getIdentifier("enough_${enoughCounter}", "string", packageName))
            enoughCounter = (enoughCounter + 1) % enoughMax

            val result = fotoapparat.takePicture()
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern(timeformat)
                current.format(formatter)
            } else {
                val now = Date()
                val formatter = SimpleDateFormat(timeformat, resources.configuration.locale)
                formatter.format(now)
            }
            val filename = "${timestamp}_${resources.getString(R.string.app_name)}.png"
            val file = File(dest, filename)

            result.saveToFile(file).whenAvailable {
                handleRotation(file.absolutePath)
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.path),
                    arrayOf("image/jpeg"),
                    null
                )
                val imageView: ImageView = findViewById(R.id.result)
                imageView.setOnClickListener {
                    val intent = Intent(this, ResultActivity::class.java)
                    intent.putExtra("filename", file.absolutePath)
                    startActivity(intent)
                }
                imageView.visibility = View.VISIBLE
            }

            Thread {
                result.toBitmap(scaled(scaleFactor)).whenAvailable { bitmapPhoto ->
                    if (bitmapPhoto != null) {
                        runOnUiThread {
                            val imageView: ImageView = findViewById(R.id.result)
                            val width = 150
                            val ratio:Float = bitmapPhoto.bitmap.width.toFloat() / bitmapPhoto.bitmap.height.toFloat()
                            val height = (width / ratio).roundToInt()
                            val bitmap = Bitmap.createScaledBitmap(bitmapPhoto.bitmap, width, height, false)
                            imageView.setImageBitmap(bitmap)
                            imageView.rotation = (-bitmapPhoto.rotationDegrees).toFloat()
                        }
                    }
                    Thread.sleep(1500)
                    runOnUiThread {
                        findViewById<TextView>(R.id.red_layer_text).text = ""
                    }
                }
            }.start()
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        val scaledBitmap = Bitmap.createScaledBitmap(this, width, height, true)
        return Bitmap.createBitmap(
            scaledBitmap,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height,
            matrix,
            true
        )
    }

    private fun handleRotation(imgPath: String) {
        Thread {
            BitmapFactory.decodeFile(imgPath)?.let { origin ->
                try {
                    ExifInterface(imgPath).apply {
                        getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_UNDEFINED
                        ).let { orientation ->
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> origin.rotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> origin.rotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> origin.rotate(270f)
                                ExifInterface.ORIENTATION_NORMAL -> origin
                                else -> origin
                            }.also { bitmap ->
                                //Update the input file with the new bytes.
                                try {
                                    FileOutputStream(imgPath).use { fos ->
                                        bitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            100,
                                            fos
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor != mAccelerometer) {
            return
        }
        val aX = event.values[0]
        val aY = event.values[1]
        val angle = atan2(aX, aY) / (Math.PI/180)

        val minAngle = 30
        val maxAlpha = 200
        val angleFromRequired: Float = if (angle > -minAngle && angle < minAngle) minAngle - abs(angle.toFloat())
            else if ((angle > 90 - minAngle && angle < 90 + minAngle) || (angle < -(90 - minAngle) && angle > -(90 + minAngle))) minAngle - abs((abs(angle.toFloat()) - 90))
            else if (angle > 180 - minAngle || angle < -(180 - minAngle)) minAngle - abs(abs(angle.toFloat()) - 180)
            else 0f

        val redLayer = findViewById<ConstraintLayout>(R.id.red_layer)
        val redLayerText = findViewById<TextView>(R.id.red_layer_text)
        val background = redLayer.background
        redLayerText.rotation = angle.toFloat()

        var layerAlpha = angleFromRequired / minAngle * maxAlpha
        if (layerAlpha > 0) {
            shootAllowed = false
            layerAlpha += 20
        } else {
            shootAllowed = true
        }
        background.alpha = layerAlpha.toInt()
    }

    private fun switchCamera() {
        fotoapparat.switchTo(
            lensPosition =  if (cameraStatus == CameraState.BACK) front() else back(),
            cameraConfiguration = CameraConfiguration()
        )

        cameraStatus = if (cameraStatus == CameraState.BACK) CameraState.FRONT
        else CameraState.BACK
    }

    private fun changeFlashState() {
        fotoapparat.updateConfiguration(
            CameraConfiguration(
                flashMode = if(flashState == FlashState.TORCH) off() else torch()
            )
        )

        flashState = if(flashState == FlashState.TORCH) FlashState.OFF
        else FlashState.TORCH
    }

    private fun hasNoPermissions(): Boolean{
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(){
        ActivityCompat.requestPermissions(this, permissions,0)
    }

    private fun createFotoapparat(){
        val cameraView = findViewById<CameraView>(R.id.camera_view)

        val cameraConfiguration = CameraConfiguration(
            pictureResolution = highestResolution(), // (optional) we want to have the highest possible photo resolution
            previewResolution = highestResolution(), // (optional) we want to have the highest possible preview resolution
            previewFpsRange = highestFps(),          // (optional) we want to have the best frame rate
            focusMode = firstAvailable(              // (optional) use the first focus mode which is supported by device
                continuousFocusPicture(),
                autoFocus(),                       // if continuous focus is not available on device, auto focus will be used
                fixed()                            // if even auto focus is not available - fixed focus mode will be used
            ),
            flashMode = firstAvailable(              // (optional) similar to how it is done for focus mode, this time for flash
                autoRedEye(),
                autoFlash(),
                torch(),
                off()
            ),
            antiBandingMode = firstAvailable(       // (optional) similar to how it is done for focus mode & flash, now for anti banding
                auto(),
                hz50(),
                hz60(),
                none()
            ),
            jpegQuality = manualJpegQuality(80),     // (optional) select a jpeg quality of 80 (out of 0-100) values
            sensorSensitivity = lowestSensorSensitivity() // (optional) we want to have the lowest sensor sensitivity (ISO)
        )

        fotoapparat = Fotoapparat(
            context = this,
            view = cameraView,
            scaleType = ScaleType.CenterCrop,
            lensPosition = back(),
            cameraConfiguration = cameraConfiguration,
            logger = loggers(
                logcat()
            ),
            cameraErrorCallback = { e ->
                e.printStackTrace()
            }
        )
    }

    enum class CameraState{
        FRONT, BACK
    }

    enum class FlashState{
        TORCH, OFF
    }

    enum class FotoapparatState{
        ON, OFF
    }
}
