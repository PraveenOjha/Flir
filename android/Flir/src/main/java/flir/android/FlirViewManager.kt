package flir.android

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import android.view.View

class FlirViewManager : SimpleViewManager<View>() {
    override fun getName(): String = "FLIRCameraView"

    override fun createViewInstance(reactContext: ThemedReactContext): View {
        return FlirView(reactContext)
    }
}
