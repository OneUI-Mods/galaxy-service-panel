package dev.ryuya.galaxyservicepanel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.textview.MaterialTextView
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.regex.Pattern
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* The same presentation is used by the live hook and the explicitly labelled sample preview. */
class PanelView(context: Context, selected: IntConsumer, failed: Consumer<Throwable?>) :
    LinearLayout(context) {
    private val list: ListView
    private val adapter: PanelView.Rows = Rows()
    private val summary: MaterialTextView
    private val note: MaterialTextView
    private val selected: IntConsumer
    private val failed: Consumer<Throwable?>
    private var rows: MutableList<DiagnosticDocument.Row?>? =
        java.util.List.of<DiagnosticDocument.Row?>()
    private var original: Boolean = false
    private var font: Int = 14

    init {
        this.selected = selected
        this.failed = failed
        setOrientation(LinearLayout.VERTICAL)
        setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        summary = text(context.getString(R.string.waiting_data), 20, true)
        summary.setPadding(dp(24), dp(18), dp(24), dp(4))
        addView(summary)
        note = text(context.getString(R.string.live_values), 12, false)
        note.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        note.setPadding(dp(24), 0, dp(24), dp(14))
        addView(note)
        val tabs = TabLayout(context)
        tabs.setTabIndicatorFullWidth(false)
        tabs.addTab(tabs.newTab().setText(R.string.formatted))
        tabs.addTab(tabs.newTab().setText(R.string.original_text))
        tabs.addOnTabSelectedListener(
            object : OnTabSelectedListener {
                override fun onTabSelected(tab: Tab) {
                    original = tab.getPosition() == 1
                    adapter.notifyDataSetChanged()
                }

                override fun onTabUnselected(tab: Tab?) {}

                override fun onTabReselected(tab: Tab?) {}
            }
        )
        addView(tabs, android.widget.LinearLayout.LayoutParams(-1, -2))
        list = ListView(context)
        list.setId(R.id.panel_list)
        list.setDivider(null)
        list.setPadding(dp(16), dp(12), dp(16), dp(24))
        list.setClipToPadding(false)
        list.setAdapter(adapter)
        list.setOnItemClickListener(
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                if (position < rows!!.size && !rows!!.get(position)!!.menuKey!!.isEmpty())
                    selected.accept(rows!!.get(position)!!.sourceIndex)
            }
        )
        list.setOnItemLongClickListener(
            OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ->
                if (position >= rows!!.size) return@OnItemLongClickListener false
                try {
                    val row = rows!!.get(position)
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.source_line, row!!.sourceIndex + 1))
                        .setMessage(row!!.raw)
                        .setPositiveButton(
                            R.string.copy,
                            OnClickListener { dialog: DialogInterface?, which: Int ->
                                context
                                    .getSystemService<ClipboardManager?>(
                                        ClipboardManager::class.java
                                    )
                                    .setPrimaryClip(
                                        ClipData.newPlainText("Service Mode", row!!.raw)
                                    )
                            },
                        )
                        .setNegativeButton(R.string.close, null)
                        .show()
                } catch (error: Throwable) {
                    failed.accept(error)
                }
                true
            }
        )
        addView(list, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun sampleMode() {
        note.setText(R.string.preview_note)
    }

    fun update(source: MutableList<String?>?, textSize: Int) {
        val next = DiagnosticDocument.parse(source!!)
        val nextFont = max(12, min(20, textSize))
        if (rows == next && font == nextFont) return
        val first = list.getFirstVisiblePosition()
        val anchor: Int = if (first < rows!!.size) rows!!.get(first)!!.sourceIndex else -1
        val offset: Int = if (list.getChildCount() == 0) 0 else list.getChildAt(0).getTop()
        val shapeChanged: Boolean = rows!!.size != next!!.size
        rows = next
        font = nextFont
        val status = DiagnosticDocument.summary(rows!!)
        summary.setText(
            if (status!!.isEmpty())
                getContext()
                    .getString(
                        if (rows!!.isEmpty()) R.string.waiting_data
                        else if (rows!!.get(0)!!.section === DiagnosticDocument.Section.SELECTION)
                            R.string.selection
                        else R.string.preview_title
                    )
            else status
        )
        adapter.notifyDataSetChanged()
        if (shapeChanged && anchor >= 0)
            for (i in rows!!.indices) if (rows!!.get(i)!!.sourceIndex == anchor) {
                list.setSelectionFromTop(i, offset)
                break
            }
    }

    private inner class Rows : BaseAdapter() {
        override fun getCount(): Int {
            return rows!!.size
        }

        override fun getItem(position: Int): Any? {
            return rows!!.get(position)
        }

        override fun getItemId(position: Int): Long {
            return rows!!.get(position)!!.sourceIndex.toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun areAllItemsEnabled(): Boolean {
            return true
        }

        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            try {
                return rowView(position)
            } catch (error: Throwable) {
                // A render exception must not escape into the phone process layout traversal.
                failed.accept(error)
                val fallback = TextView(getContext())
                fallback.setText(rows!!.get(position)!!.raw)
                return fallback
            }
        }
    }

    private fun rowView(position: Int): View {
        val row = rows!!.get(position)
        val start: Boolean = position == 0 || rows!!.get(position - 1)!!.section !== row!!.section
        val end: Boolean =
            position == rows!!.size - 1 || rows!!.get(position + 1)!!.section !== row!!.section
        val result = vertical()
        if (start && !original) {
            val heading =
                text(getContext().getString(PanelView.sectionTitle(row!!.section!!)), 12, true)
            heading.setTextColor(color(androidx.appcompat.R.attr.colorPrimary))
            heading.setPadding(dp(8), dp(if (position == 0) 4 else 20), dp(8), dp(10))
            result.addView(heading)
        }
        val card = MaterialCardView(getContext())
        card.setCardElevation(0f)
        card.setStrokeWidth(0)
        card.setCardBackgroundColor(
            color(com.google.android.material.R.attr.colorSurfaceContainerLow)
        )
        val top = (if (start || original) dp(20) else 0).toFloat()
        val bottom = (if (end || original) dp(20) else 0).toFloat()
        card.setShapeAppearanceModel(
            card
                .getShapeAppearanceModel()
                .toBuilder()
                .setTopLeftCornerSize(top)
                .setTopRightCornerSize(top)
                .setBottomLeftCornerSize(bottom)
                .setBottomRightCornerSize(bottom)
                .build()
        )
        val body = vertical()
        body.setPadding(dp(20), dp(14), dp(20), dp(14))
        if (original) {
            val source =
                text(getContext().getString(R.string.source_line, row!!.sourceIndex + 1), 11, false)
            source.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            body.addView(source)
            val raw = text(row!!.raw, font, false)
            raw.setTypeface(Typeface.MONOSPACE)
            raw.setPadding(0, dp(4), 0, 0)
            body.addView(raw)
        } else if (!row!!.menuKey!!.isEmpty()) {
            val menu = LinearLayout(getContext())
            menu.setGravity(Gravity.CENTER_VERTICAL)
            val number = text(row!!.menuKey, 18, true)
            number.setTextColor(color(androidx.appcompat.R.attr.colorPrimary))
            menu.addView(number, android.widget.LinearLayout.LayoutParams(dp(40), -2))
            val label = text(menuTitle(row!!.text), font + 2, true)
            menu.addView(label, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            menu.addView(text("›", 24, false))
            body.setMinimumHeight(dp(68))
            body.setGravity(Gravity.CENTER_VERTICAL)
            body.addView(menu)
        } else if (row!!.values!!.isEmpty()) {
            body.addView(
                text(
                    if (row!!.text.equals("BASIC INFORMATION"))
                        getContext().getString(R.string.basic_info)
                    else row!!.text,
                    font,
                    false,
                )
            )
        } else if (row!!.values.size > 1) {
            val metrics = LinearLayout(getContext())
            for (value in row!!.values!!) {
                val cell = vertical()
                val name = text(value!!.label, 11, false)
                name.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
                cell.addView(name)
                val amount = text(value!!.text!!.replaceFirst(",\\s*$", ""), font + 2, true)
                amount.setPadding(0, dp(3), dp(6), 0)
                amount.setFontFeatureSettings("tnum")
                cell.addView(amount)
                metrics.addView(cell, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            }
            body.addView(metrics)
        } else {
            val value = row!!.values!!.get(0)
            val pair = LinearLayout(getContext())
            pair.setGravity(Gravity.CENTER_VERTICAL)
            val label = text(value!!.label, font - 1, false)
            label.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            label.setPadding(0, 0, dp(12), 0)
            pair.addView(label, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            val amount = text(value!!.text, font, true)
            amount.setFontFeatureSettings("tnum")
            amount.setGravity(Gravity.END)
            pair.addView(amount, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(pair)
        }
        card.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
        val size = android.widget.LinearLayout.LayoutParams(-1, -2)
        size.bottomMargin = dp(if (original) 8 else 1)
        result.addView(card, size)
        return result
    }

    private fun menuTitle(text: String?): String? {
        val matcher = Pattern.compile("SIM([0-9]+) INFORMATION").matcher(text)
        return if (matcher.matches()) getContext().getString(R.string.sim_info, matcher.group(1))
        else text
    }

    private fun vertical(): LinearLayout {
        val result = LinearLayout(getContext())
        result.setOrientation(LinearLayout.VERTICAL)
        return result
    }

    private fun text(value: String?, sp: Int, strong: Boolean): MaterialTextView {
        val view = MaterialTextView(getContext())
        view.setText(value)
        view.setTextSize(sp.toFloat())
        view.setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
        if (strong) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        view.setLineSpacing(dp(2).toFloat(), 1f)
        return view
    }

    private fun color(attr: Int): Int {
        return MaterialColors.getColor(this, attr)
    }

    private fun dp(value: Int): Int {
        return Math.round(value * getResources().getDisplayMetrics().density)
    }

    companion object {
        @JvmStatic
        private fun sectionTitle(section: DiagnosticDocument.Section): Int {
            return when (section) {
                DiagnosticDocument.Section.SELECTION -> R.string.selection
                DiagnosticDocument.Section.CONNECTION -> R.string.connection
                DiagnosticDocument.Section.LTE -> R.string.lte
                DiagnosticDocument.Section.NR -> R.string.nr
                DiagnosticDocument.Section.DETAILS -> R.string.details
            }
        }
    }
}
