package dev.titan2.keys

import android.content.Context
import android.graphics.Paint

class Emoji(val text: String, val name: String)

class Category(val title: String, val icon: String, val items: List<Emoji>)

/**
 * Emoji list from assets/emoji.txt (generated from Unicode's emoji-test.txt): a line
 * without a tab starts a group ("#Smileys & Emotion"), other lines are "<emoji>\t<name>".
 */
object EmojiData {
    private val ICONS = mapOf(
        "Smileys & Emotion" to "😀",
        "People & Body" to "👋",
        "Animals & Nature" to "🐻",
        "Food & Drink" to "🍔",
        "Travel & Places" to "✈️",
        "Activities" to "⚽",
        "Objects" to "💡",
        "Symbols" to "🔣",
        "Flags" to "🏁",
    )

    @Volatile
    private var cached: List<Category>? = null

    fun categories(context: Context): List<Category> =
        cached ?: load(context).also { cached = it }

    /** Emoji whose name contains every word of [query]; names starting with the first word come first. */
    fun search(context: Context, query: String): List<Emoji> {
        val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return categories(context).asSequence()
            .flatMap { it.items }
            .filter { e -> words.all { e.name.contains(it) } }
            .sortedBy { e -> if (e.name.split(' ').any { it.startsWith(words[0]) }) 0 else 1 }
            .toList()
    }

    private fun load(context: Context): List<Category> {
        val paint = Paint() // used to skip emoji this phone's font can't draw
        val out = ArrayList<Category>()
        var title: String? = null
        var items = ArrayList<Emoji>()
        fun flush() {
            val t = title ?: return
            if (items.isNotEmpty()) out += Category(t, ICONS[t] ?: t.take(1), items)
        }
        context.assets.open("emoji.txt").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty()) continue
                val tab = line.indexOf('\t')
                if (tab < 0) {
                    flush()
                    title = line.removePrefix("#")
                    items = ArrayList()
                } else {
                    val emoji = line.substring(0, tab)
                    if (paint.hasGlyph(emoji)) items.add(Emoji(emoji, line.substring(tab + 1)))
                }
            }
        }
        flush()
        return out
    }
}

/** Most recently used emoji, newest first. */
object Recents {
    private const val MAX = 32

    fun get(context: Context): List<String> =
        prefs(context).getString("recent", "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun add(context: Context, emoji: String) {
        val list = (listOf(emoji) + get(context).filter { it != emoji }).take(MAX)
        prefs(context).edit().putString("recent", list.joinToString("\n")).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("emoji", Context.MODE_PRIVATE)
}
