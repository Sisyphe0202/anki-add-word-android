package com.wsx.ankiaddword

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 从有道 jsonapi 抓英文单词的 IPA 音标。
 */
object YoudaoFetcher {
    fun fetchIpa(word: String): String {
        val encoded = URLEncoder.encode(word, "UTF-8")
        val url = URL("https://dict.youdao.com/jsonapi?q=$encoded")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 8000
            readTimeout = 8000
        }
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.let { body ->
            val root = JSONObject(body)
            val simple = root.optJSONObject("simple") ?: return ""
            val wordArr = simple.optJSONArray("word") ?: return ""
            if (wordArr.length() == 0) return ""
            val first = wordArr.getJSONObject(0)
            val ipa = first.optString("usphone").ifEmpty { first.optString("ukphone") }
            ipa.split(";").firstOrNull()?.trim().orEmpty()
        }
    }
}
