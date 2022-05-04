package com.example.arexample

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.arexample.databinding.ArMainBinding
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.coroutines.sync.Mutex
import java.io.*


class MainActivity : FragmentActivity() {
    private lateinit var binding: ArMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ArMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Load model.glb from assets folder or http url
        val arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment)
        arFragment.setOnTapPlaneGlbModel("Persian.glb")
        arFragment.

        binding.screenshot.setOnClickListener {
            val sceneView = arFragment.arSceneView
//            val currentFrame: Frame = sceneView.arFrame!!
//            val currentImage: Image = currentFrame.acquireCameraImage()
//            val bitmap = imageToBitmap(currentImage, 90f)
            val bitmap = Bitmap.createBitmap(
                sceneView.width,
                sceneView.height, Bitmap.Config.ARGB_8888
            )
            val filename = "${System.currentTimeMillis()}.jpg"
            // Create a handler thread to offload the processing of the image.
            val handlerThread = HandlerThread("PixelCopier");
            handlerThread.start();
            PixelCopy.request(sceneView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    try {
                        saveMediaToStorage(bitmap, filename)
                    } catch (e: IOException) {
                        val toast: Toast = Toast.makeText(
                            this@MainActivity, e.toString(),
                            Toast.LENGTH_LONG
                        )
                        toast.show()
                        return@request
                    }
                } else {
                    val toast = Toast.makeText(
                        this@MainActivity,
                        "Failed to copyPixels: $copyResult", Toast.LENGTH_LONG
                    )
                    toast.show()
                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.looper))

        }
    }

    fun saveMediaToStorage(bitmap: Bitmap, filename: String) {
        //Output stream
        var fos: OutputStream? = null

        //For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //getting the contentResolver
            this?.contentResolver?.also { resolver ->

                //Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    //putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                //Inserting the contentValues to contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                //Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            //These for devices running on android < Q
            //So I don't think an explanation is needed here
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            //Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(this, "Saved to Photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val dirMutex: Mutex = Mutex()
    private fun getFilePath(fileName: String): Uri? {
        val mediaStorageDir = File(this.filesDir, "files" + File.separator + "screenshots")
        // Create directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }

        val mediaFile = File(mediaStorageDir.path + File.separator + fileName)
        return Uri.fromFile(mediaFile)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun imageToBitmap(image: Image, rotationDegrees: Float): Bitmap? {
        val planes: Array<Image.Plane> = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        //U and V are swapped
        //U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)

        val imageBytes = out.toByteArray()
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        out.close()
        val resultBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees)
        return Bitmap.createBitmap(
            resultBitmap,
            0,
            0,
            resultBitmap.width,
            resultBitmap.height,
            matrix,
            true
        )
    }
}