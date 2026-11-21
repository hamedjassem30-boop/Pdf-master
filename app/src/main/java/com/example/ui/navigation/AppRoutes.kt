package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ToolsRoute

@Serializable
object DashboardRoute

@Serializable
object CapabilitiesRoute

@Serializable
data class WorkspaceRoute(val toolId: String)

@Serializable
data class ViewerRoute(val filePath: String, val title: String, val mimeType: String)
