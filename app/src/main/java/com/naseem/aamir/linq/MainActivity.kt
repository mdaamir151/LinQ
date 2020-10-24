package com.naseem.aamir.linq

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService

    class MainImageAnalyzer : ImageAnalysis.Analyzer {

        private var mStarted: Boolean = false
        private var mListener: WeakReference<TextListener>? = null

        //grubber regex
        private val regex =
            Regex("\\b(([\\w-]+://?|www[.])[^\\s()<>]+(?:\\([\\w\\d]+\\)|([^[:punct:]\\s]|/))?)")

        override fun analyze(imageProxy: ImageProxy) {
            processImage(imageProxy)
        }

        @SuppressLint("UnsafeExperimentalUsageError")
        private fun processImage(imageProxy: ImageProxy, rotation: Int = 0) {
            val mediaImage = imageProxy.image
            if (mStarted && mListener != null && mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, rotation)
                val recognizer = TextRecognition.getClient()
                recognizer.process(image).addOnSuccessListener {
                    val text = it.text
                    val matchedTextList =
                        regex.findAll(text).map { it.groupValues[0] }.filter { it.length > 5 }
                            .toList()
                    if (matchedTextList.isEmpty() && rotation < 270) {
                        processImage(imageProxy, rotation + 90)
                    } else {
                        mListener?.get()?.onTextReceived(matchedTextList)
                        closeImage(imageProxy)
                    }

                }.addOnFailureListener {
                    Log.d(TAG, "Exception: ${it.message}")
                }
            } else {
                closeImage(imageProxy)
            }
        }

        private fun closeImage(imageProxy: ImageProxy) {
            val task = object: TimerTask() {
                override fun run() {
                    imageProxy.close()
                }
            }
            Timer().schedule(task, 1000)
        }

        fun start() {
            mStarted = true
        }

        fun stop() {
            mStarted = false
        }

        fun setListener(listener: TextListener) {
            mListener = WeakReference(listener)
            Log.d(TAG, "ref1 = ${mListener?.get()}")
        }

        interface TextListener {
            fun onTextReceived(textList: List<String>)
        }
    }

    private var mStarted = false
    private lateinit var mainAdapter: MainAdapter

    private val analyzer = MainImageAnalyzer()
    private lateinit var textListener: MainImageAnalyzer.TextListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val shareButton = findViewById<View>(R.id.share_link)
        mainAdapter = MainAdapter(shareButton)
        textListener = object : MainImageAnalyzer.TextListener {
            override fun onTextReceived(textList: List<String>) {
                runOnUiThread {
                    recycler_view.visibility = View.VISIBLE
                    mainAdapter.addItems(textList)
                }
            }
        }
        analyzer.setListener(textListener)
        share_link.isEnabled = false
        recycler_view.adapter = mainAdapter
        recycler_view.layoutManager = LinearLayoutManager(this)
        val simpleItemTouchCallback: ItemTouchHelper.SimpleCallback =
            object :
                ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                    val pos = viewHolder.adapterPosition
                    mainAdapter.blackList(pos)
                }
            }
        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(recycler_view)
        start.setOnClickListener {
            if (mStarted) {
                analyzer.stop()
                mStarted = false
                start.setBackgroundResource(R.drawable.camera_btn)
            } else {
                mainAdapter.reset()
                recycler_view.visibility = View.INVISIBLE
                analyzer.start()
                mStarted = true
                start.setBackgroundResource(R.drawable.camera_btn_red)
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        share_link.setOnClickListener {
            val list = mainAdapter.getSelected()
            if (list.isNullOrEmpty().not()) {
                val intent = Intent(this, ShareActivity::class.java)
                intent.putExtra("data", list.joinToString(";"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "No Link selected", Toast.LENGTH_SHORT).show()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera Permission Denied!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val imageAnalyzer = imageAnalysis
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer, preview)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val TAG = "LinQMain"
        const val REQUEST_CODE_PERMISSIONS = 132
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}