package flir.android

import android.content.Context
import android.graphics.Color
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import com.facebook.react.uimanager.ThemedReactContext

class FlirView(context: ThemedReactContext) : FrameLayout(context) {
    private val textureView: TextureView

    init {
        textureView = TextureView(context)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(textureView)

        // Simple placeholder overlay to indicate native FLIR view
        val overlay = TextView(context)
        overlay.text = "FLIR Preview"
        overlay.setTextColor(Color.WHITE)
        overlay.setPadding(12, 12, 12, 12)
        addView(overlay)

        // Ensure SDK is initialized
        FlirManager.init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Let the centralized manager handle discovery and streaming and emit events
        try {
            FlirManager.startDiscoveryAndConnect(context as ThemedReactContext)
        } catch (ignored: Throwable) {}
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            FlirManager.stop()
        } catch (ignored: Throwable) {}
    }

    // Called by JS/native module to get latest frame cache file
    fun getLatestFramePath(): String? {
        return FlirManager.getLatestFramePath()
    }
}
