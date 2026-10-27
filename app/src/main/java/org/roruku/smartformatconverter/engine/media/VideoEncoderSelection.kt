package org.roruku.smartformatconverter.engine.media

/** Persisted encoder choice in the immutable job snapshot. */
object VideoEncoderSelection {
    const val AUTO = "auto"
    const val HARDWARE = "hardware"
    const val SOFTWARE = "software"
    private const val CODEC_PREFIX = "codec:"

    fun specific(name: String): String = CODEC_PREFIX + name

    fun isValid(value: String): Boolean =
        value in setOf(AUTO, HARDWARE, SOFTWARE) ||
            (value.startsWith(CODEC_PREFIX) &&
                value.removePrefix(CODEC_PREFIX).isNotBlank() &&
                value.length <= 256)

    fun matches(value: String, name: String, hardware: Boolean): Boolean =
        when (value) {
            AUTO -> true
            HARDWARE -> hardware
            SOFTWARE -> !hardware
            else -> value.startsWith(CODEC_PREFIX) &&
                name == value.removePrefix(CODEC_PREFIX)
        }

    fun displayName(value: String): String = when (value) {
        AUTO -> "Auto"
        HARDWARE -> "Hardware only"
        SOFTWARE -> "Software only"
        else -> value.removePrefix(CODEC_PREFIX)
    }
}
