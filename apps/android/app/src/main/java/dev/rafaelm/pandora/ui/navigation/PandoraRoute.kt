package dev.rafaelm.pandora.ui.navigation

import kotlinx.serialization.Serializable

sealed interface PandoraRoute {
    @Serializable
    data object Home : PandoraRoute

    @Serializable
    data object Tasks : PandoraRoute

    @Serializable
    data object Development : PandoraRoute

    @Serializable
    data object Settings : PandoraRoute

    @Serializable
    data object Appearance : PandoraRoute
}
