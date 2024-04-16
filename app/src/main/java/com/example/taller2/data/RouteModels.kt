package com.example.taller2.data

import com.google.gson.annotations.SerializedName

data class RouteModels(@SerializedName("features")val features:List<Feature>)
data class Feature(@SerializedName("geometry") val geometry:Geometry)
data class Geometry(@SerializedName("coordinates") val coordinates:List<List<Double>> )

data class Segment(
    val steps: List<Step>
)

data class Step(
    val distance: Double,
    val duration: Double,
    val instruction: String,
    val name: String,
    val type: Int,
    val way_points: List<Int>
)
