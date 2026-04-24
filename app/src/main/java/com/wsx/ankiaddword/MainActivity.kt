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

class MainActivity : AppCompatActivity() {

    private lateinit var etWord: TextInputEditText
    private lateinit var etKk: TextInputEditText
    private lateinit var acDeck: AutoCompleteTextView
    private lateinit var btnFetch: Button
    private lateinit var btnSave: Button
    private lateinit var btnClear: Button
    private lateinit var tvStatus: TextView

    private val ankiPerm = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private var bridge: AnkiBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etWord = findViewById(R.id.etWord)
        etKk = findViewById(R.id.etKk)
        acDeck = findViewById(R.id.acDeck)
        btnFetch = findViewById(R.id.btnFetch)
        btnSave = findViewById(R.id.btnSave)
        btnClear = findViewById(R.id.btnClear)
        tvStatus = findViewById(R.id.tvStatus)

        btnFetch.setOnClickListener { doFetch() }
        btnSave.setOnClickListener { doSave() }
        btnClear.setOnClickListener { doClear() }

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
        val decks = b.topLevelDecks()
        acDeck.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, decks))
        if (decks.isNotEmpty()) acDeck.setText(decks.first(), false)
    }

    private fun doFetch() {
        val word = etWord.text.toString().trim()
        if (word.isEmpty()) { status("先填单词", true); return }
        status("抓取中…")
        lifecycleScope.launch {
            val ipa = try { withContext(Dispatchers.IO) { YoudaoFetcher.fetchIpa(word) } }
                catch (e: Exception) { status("抓取失败: ${e.message}", true); return@launch }
            if (ipa.isEmpty()) { status("没查到 IPA，手动填吧", true); return@launch }
            val kk = IpaToKk.convert(ipa)
            etKk.setText(kk)
            status("IPA: $ipa  →  KK: $kk  （可手改）")
        }
    }

    private fun doSave() {
        val b = bridge ?: run { status("AnkiDroid 未就绪", true); return }
        val word = etWord.text.toString().trim()
        val kk = etKk.text.toString().trim()
        val top = acDeck.text.toString().trim()
        when {
            word.isEmpty() -> { status("缺单词", true); return }
            kk.isEmpty() -> { status("缺音标", true); return }
            top.isEmpty() -> { status("缺牌组", true); return }
        }
        status("保存中…")
        lifecycleScope.launch {
            val n = try { withContext(Dispatchers.IO) { b.addWord(word, kk, top) } }
                catch (e: Exception) { status("保存失败: ${e.message}", true); return@launch }
            when (n) {
                2 -> {
                    status("✓ $word 已存入 $top::问答 和 $top::填空")
                    etWord.text?.clear(); etKk.text?.clear(); etWord.requestFocus()
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
        etWord.text?.clear(); etKk.text?.clear()
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
