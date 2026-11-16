package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AxionRoute {
    @Serializable
    data object StudioHome : AxionRoute

    @Serializable
    data object ImageEditor : AxionRoute

    @Serializable
    data object AiAnalysis : AxionRoute

    @Serializable
    data object ImageHistory : AxionRoute

    @Serializable
    data object ModelManager : AxionRoute

    @Serializable
    data object Settings : AxionRoute
}
