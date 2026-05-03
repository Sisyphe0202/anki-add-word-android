package com.wsx.ankiaddword

import android.content.Context
import com.ichi2.anki.api.AddContentApi

/**
 * 封装对 AnkiDroid Content Provider 的访问。
 * 需要运行 AnkiDroid 的用户首次授权 READ_WRITE_DATABASE 权限。
 */
class AnkiBridge(context: Context) {
    private val ctx = context.applicationContext
    private val api = AddContentApi(ctx)

    fun isAvailable(): Boolean =
        AddContentApi.getAnkiDroidPackageName(ctx) != null

    /** 顶级牌组名列表（仅父层，子牌组会被合并） */
    fun topLevelDecks(): List<String> {
        val decks = api.deckList ?: return emptyList()
        val tops = decks.values.map { it.substringBefore("::") }.toSortedSet()
        tops.remove("Default")
        tops.remove("系统默认")
        return tops.toList()
    }

    /** 返回 (modelId, fieldNames) 或 null */
    private fun findModel(name: String): Pair<Long, Array<String>>? {
        val models = api.modelList ?: return null
        val mid = models.entries.firstOrNull { it.value == name }?.key ?: return null
        val fields = api.getFieldList(mid) ?: return null
        return mid to fields
    }

    /** 找不到则自动建模型，返回 (modelId, fieldNames) 或 null */
    private fun ensureModel(
        name: String,
        fields: Array<String>,
        cards: Array<String>,
        qfmt: Array<String>,
        afmt: Array<String>
    ): Pair<Long, Array<String>>? {
        findModel(name)?.let { return it }
        val mid = api.addNewCustomModel(name, fields, cards, qfmt, afmt, null, null, null)
            ?: return null
        return mid to fields
    }

    /** 找或建牌组，返回 deckId；建牌失败时抛异常 */
    private fun ensureDeck(name: String): Long {
        val decks = api.deckList ?: emptyMap()
        val existing = decks.entries.firstOrNull { it.value == name }?.key
        if (existing != null) return existing
        return api.addNewDeck(name)
            ?: error("无法创建牌组: $name")
    }

    /**
     * 写入两条笔记:
     *   - 问答题  (正面=word, 背面=kk)  -> topDeck::问答
     *   - 填空题+音频 (文字=kk + cloze, 单词=word) -> topDeck::填空
     * 返回成功写入的笔记数（0/1/2）
     */
    fun addWord(word: String, kk: String, topDeck: String): Int {
        var ok = 0

        ensureModel(
            name = "问答题",
            fields = arrayOf("正面", "背面"),
            cards = arrayOf("卡片"),
            qfmt = arrayOf("{{正面}}"),
            afmt = arrayOf("{{FrontSide}}<hr id=answer>{{背面}}")
        )?.let { (mid, fields) ->
            val map = buildFields(fields, mapOf("正面" to word, "背面" to kk))
            val did = ensureDeck("$topDeck::问答")
            if (api.addNote(mid, did, map, setOf(topDeck)) != null) ok++
        }

        ensureModel(
            name = "填空题+音频",
            fields = arrayOf("文字", "单词", "背面额外"),
            cards = arrayOf("填空"),
            qfmt = arrayOf("{{cloze:文字}}"),
            afmt = arrayOf("{{cloze:文字}}<br>{{单词}}<br>{{背面额外}}")
        )?.let { (mid, fields) ->
            val clozeText = "$kk ─ {{c1::$word}}"
            val map = buildFields(fields, mapOf("文字" to clozeText, "单词" to word, "背面额外" to ""))
            val did = ensureDeck("$topDeck::填空")
            if (api.addNote(mid, did, map, setOf(topDeck)) != null) ok++
        }
        return ok
    }

    private fun buildFields(order: Array<String>, values: Map<String, String>): Array<String> =
        order.map { values[it] ?: "" }.toTypedArray()
}
