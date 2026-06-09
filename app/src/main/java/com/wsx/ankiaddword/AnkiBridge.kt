package com.wsx.ankiaddword

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ichi2.anki.api.AddContentApi
import java.io.File

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
     * 把本地音频文件加入 AnkiDroid 媒体库，返回库内文件名（如 "give_up.mp3"）。
     * 任何环节失败都抛出带原因的异常，方便上层显示给用户排查。
     */
    fun addMedia(file: File, preferredName: String): String {
        val authority = "${ctx.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(ctx, authority, file)
        val pkg = AddContentApi.getAnkiDroidPackageName(ctx)
            ?: error("找不到 AnkiDroid")
        ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val cv = ContentValues().apply {
            put("file_uri", uri.toString())
            put("preferred_name", preferredName)
        }
        val mediaUri = Uri.parse("content://com.ichi2.anki.flashcards/media")
        val ret = ctx.contentResolver.insert(mediaUri, cv)
            ?: error("媒体写入返回空")
        return ret.lastPathSegment ?: error("媒体返回无文件名")
    }

    /**
     * 写入两条笔记（单词与词组通用）。把发音放进模型自带的「音频」字段，
     * 中文意思+音标放「背面」/cloze 文字。kk / meaning / sound 任意可为空。
     * 返回成功写入的笔记数（0/1/2）。
     */
    fun addWord(word: String, kk: String, meaning: String, sound: String, source: String, topDeck: String): Int {
        var ok = 0
        val soundTag = if (sound.isNotEmpty()) "[sound:$sound]" else ""
        val badge = AudioFetcher.badge(source)

        ensureModel(
            name = "问答题",
            fields = arrayOf("正面", "背面", "音频", "音源", "图片"),
            cards = arrayOf("卡片 1"),
            qfmt = arrayOf("{{音频}}\n{{正面}}"),
            afmt = arrayOf("{{正面}}\n{{音频}}\n<hr id=answer>\n{{背面}}")
        )?.let { (mid, fields) ->
            val back = listOf(meaning, kk).filter { it.isNotEmpty() }.joinToString("<br>")
            val map = buildFields(
                fields,
                mapOf("正面" to word, "背面" to back, "音频" to soundTag, "音源" to badge)
            )
            val did = ensureDeck("$topDeck::问答")
            if (api.addNote(mid, did, map, setOf(topDeck)) != null) ok++
        }

        // 听写卡：正面只播放发音（{{音频}}）+ 输入框（{{type:单词}}），不显示任何文字提示。
        // 听完声音把词/词组打进去，背面对照正确答案（打对绿色、打错红色）。
        ensureModel(
            name = "听音拼词",
            fields = arrayOf("单词", "释义", "音频", "音源", "图片"),
            cards = arrayOf("听写"),
            qfmt = arrayOf("{{音频}}<br>{{type:单词}}"),
            afmt = arrayOf("{{音频}}<br>{{type:单词}}\n<hr id=answer>\n<b>{{单词}}</b><br>{{释义}}<br>{{音源}}")
        )?.let { (mid, fields) ->
            val expl = listOf(meaning, kk).filter { it.isNotEmpty() }.joinToString("<br>")
            val map = buildFields(
                fields,
                mapOf("单词" to word, "释义" to expl, "音频" to soundTag, "音源" to badge)
            )
            val did = ensureDeck("$topDeck::填空")
            if (api.addNote(mid, did, map, setOf(topDeck)) != null) ok++
        }
        return ok
    }

    private fun buildFields(order: Array<String>, values: Map<String, String>): Array<String> =
        order.map { values[it] ?: "" }.toTypedArray()
}
