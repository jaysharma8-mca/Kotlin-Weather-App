@file:Suppress("NAME_SHADOWING")

package jay.kotlin.projects.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import jay.kotlin.projects.weatherapp.R
import jay.kotlin.projects.weatherapp.models.WeatherResponseMain
import jay.kotlin.projects.weatherapp.network.WeatherService
import jay.kotlin.projects.weatherapp.utils.Constants
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private lateinit var mSharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        locationEnabled()
        setupUI()
    }

    private fun locationEnabled(){
        if (!isLocationEnabled()) {
            Toast.makeText(
                    this,
                    "Your location provider is turned off. Please turn it on.",
                    Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                    .withPermissions(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    .withListener(object : MultiplePermissionsListener {
                        override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if (report!!.areAllPermissionsGranted()) {
                                requestLocationData()
                            }

                            if (report.isAnyPermissionPermanentlyDenied) {
                                Toast.makeText(
                                        this@MainActivity,
                                        "You have denied location permission. Please allow it.",
                                        Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(
                                permissions: MutableList<PermissionRequest>?,
                                token: PermissionToken?
                        ) {
                            showRationalDialogForPermissions()
                        }
                    }).onSameThread()
                    .check()
        }
    }


    // This provides access to the system location services.
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
        )
    }


    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
                .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
                .setPositiveButton(
                        "GO TO SETTINGS"
                ) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {

            val mLastLocation: Location = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.e("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.e("Current Longitude", "$mLongitude")

            getLocationWeatherDetails(mLatitude, mLongitude)
        }
    }


    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).
            addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponseMain> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object: Callback<WeatherResponseMain>{
                override fun onResponse(
                    response: Response<WeatherResponseMain>?,
                    retrofit: Retrofit?
                ) {
                    if (response != null) {
                        if(response.isSuccess){
                            val weatherList: WeatherResponseMain = response.body()
                            Log.i("Response Result", "$weatherList")
                            hideProgressDialog()

                            val weatherResponseJsonString = Gson().toJson(weatherList)
                            val editor = mSharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                            editor.apply()
                            setupUI()
                        }
                        else{
                            when(response.code()){
                                400->{
                                    Log.e("Error 400", "Bad Connection")
                                    hideProgressDialog()
                                    Toast.makeText(this@MainActivity, "Bad Connection", Toast.LENGTH_LONG).show()
                                }
                                404->{
                                    Log.e("Error 404", "Page Not Found")
                                    hideProgressDialog()
                                    Toast.makeText(this@MainActivity, "No Data Found", Toast.LENGTH_LONG).show()
                                }
                                else->{
                                    Log.e("Error", "Generic Error")
                                    hideProgressDialog()
                                    Toast.makeText(this@MainActivity, "Generic Error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    Log.e("Errror", t!!.message.toString())
                    showCustomProgressDialog()
                }

            })
        }
        else{
            Toast.makeText(this, "Internet Not Connected", Toast.LENGTH_LONG).show()
        }
    }


    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }


    @SuppressLint("SetTextI18n")
    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponseMain::class.java)
            for (i in weatherList.weather.indices) {
                Log.i("Weather NAME", weatherList.weather[i].main)

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    tv_temp.text =
                        weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                }

                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.tempMin.toString() + " min"
                tv_max.text = weatherList.main.tempMax.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise.toLong())
                tv_sunset_time.text = unixTime(weatherList.sys.sunset.toLong())


                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }

            }
        }
        }


    private fun getUnit(value: String): String? {
        Log.i("unit", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }


    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }else -> super.onOptionsItemSelected(item)
        }
    }


}