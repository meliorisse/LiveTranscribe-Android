package com.charles.livecaptionn.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.livecaptionn.overlay.OverlayFontCatalog
import com.charles.livecaptionn.overlay.OverlayThemeCatalog
import com.charles.livecaptionn.overlay.OverlayUiState
import com.charles.livecaptionn.overlay.computeOverlayTextDisplay

/** Native TextViews use the same SP scaling, typeface, and text resolution as the overlay. */
@Composable
internal fun OverlayPreview(state: OverlayUiState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().height(200.dp),
        factory = { context ->
            ScrollView(context).apply {
                isFillViewport = true
                clipToOutline = true
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (12 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    addView(TextView(context))
                    addView(View(context), LinearLayout.LayoutParams(-1, (resources.displayMetrics.density).toInt()).apply {
                        topMargin = padding
                        bottomMargin = padding
                    })
                    addView(TextView(context))
                })
            }
        },
        update = { scroll ->
            val theme = OverlayThemeCatalog.find(state.themeId)
            val font = OverlayFontCatalog.find(state.fontId)
            val display = computeOverlayTextDisplay(state)
            val r = Color.red(theme.textRgb)
            val g = Color.green(theme.textRgb)
            val b = Color.blue(theme.textRgb)
            scroll.background = GradientDrawable().apply {
                cornerRadius = 12 * scroll.resources.displayMetrics.density
                setColor(Color.argb(
                    (state.opacity.coerceIn(0.5f, 1f) * 255).toInt(),
                    Color.red(theme.backgroundRgb), Color.green(theme.backgroundRgb), Color.blue(theme.backgroundRgb)
                ))
            }
            val column = scroll.getChildAt(0) as LinearLayout
            (column.getChildAt(0) as TextView).apply {
                text = display.originalText
                textSize = display.originalTextSizeSp
                typeface = Typeface.create(font.typeface, Typeface.ITALIC)
                setTextColor(Color.argb(190, r, g, b))
                visibility = if (display.showOriginal) View.VISIBLE else View.GONE
            }
            column.getChildAt(1).apply {
                setBackgroundColor(Color.argb(45, r, g, b))
                visibility = if (display.showOriginal) View.VISIBLE else View.GONE
            }
            (column.getChildAt(2) as TextView).apply {
                text = display.translatedText
                textSize = display.translatedTextSizeSp
                typeface = font.typeface
                setTextColor(theme.textRgb)
            }
        }
    )
}
