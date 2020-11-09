package jay.kotlin.projects.weatherapp.models

import java.io.Serializable

data class Main(
    val temp: Double,
    val pressure: Int,
    val humidity: Int,
    val tempMin: Int,
    val tempMax: Int
) : Serializable