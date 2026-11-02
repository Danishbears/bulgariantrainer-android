package com.danishbears.bulgariantrainer.data

import java.text.Normalizer
import java.util.Locale

/** Java's Unicode White_Space set, explicit to behave consistently on Android and the JVM. */
internal object VocabularyText {
    private val whitespace = Regex(
        "[\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A" +
            "\\u2028\\u2029\\u202F\\u205F\\u3000]+",
    )

    fun clean(value: String): String = whitespace
        .replace(Normalizer.normalize(value, Normalizer.Form.NFC), " ")
        .trim { Character.isWhitespace(it) }

    fun key(value: String): String = clean(value).lowercase(Locale.ROOT)

    fun optional(value: String?): String? = value?.let(::clean)?.ifEmpty { null }

    fun searchPattern(value: String): String = "%" + key(value)
        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
}
