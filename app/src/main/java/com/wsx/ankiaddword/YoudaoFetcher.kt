package com.wsx.ankiaddword

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 查询结果：IPA 音标 + 中文意思（词组也适用） */
data class WordInfo(val ipa: String, val meaning: String)

/**
 * 从有道 jsonapi 抓 IPA 音标 + 中文意思，并能下载发音 mp3。
 * 单词通常是真人录音；词组多为合成音。
 */
object YoudaoFetcher {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** 查 IPA(美音优先) + 中文意思；查不到的项返回空串 */
    fun lookup(word: String): WordInfo {
        val url = URL("https://dict.youdao.com/jsonapi?q=${enc(word)}")
        val body = open(url).inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(body)
        val ec = root.optJSONObject("ec") ?: return WordInfo("", "")
        val arr = ec.optJSONArray("word") ?: return WordInfo("", "")
        if (arr.length() == 0) return WordInfo("", "")
        val w0 = arr.getJSONObject(0)
        val us = w0.optString("usphone").trim()
        val uk = w0.optString("ukphone").trim()
        // 词组：有道的美音常把分词连写（如 "hæv ərest"），转换会串成乱码；
        // 优先选带空格、分词清晰的那版（通常是英音）。单词仍用美音优先。
        val ipa = if (word.trim().contains(' ')) {
            listOf(uk, us).firstOrNull { it.contains(' ') } ?: us.ifEmpty { uk }
        } else {
            us.ifEmpty { uk }
        }
        val meaning = parseMeaning(w0.optJSONArray("trs"))
        return WordInfo(ipa, meaning)
    }

    /**
     * 解析 ec.word[0].trs。
     * 单词义形如 "n. 苹果"；词组义形如 "放弃：指放弃对某物的控制…"，
     * 后者取冒号前的简短义。最多取 3 条、去重。
     */
    private fun parseMeaning(trs: JSONArray?): String {
        if (trs == null) return ""
        val outs = LinkedHashSet<String>()
        loop@ for (i in 0 until trs.length()) {
            val tr = trs.optJSONObject(i)?.optJSONArray("tr") ?: continue
            for (j in 0 until tr.length()) {
                val l = tr.optJSONObject(j)?.optJSONObject("l") ?: continue
                val iarr = l.optJSONArray("i") ?: continue
                for (k in 0 until iarr.length()) {
                    var s = iarr.optString(k).trim()
                    if (s.isEmpty()) continue
                    val cut = s.indexOf('：')
                    if (cut > 0) s = s.substring(0, cut).trim()
                    outs.add(s)
                    if (outs.size >= 3) break@loop
                }
            }
        }
        return outs.joinToString("；")
    }

    private fun open(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 8000
            readTimeout = 12000
        }
}

/**
 * 发音抓取：优先走自家服务器（单词剑桥/韦氏真人、词组 Edge 高质量合成），
 * 失败回退有道。返回发音来源标签（cambridge/mw/tts/youdao），失败返回 ""。
 */
object AudioFetcher {
    private const val SERVER = "http://38.143.19.180:8092/audio"
    private const val TOKEN = "tCyrij3EPTWDaRbx"

    /** 最近一次 fetch 失败的真实原因；成功时为空。用于界面排查。 */
    var lastError: String = ""
        private set

    fun fetch(word: String, out: File): String {
        val enc = URLEncoder.encode(word, "UTF-8")
        val errs = StringBuilder()
        // 1) 自家服务器
        try {
            val conn = open(URL("$SERVER?q=$enc&k=$TOKEN"))
            val code = conn.responseCode
            if (code == 200) {
                val src = conn.getHeaderField("X-Audio-Source") ?: "tts"
                conn.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
                if (out.length() > 0) { lastError = ""; return src }
                errs.append("服务器:空文件; ")
            } else {
                errs.append("服务器:HTTP$code; ")
                conn.disconnect()
            }
        } catch (e: Exception) { errs.append("服务器:${e.javaClass.simpleName}:${e.message}; ") }
        // 2) 有道兜底
        try {
            open(URL("https://dict.youdao.com/dictvoice?audio=$enc&type=2"))
                .inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
            if (out.length() > 0) { lastError = ""; return "youdao" }
            errs.append("有道:空文件; ")
        } catch (e: Exception) { errs.append("有道:${e.javaClass.simpleName}:${e.message}; ") }
        lastError = errs.toString().trim()
        return ""
    }

    /** 来源标签 -> 卡片「音源」字段的徽章 */
    fun badge(src: String): String = when (src) {
        "cambridge" -> "🎙️剑桥"
        "mw" -> "🎙️韦氏"
        "youdao" -> "🔊有道"
        "tts" -> "🔊合成"
        else -> ""
    }

    private fun open(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 8000
            readTimeout = 15000
        }
}
