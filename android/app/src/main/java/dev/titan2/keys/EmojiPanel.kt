package dev.titan2.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The emoji panel: a search line, category tabs and a grid.
 *
 * Tapping an emoji inserts it and keeps the panel open. From the hardware keyboard,
 * typing searches by name, arrows (Sym+HJKL) move the highlight, Enter inserts it and
 * closes the panel, Tab (Sym+A) changes category, Esc (Sym+Q) clears/closes.
 */
@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val onPick: (emoji: String, fromKeyboard: Boolean) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val categories = EmojiData.categories(context)
    private var tabs: List<Category> = categories
    private var tab = 0
    private var query = ""
    private var items: List<Emoji> = emptyList()
    private var selected = -1

    private val header = TextView(context)
    private val tabStrip = LinearLayout(context)
    private val grid = GridView(context)
    private val adapter = Adapter()

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(color(R.color.panel_bg))

        header.textSize = 14f
        header.isSingleLine = true
        header.setPadding(dp(12), dp(6), dp(12), dp(4))
        addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        val tabScroller = HorizontalScrollView(context)
        tabScroller.isHorizontalScrollBarEnabled = false
        tabScroller.addView(tabStrip)
        addView(tabScroller, LinearLayout.LayoutParams(MATCH, WRAP))

        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = dp(46)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            items.getOrNull(position)?.let { onPick(it.text, false) }
        }
        val height = minOf(dp(240), resources.displayMetrics.heightPixels * 2 / 5)
        addView(grid, LinearLayout.LayoutParams(MATCH, height))

        reset()
    }

    /** Called each time the panel opens: reload recents and clear the search. */
    fun reset() {
        val recent = Recents.get(context)
        tabs = if (recent.isEmpty()) categories
        else listOf(Category("Recent", "🕘", recent.map { Emoji(it, "") })) + categories
        tab = 0
        query = ""
        tabStrip.removeAllViews()
        tabs.forEachIndexed { i, category ->
            val view = TextView(context)
            view.text = category.icon
            view.textSize = 20f
            view.setPadding(dp(10), dp(4), dp(10), dp(4))
            view.setOnClickListener {
                tab = i
                query = ""
                refresh()
            }
            tabStrip.addView(view)
        }
        refresh()
    }

    /** A hardware key while the panel is showing. Returns true if the panel used it. */
    fun onKey(event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> return escape()
            KeyEvent.KEYCODE_DEL -> {
                if (query.isEmpty()) return false // let Backspace reach the text field
                setQuery(query.dropLast(1))
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                val emoji = items.getOrNull(selected) ?: return false
                onPick(emoji.text, true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> return move(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return move(1)
            KeyEvent.KEYCODE_DPAD_UP -> return move(-columns())
            KeyEvent.KEYCODE_DPAD_DOWN -> return move(columns())
            KeyEvent.KEYCODE_TAB -> {
                if (query.isNotEmpty()) return false
                tab = (tab + if (event.isShiftPressed) tabs.size - 1 else 1) % tabs.size
                refresh()
                return true
            }
        }
        val ch = event.unicodeChar
        return when {
            ch == 0x1b -> escape() // Sym+Q types ESC in titan2-custom.kcm
            ch == ' '.code && query.isNotEmpty() -> { setQuery("$query "); true }
            ch > 0 && Character.isLetterOrDigit(ch) -> { setQuery(query + ch.toChar().lowercaseChar()); true }
            else -> false
        }
    }

    private fun escape(): Boolean {
        if (query.isNotEmpty()) setQuery("") else onClose()
        return true
    }

    private fun setQuery(q: String) {
        query = q
        refresh()
    }

    private fun refresh() {
        items = if (query.isEmpty()) tabs[tab].items else EmojiData.search(context, query)
        selected = if (query.isEmpty()) -1 else 0 // while searching, Enter takes the top hit
        for (i in 0 until tabStrip.childCount) {
            val on = query.isEmpty() && i == tab
            tabStrip.getChildAt(i).setBackgroundColor(if (on) color(R.color.tab_selected) else Color.TRANSPARENT)
        }
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        updateHeader()
    }

    private fun move(delta: Int): Boolean {
        if (items.isEmpty()) return false
        selected = if (selected < 0) 0 else (selected + delta).coerceIn(0, items.lastIndex)
        adapter.notifyDataSetChanged()
        grid.smoothScrollToPosition(selected)
        updateHeader()
        return true
    }

    private fun updateHeader() {
        val pick = items.getOrNull(selected)
        header.text = when {
            query.isEmpty() && pick == null -> "${tabs[tab].title} · type to search"
            query.isEmpty() -> "${tabs[tab].title} · Enter inserts ${pick!!.text}"
            pick == null -> "🔍 $query · no matches"
            else -> "🔍 $query · Enter inserts ${pick.text}"
        }
        header.setTextColor(color(if (query.isEmpty() && pick == null) R.color.panel_hint else R.color.panel_text))
    }

    private fun columns() = grid.numColumns.takeIf { it > 0 } ?: 1

    private fun color(id: Int) = context.getColor(id)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView as? TextView ?: TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                layoutParams = AbsListView.LayoutParams(MATCH, dp(48))
            }
            view.text = items[position].text
            view.setBackgroundColor(if (position == selected) color(R.color.cell_selected) else Color.TRANSPARENT)
            return view
        }
    }
}
