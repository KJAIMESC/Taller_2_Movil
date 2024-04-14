package com.example.taller2

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.taller2.Alerts
import com.example.taller2.databinding.ActivityCamaraBinding
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.*

class CamaraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCamaraBinding
    private val alerts = Alerts(this)

    // Códigos de solicitud de permisos y actividades
    private val PERM_CAMERA_CODE = 101
    private val GALLERY_PERMISSION_REQUEST_CODE = 102
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_VIDEO_CAPTURE = 2
    private val REQUEST_PICK = 3
    private var outputPath: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCamaraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el click listener para el botón de tomar foto o video
        binding.takeButton.setOnClickListener {
            checkCameraPermissionAndTakePhotoOrVideo()
        }

        // Configurar el click listener para el botón de abrir la galería
        binding.galleryButton.setOnClickListener {
            checkGalleryPermissionAndOpen()
        }
    }

    // Verificar el permiso de la cámara y tomar una foto o video
    private fun checkCameraPermissionAndTakePhotoOrVideo() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            takePhotoOrVideo()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERM_CAMERA_CODE
            )
        }
    }

    // Verificar el permiso de la galería y abrir la galería
    private fun checkGalleryPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openGallery()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                GALLERY_PERMISSION_REQUEST_CODE
            )
        }
    }

    // Tomar una foto o video dependiendo de la configuración del switch
    private fun takePhotoOrVideo() {
        if (binding.videoSwitch.isChecked)
            dispatchTakeVideoIntent()
        else
            dispatchTakePictureIntent()
    }

    // Iniciar la actividad para capturar un video
    private fun dispatchTakeVideoIntent() {
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->
            val file = File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES).toString() + File.separator + "${DateFormat.getDateInstance().format(Date())}.mp4"
            )
            outputPath = FileProvider.getUriForFile(this, "com.example.android.fileprovider", file)
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputPath)
            takeVideoIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE)
            } ?: run {
                alerts.shortSimpleSnackbar(binding.root, "No se pudo tomar el video")
            }
        }
    }

    // Iniciar la actividad para capturar una foto
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val imageFileName = "${DateFormat.getDateInstance().format(Date())}.jpg"
        val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/" + imageFileName)
        outputPath = FileProvider.getUriForFile(
            this,
            "com.example.android.fileprovider",
            imageFile
        )
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputPath)
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: ActivityNotFoundException) {
            alerts.indefiniteSnackbar(binding.root, e.localizedMessage)
        }
    }

    // Abrir la galería para seleccionar una foto o video
    private fun openGallery() {
        val intentPick = Intent(Intent.ACTION_PICK)
        intentPick.type = if (binding.videoSwitch.isChecked) "video/*" else "image/*"
        startActivityForResult(intentPick, REQUEST_PICK)
    }

    // Manejar el resultado de la solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == GALLERY_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, abrir la galería
                openGallery()
            } else {
                // Permiso denegado
                alerts.shortSimpleSnackbar(binding.root, "Permiso denegado")
            }
        }
    }

    // Manejar el resultado de la actividad iniciada
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if (resultCode == RESULT_OK) {
                    alerts.shortSimpleSnackbar(binding.root, "Foto tomada correctamente")
                    outputPath?.let { uri ->
                        binding.cameraImageView.visibility = View.VISIBLE
                        binding.cameraVideoView.visibility = View.GONE
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()

                            // Rotar la imagen según su orientación
                            val rotation = getImageRotation(uri)
                            val rotatedBitmap = rotateBitmap(bitmap, rotation)

                            // Mostrar la imagen rotada
                            binding.cameraImageView.setImageBitmap(rotatedBitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            alerts.shortSimpleSnackbar(binding.root, "Error al cargar la imagen")
                        }
                    }
                } else {
                    alerts.shortSimpleSnackbar(binding.root, "No se pudo tomar la foto")
                }
            }

            REQUEST_VIDEO_CAPTURE -> {
                if (resultCode == RESULT_OK) {
                    alerts.shortSimpleSnackbar(binding.root, "Video tomado correctamente")
                    val videoUri = outputPath
                    if (videoUri != null) {
                        val aspectRatio = calculateVideoAspectRatio(videoUri)
                        val screenHeight = resources.displayMetrics.heightPixels
                        val maxHeight = (screenHeight * 0.8).toInt()
                        val maxWidth = (maxHeight / aspectRatio).toInt()

                        val params = binding.cameraVideoView.layoutParams as FrameLayout.LayoutParams
                        params.height = maxHeight
                        params.width = maxWidth
                        binding.cameraVideoView.layoutParams = params

                        binding.cameraImageView.visibility = View.GONE
                        binding.cameraVideoView.visibility = View.VISIBLE
                        binding.cameraVideoView.setVideoURI(videoUri)
                        binding.cameraVideoView.setMediaController(MediaController(this))
                        binding.cameraVideoView.requestFocus()
                        binding.cameraVideoView.start()
                    } else {
                        alerts.shortSimpleSnackbar(binding.root, "URI de video es nula")
                    }
                } else {
                    alerts.shortSimpleSnackbar(binding.root, "No se pudo tomar el video")
                }
            }

            REQUEST_PICK -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) {
                        if (binding.videoSwitch.isChecked) {
                            binding.cameraImageView.visibility = View.GONE
                            binding.cameraVideoView.visibility = View.VISIBLE
                            binding.cameraVideoView.setVideoURI(uri)
                            binding.cameraVideoView.setMediaController(MediaController(this))
                            binding.cameraVideoView.requestFocus()
                            binding.cameraVideoView.start()
                        } else {
                            binding.cameraImageView.visibility = View.VISIBLE
                            binding.cameraVideoView.visibility = View.GONE
                            binding.cameraImageView.setImageURI(uri)
                        }
                    } else {
                        alerts.shortSimpleSnackbar(binding.root, "URI es nula")
                    }
                } else {
                    alerts.shortSimpleSnackbar(binding.root, "Error al seleccionar de la galería")
                }
            }
        }
    }

    // Calcular la relación de aspecto de un video
    private fun calculateVideoAspectRatio(videoUri: Uri): Float {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, videoUri)
        val width =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloat() ?: 0f
        val height =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloat() ?: 0f
        retriever.release()
        return if (height != 0f) width / height else 0f
    }

    // Obtener la rotación de una imagen
    private fun getImageRotation(uri: Uri): Int {
        val inputStream = contentResolver.openInputStream(uri)
        inputStream?.use { stream ->
            val exif = ExifInterface(stream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
        return 0
    }

    // Rotar un bitmap
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
