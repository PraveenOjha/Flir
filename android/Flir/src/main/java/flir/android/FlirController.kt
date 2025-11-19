package flir.android

object FlirController {
    @JvmStatic
    var cameraHandler: CameraHandler? = null

    @JvmStatic
    fun getTemperatureAt(x: Int, y: Int): Double? {
        return cameraHandler?.getTemperatureAt(x, y)
    }
}
