package br.com.pilovieira.launcher

data class AppLanguage(val tag: String?, val label: String)

val APP_LANGUAGES = listOf(
    AppLanguage(null, "System default"),
    AppLanguage("en", "English"),
    AppLanguage("pt", "Português"),
    AppLanguage("pt-BR", "Português (Brasil)"),
    AppLanguage("es", "Español"),
    AppLanguage("fr", "Français"),
    AppLanguage("it", "Italiano"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("ar", "العربية"),
    AppLanguage("ru", "Русский"),
    AppLanguage("ja", "日本語"),
    AppLanguage("zh-CN", "中文"),
    AppLanguage("hi", "हिन्दी"),
    AppLanguage("ko", "한국어"),
    AppLanguage("tr", "Türkçe"),
    AppLanguage("id", "Bahasa Indonesia"),
    AppLanguage("pl", "Polski")
)
