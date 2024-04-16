package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller2.databinding.ActivityMapaBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.provider.Settings
import android.net.Uri
import com.google.android.material.snackbar.Snackbar
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.view.View
import android.widget.Toast
import android.util.Log
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.example.taller2.api.OpenRouteService
import com.example.taller2.data.RouteModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MapaActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {
    private lateinit var binding: ActivityMapaBinding
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var root: View
    private var valormax: Float = 0f
    private lateinit var polyline: Polyline
    private val pathPoints = mutableListOf<LatLng>()
    private var locationCallback: LocationCallback? = null
    private lateinit var searchEditText: EditText
    private lateinit var geocoder: Geocoder



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        root = findViewById(R.id.root)


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        lightSensor?.let {
            valormax = it.maximumRange
        } ?: run {
            Toast.makeText(this, "El dispositivo no tiene sensor de luz!", Toast.LENGTH_SHORT)
                .show()
            finish()
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Añade la ubicación a la lista de puntos de ruta
                    val point = LatLng(location.latitude, location.longitude)
                    pathPoints.add(point)

                    // Actualiza la Polyline en el mapa
                    polyline.points = pathPoints
                }
            }
        }

        // Comienza a recibir actualizaciones de ubicación
        startLocationUpdates()

        searchEditText = findViewById(R.id.searchEditText)
        geocoder = Geocoder(this)

        // Configura el listener para el evento de edición terminada
        searchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                geolocateAddress(searchEditText.text.toString())
                true // Consumir el evento del teclado
            } else false
        }


    }

    private fun geolocateAddress(addressString: String) {
        val addresses = geocoder.getFromLocationName(addressString, 1)
        if (addresses != null && addresses.isNotEmpty()) {
            val address = addresses.first()
            val latLng = LatLng(address.latitude, address.longitude)
            // Agregar el título del marcador con la dirección completa o una descripción
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(address.getAddressLine(0))

            map.addMarker(markerOptions)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0f))
        } else {
            Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_LONG).show()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // Intervalo de actualización
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Pedir permisos
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        // Comenzar a recibir actualizaciones de ubicación
        locationCallback?.let {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                it,
                Looper.getMainLooper()
            )
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableMyLocation()

        val polylineOptions = PolylineOptions().apply {
            width(5f)
            color(Color.BLUE)
        }
        polyline = map.addPolyline(polylineOptions)

        map.setOnMapLongClickListener { latLng ->
            setDestination(latLng)
        }
    }

    private fun setDestination(latLng: LatLng) {
        map.clear()  // Opcional: limpiar el mapa antes de añadir un nuevo destino
        val address = getAddressFromLatLng(latLng)
        map.addMarker(MarkerOptions().position(latLng).title(address))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0f))

        val startPoint = if (pathPoints.isNotEmpty()) pathPoints.last() else latLng
        createRoute(startPoint, latLng)
    }

    private suspend fun fetchRoute(originStr: String, destinationStr: String): RouteModels? {
        return withContext(Dispatchers.IO) { // Utiliza Dispatchers.IO para llamadas de red
            try {
                val response = Retrofit.Builder()
                    .baseUrl("https://api.openrouteservice.org")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(OpenRouteService::class.java)
                    .getRoute("5b3ce3597851110001cf6248ed0fc7dbb697486ca03c5f793bb56f7d", originStr, destinationStr)

                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("MapaActivity", "Error obteniendo la ruta: ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("MapaActivity", "Error en la llamada a la API", e)
                null
            }
        }
    }

    private fun createRoute(origin: LatLng, destination: LatLng) {
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destination.latitude},${destination.longitude}"

        CoroutineScope(Dispatchers.Main).launch {
            val routeResponse = fetchRoute(originStr, destinationStr)
            routeResponse?.let { drawRoute(it) }
        }
    }

    private fun drawRoute(routeResponse: RouteModels) {
        val points = routeResponse.features.first().geometry.coordinates.map { coordinate ->
            LatLng(coordinate[1], coordinate[0]) // Asumiendo que el formato es [longitud, latitud]
        }

        // Crear opciones de polyline y añadir todos los puntos
        val polylineOptions = PolylineOptions().addAll(points).width(8f).color(Color.RED)

        runOnUiThread {
            // Añadir la polyline al mapa
            polyline = map.addPolyline(polylineOptions)
        }
    }


    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        map.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                map.addMarker(MarkerOptions().position(currentLatLng).title("Mi ubicación"))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    // este hilo esperando la respuesta del usuario!
                    Snackbar.make(
                        binding.root,
                        "Se requieren los permisos de ubicación para mostrar su posición en el mapa.",
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction("OK") {
                            ActivityCompat.requestPermissions(
                                this@MapaActivity,
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ),
                                LOCATION_PERMISSION_REQUEST_CODE
                            )
                        }
                        .show()
                } else {
                    // El usuario ha denegado los permisos y ha seleccionado no volver a preguntar.
                    Snackbar.make(
                        binding.root,
                        "Por favor, activa la ubicación en la configuración.",
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction("Ajustes") {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                            startActivity(intent)
                        }
                        .show()
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LIGHT) {
                val lux = it.values[0]
                Log.d("MapaActivity", "Luminosidad: $lux lx")

                // Asegúrate de que el mapa esté inicializado antes de utilizarlo.
                if (::map.isInitialized) {
                    val styleResId = when {
                        lux < 1 -> R.raw.night_style // Muy oscuro
                        else -> R.raw.day_style // Día soleado
                    }
                    val style = MapStyleOptions.loadRawResourceStyle(this@MapaActivity, styleResId)
                    map.setMapStyle(style)
                }
            }
        }
    }

    private fun getAddressFromLatLng(latLng: LatLng): String {
        val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
        if (addresses != null) {
            if (addresses.isNotEmpty()) {
                return addresses[0].getAddressLine(0)
            }
        }
        return "Dirección Desconocida"
    }


    override fun onResume() {
        super.onResume()
        lightSensor?.also { light ->
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //no quiero hacer nada ahorita
    }


}
