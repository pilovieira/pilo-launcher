package br.com.pilovieira.launcher

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
) {
    val key: String
        get() = "$packageName/$activityName"
}
