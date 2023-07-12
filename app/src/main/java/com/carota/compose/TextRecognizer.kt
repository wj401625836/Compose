package com.carota.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.carota.compose.ui.theme.ComposeTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors


class TextRecognizer : ComponentActivity() {

    companion object {
        const val CAMERA_PERMISSION_CODE = 100
    }
    private lateinit var  cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider
    private var preview: androidx.camera.core.Preview = androidx.camera.core.Preview.Builder().build()

    private val analysis = ImageAnalysis.Builder()
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    var previewState by mutableStateOf(true)
    var mBitmap:Bitmap? = null
    var globalText by mutableStateOf("")
    var rotationDegrees:Int = 0
    var camera:Camera? = null

    lateinit var previewView:PreviewView


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProvider = cameraProviderFuture.get()
        analysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyzeImage)
        requestPermission()
        setContent {
            ComposeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box() {
                        val clickPosition = remember { mutableStateOf(Offset.Zero) }
                        previewView = previewViewComponent(preview)
                        previewView.setOnTouchListener { _, event ->
                            clickPosition.value = Offset(event.x, event.y)
                            false
                        }
                        previewView.setOnClickListener { prev ->
                            println("wangjian onclick x= ${clickPosition.value.x}, y = ${clickPosition.value.y}")
                            camera?.cameraControl?.let {control ->
                                prev.isClickable = false
                                val tapPoint = previewView.meteringPointFactory.createPoint(clickPosition.value.x, clickPosition.value.y)
                                val action = FocusMeteringAction.Builder(tapPoint).build()
                                val future = control.startFocusAndMetering(action)
                                future.addListener({
                                    val result = future.get()
                                    if (result.isFocusSuccessful) {
                                        println("wangjian 对焦成功")
                                    } else {
                                        println("wangjian 对焦失败")
                                    }
                                    runOnUiThread{
                                        prev.isClickable = true
                                    }
                                }, Executors.newSingleThreadExecutor())
                            }
                        }
                        val buttonText = remember { mutableStateOf("点击开始文本识别") }
                        MyComposable()
                        Button(onClick = {
                            previewState = !previewState
                            println("wangjian previewState = $previewState")
                            if (!previewState) {
                                cameraProvider.unbindAll()
                                camera = null
                                buttonText.value = "点击停止文本识别"
                                showText()
                            } else {
                                updateGlobalText("")
                                globalText = ""
                                buttonText.value = "点击开始文本识别"
                                bindPreview()
                            }
                        }, modifier = Modifier.align(alignment = Alignment.BottomStart)
                        ) {
                            Text(text = buttonText.value)
                        }
                        Button(
                            onClick = { setClipboardText(this@TextRecognizer, globalText) },
                            modifier = Modifier.align(alignment = Alignment.BottomEnd)) {
                            Text("复制到剪贴板")
                        }
                    }
                }
            }
        }
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            setupCamera();
        }
    }

    /**
     * 设置相机
     */
    private fun setupCamera() {
        cameraProviderFuture.addListener({bindPreview()}, ContextCompat.getMainExecutor(this))
    }

    /**
     * 绑定 preview
     */
    private fun bindPreview() {
        camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        println("wangjian bindPreview camera = $camera")
    }

    /**
     * 解析文本
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(imageProxy: ImageProxy) {
        mBitmap = imageProxy.toBitmap()
        rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y plane
        val uBuffer = planes[1].buffer // U plane
        val vBuffer = planes[2].buffer // V plane

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, outputStream)
        val jpegByteArray = outputStream.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)

        outputStream.close()

        return bitmap
    }


    private fun showText() {
        mBitmap?.let {
            val inputImage = InputImage.fromBitmap(it, rotationDegrees)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    updateGlobalText(result.text)
                    globalText = result.text
                    println("wangjian 111 = ${result.text}")
                }
                .addOnCompleteListener {
                }
                .addOnFailureListener {
                    println("wangjian addOnFailureListener")
                    it.printStackTrace()
                }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera()
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun previewViewComponent(preview: androidx.camera.core.Preview):PreviewView {
    val context = LocalContext.current

    val previewView = PreviewView(context).apply {
        preview.setSurfaceProvider(surfaceProvider)
    }
    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxSize()
    )
    return previewView
}

fun setClipboardText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("label", text)
    clipboard.setPrimaryClip(clip)
}
