package com.alothmany.wa.core.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Groups : Destination("groups")
    data object Tasks : Destination("tasks")
    data object Results : Destination("results")
    data object Settings : Destination("settings")
    data object Logs : Destination("logs")
    data object Diagnostics : Destination("diagnostics")
    data object Sync : Destination("feature/sync")
    data object Join : Destination("feature/join")
    data object Check : Destination("feature/check")
    data object Extract : Destination("feature/extract")
    data object Publish : Destination("feature/publish")
    data object Delete : Destination("feature/delete")
}
