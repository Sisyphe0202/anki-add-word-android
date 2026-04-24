package com.wsx.ankiaddword

/**
 * 粗略的 IPA → KK 转换器。覆盖 ~80% 常见词，复杂词留给用户手改。
 */
object IpaToKk {
    private const val SENT = ""
    private val rules = listOf(
        "ˈ" to "ˋ", "ˌ" to "ˏ",
        "eɪ" to SENT,       // 占位，避免 e → ɛ 把它吃了
        "oʊ" to "o", "əʊ" to "o",
        "iː" to "i", "uː" to "u", "ɑː" to "ɑ", "ɔː" to "ɔ",
        "ɜːr" to "ɝ", "ɜː" to "ɝ", "ər" to "ɚ",
        "ː" to "",
        "e" to "ɛ",
        SENT to "e"
    )

    fun convert(ipa: String): String {
        if (ipa.isEmpty()) return ""
        var s = ipa
        for ((a, b) in rules) s = s.replace(a, b)
        return s
    }
}
