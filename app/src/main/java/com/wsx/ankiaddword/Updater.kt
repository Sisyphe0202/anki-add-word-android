package com.wsx.ankiaddword

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：查 GitHub 最新 Release，比较版本号，新版则下载 APK 并拉起系统安装器。
 * 仓库是公开的，所以查询和下载都不需要 token。
 */
object Updater {
    private const val API =
        "https://api.github.com/repos/Sisyphe0202/anki-add-word-android/releases/latest"

    data class Release(val tag: String, val notes: String, val apkUrl: String)

    /** 查最新 Release；查不到或没有 apk 资源时返回 null */
    fun latest(): Release? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "AnkiAddWord")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10000
            readTimeout = 15000
        }
        if (conn.responseCode != 200) { conn.disconnect(); return null }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(body)
        val tag = j.optString("tag_name")
        val notes = j.optString("body")
        val assets = j.optJSONArray("assets") ?: return null
        var apk = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", true)) {
                apk = a.optString("browser_download_url"); break
            }
        }
        if (tag.isEmpty() || apk.isEmpty()) return null
        return Release(tag, notes, apk)
    }

    /** 远程 tag（如 v1.5）比本地 versionName（如 1.4）更高时返回 true */
    fun isNewer(tag: String, localVersion: String): Boolean {
        val r = tag.trimStart('v', 'V').trim()
        if (r.isEmpty()) return false
        return compareVersions(r, localVersion.trim()) > 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.'); val pb = b.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }

    /** 下载 APK 到 cacheDir 并拉起系统安装器（需用户允许“安装未知应用”）。 */
    fun downloadAndInstall(activity: Activity, url: String) {
        val out = File(activity.cacheDir, "update.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "AnkiAddWord")
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 60000
        }
        conn.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
        if (out.length() <= 0) error("下载内容为空")
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", out)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
