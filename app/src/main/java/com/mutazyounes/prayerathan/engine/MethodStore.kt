package com.mutazyounes.prayerathan.engine

import android.content.Context

interface MethodStore {
    fun read(): MethodSettings
    fun write(settings: MethodSettings)
}

class InMemoryMethodStore(
    initial: MethodSettings = MethodSettings.AUTO,
) : MethodStore {
    private var stored: MethodSettings = initial

    override fun read(): MethodSettings = stored

    override fun write(settings: MethodSettings) {
        stored = settings
    }
}

class PrefsMethodStore internal constructor(
    private val prefs: MethodPrefsBackend,
) : MethodStore {

    constructor(context: Context) : this(
        SharedPreferencesMethodBackend(context.applicationContext),
    )

    override fun read(): MethodSettings = MethodSettings(
        method = CalcMethod.fromStored(prefs.getString(KEY_METHOD)),
        asr = AsrSchool.fromStored(prefs.getString(KEY_ASR)),
    )

    override fun write(settings: MethodSettings) {
        prefs.write(
            mapOf(
                KEY_METHOD to (settings.method?.name ?: VALUE_AUTO),
                KEY_ASR to settings.asr.name,
            ),
        )
    }

    companion object {
        internal const val PREFS = "prayerathan_method"
        internal const val KEY_METHOD = "method"
        internal const val KEY_ASR = "asr"
        internal const val VALUE_AUTO = "AUTO"
    }
}

internal interface MethodPrefsBackend {
    fun getString(key: String): String?
    fun write(values: Map<String, String>)
}

private class SharedPreferencesMethodBackend(
    context: Context,
) : MethodPrefsBackend {
    private val prefs = context.getSharedPreferences(
        PrefsMethodStore.PREFS,
        Context.MODE_PRIVATE,
    )

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun write(values: Map<String, String>) {
        prefs.edit().apply {
            values.forEach { (key, value) -> putString(key, value) }
            apply()
        }
    }
}
