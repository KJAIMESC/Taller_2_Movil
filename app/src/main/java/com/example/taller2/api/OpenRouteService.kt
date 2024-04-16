package com.example.taller2.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import com.example.taller2.data.RouteModels
interface OpenRouteService {
    @GET("v2/directions/driving-car")
    suspend fun getRoute(
        @Query("api_key") apiKey: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): Response<RouteModels>
}