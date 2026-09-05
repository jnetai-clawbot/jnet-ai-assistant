package com.jnetai.assistant.ui.navigation

sealed class Dest(val route: String, val label: String) {
    object Chat : Dest("chat", "Chat")
    object Documents : Dest("documents", "Docs")
    object Models : Dest("models", "Models")
    object Agents : Dest("agents", "Agents")
    object Voice : Dest("voice", "Voice")
    object Activity : Dest("activity", "Activity")
    object Settings : Dest("settings", "Setting")
    object About : Dest("about", "About")
    object Onboarding : Dest("onboarding", "Setup")
}

val allDestinations = listOf(
    Dest.Chat, Dest.Documents, Dest.Models, Dest.Agents, Dest.Voice, Dest.Activity, Dest.Settings
)