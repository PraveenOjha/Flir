package flir.android

object FlirStatus {
    @JvmStatic
    var flirConnected: Boolean = false

    @JvmStatic
    var flirStreaming: Boolean = false

    @JvmStatic
    var latestFramePath: String? = null
}
