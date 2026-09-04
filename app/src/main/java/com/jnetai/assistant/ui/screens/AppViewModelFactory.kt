package com.jnetai.assistant.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jnetai.assistant.data.AppGraph

class AppViewModelFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(graph.context as android.app.Application) as T
    }
}