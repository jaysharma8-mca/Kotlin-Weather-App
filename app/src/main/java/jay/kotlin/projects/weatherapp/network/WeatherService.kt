package jay.kotlin.projects.weatherapp.network

import jay.kotlin.projects.weatherapp.models.WeatherResponseMain
import retrofit.Call
import retrofit.http.GET
import retrofit.http.Query

interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String?,
        @Query("appId") appId: String?,
    ):Call<WeatherResponseMain>
}