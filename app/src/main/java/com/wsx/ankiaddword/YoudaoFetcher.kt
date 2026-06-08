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
        val ipa = w0.optString("usphone").ifEmpty { w0.optString("ukphone") }.trim()
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

    /** 下载有道发音(美音 type=2)到文件；成功返回 true */
    fun downloadAudio(word: String, out: File): Boolean {
        return try {
            val url = URL("https://dict.youdao.com/dictvoice?audio=${enc(word)}&type=2")
            open(url).inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun open(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 8000
            readTimeout = 8000
        }
}
