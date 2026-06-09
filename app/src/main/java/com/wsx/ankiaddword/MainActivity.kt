package com.wsx.ankiaddword

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etWord: TextInputEditText
    private lateinit var etKk: TextInputEditText
    private lateinit var etMeaning: TextInputEditText
    private lateinit var acDeck: AutoCompleteTextView
    private lateinit var btnFetch: Button
    private lateinit var btnSave: Button
    private lateinit var btnClear: Button
    private lateinit var btnRefreshDecks: Button
    private lateinit var tvStatus: TextView

    private val ankiPerm = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private var bridge: AnkiBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etWord = findViewById(R.id.etWord)
        etKk = findViewById(R.id.etKk)
        etMeaning = findViewById(R.id.etMeaning)
        acDeck = findViewById(R.id.acDeck)
        btnFetch = findViewById(R.id.btnFetch)
        btnSave = findViewById(R.id.btnSave)
        btnClear = findViewById(R.id.btnClear)
        btnRefreshDecks = findViewById(R.id.btnRefreshDecks)
        tvStatus = findViewById(R.id.tvStatus)

        btnFetch.setOnClickListener { doFetch() }
        btnSave.setOnClickListener { doSave() }
        btnClear.setOnClickListener { doClear() }
        btnRefreshDecks.setOnClickListener { refreshDecks() }

        requestAnkiPermissionIfNeeded()
    }

    private fun requestAnkiPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, ankiPerm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(ankiPerm), 1)
        } else {
            initBridge()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            initBridge()
        } else {
            status("未授权访问 AnkiDroid；请在设置里允许本应用，再重启", true)
        }
    }

    private fun initBridge() {
        val b = AnkiBridge(this)
        if (!b.isAvailable()) {
            status("没检测到 AnkiDroid，请先装 AnkiDroid", true); return
        }
        bridge = b
        refreshDecks()
    }

    private fun refreshDecks() {
        val b = bridge ?: return
        val decks = try { b.topLevelDecks() } catch (e: Exception) {
            status("读取牌组失败: ${e.message}", true); return
        }
        acDeck.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, decks))
        if (decks.isEmpty()) {
            status("未读到牌组，请确认 AnkiDroid 已授权本应用，可手动填牌组名", true)
        } else {
            if (acDeck.text.isNullOrEmpty()) acDeck.setText(decks.first(), false)
            status("读到 ${decks.size} 个牌组")
        }
    }

    private fun doFetch() {
        val word = etWord.text.toString().trim()
        if (word.isEmpty()) { status("先填单词", true); return }
        status("抓取中…")
        lifecycleScope.launch {
            val info = try { withContext(Dispatchers.IO) { YoudaoFetcher.lookup(word) } }
                catch (e: Exception) { status("抓取失败: ${e.message}", true); return@launch }
            val kk = if (info.ipa.isNotEmpty()) IpaToKk.convert(info.ipa) else ""
            if (kk.isNotEmpty()) etKk.setText(kk)
            if (info.meaning.isNotEmpty()) etMeaning.setText(info.meaning)
            status(
                when {
                    kk.isEmpty() && info.meaning.isEmpty() -> "没查到，手动填吧"
                    kk.isEmpty() -> "意思: ${info.meaning}  （词组没音标，可手填）"
                    else -> "KK: $kk   意思: ${info.meaning}  （可手改）"
                },
                warn = kk.isEmpty() && info.meaning.isEmpty()
            )
        }
    }

    private fun doSave() {
        val b = bridge ?: run { status("AnkiDroid 未就绪", true); return }
        val word = etWord.text.toString().trim()
        val kk = etKk.text.toString().trim()
        val meaning = etMeaning.text.toString().trim()
        val top = acDeck.text.toString().trim()
        when {
            word.isEmpty() -> { status("缺单词", true); return }
            kk.isEmpty() && meaning.isEmpty() -> { status("缺音标和意思（至少填一个）", true); return }
            top.isEmpty() -> { status("缺牌组", true); return }
        }
        status("保存中…")
        lifecycleScope.launch {
            // 取发音(服务器真人/合成,有道兜底)并加入媒体库；失败记原因但不阻断
            var sound = ""
            var source = ""
            var audioErr = ""
            withContext(Dispatchers.IO) {
                try {
                    val safe = word.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "word" }
                    val f = File(cacheDir, "yd_$safe.mp3")
                    source = AudioFetcher.fetch(word, f)
                    if (source.isEmpty()) {
                        audioErr = "发音获取失败(网络?)"
                    } else {
                        sound = b.addMedia(f, "yd_$safe")
                    }
                } catch (e: Exception) {
                    audioErr = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
            val n = try { withContext(Dispatchers.IO) { b.addWord(word, kk, meaning, sound, source, top) } }
                catch (e: Exception) { status("保存失败: ${e.message}", true); return@launch }
            val audioNote = if (sound.isNotEmpty()) "（${AudioFetcher.badge(source)}）" else "（无发音: $audioErr）"
            when (n) {
                2 -> {
                    status("✓ $word 已存入 $top::问答 和 $top::填空 $audioNote")
                    etWord.text?.clear(); etKk.text?.clear(); etMeaning.text?.clear(); etWord.requestFocus()
                }
                1 -> status("⚠ 只写入 1 条（另一个模型可能缺失或重复）", true)
                else -> status("✗ 没写入任何笔记（模型缺失？）", true)
            }
            b.topLevelDecks().let {
                acDeck.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, it))
            }
        }
    }

    private fun doClear() {
        etWord.text?.clear(); etKk.text?.clear(); etMeaning.text?.clear()
        status("")
        etWord.requestFocus()
    }

    private fun status(msg: String, warn: Boolean = false) {
        tvStatus.text = msg
        tvStatus.setTextColor(
            if (warn) 0xFFD32F2F.toInt() else 0xFF388E3C.toInt()
        )
    }
}
