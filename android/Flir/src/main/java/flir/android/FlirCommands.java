package flir.android;

import androidx.annotation.Nullable;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;

public final class FlirCommands {

    private FlirCommands() {
        // No instances
    }

    /**
     * Command 59: selectFlirDevice - Select FLIR device by ID
     * Args: [deviceId: string]
     */
    public static void handleSelectFlirDevice(Object root, WritableMap resp, @Nullable ReadableArray args) {
        try {
            if (args == null || args.size() < 1) {
                resp.putString("status", "error");
                resp.putString("message", "deviceId required");
                return;
            }

            String deviceId = args.getString(0);
            android.util.Log.i("CameraCommand", "[FLIR] Selecting device: " + deviceId);

            // Get FlirManager instance and select device
            FlirManager flirManager = FlirManager.INSTANCE;
            if (flirManager == null) {
                resp.putString("status", "error");
                resp.putString("message", "FlirManager not initialized");
                return;
            }

            // Select device by ID (triggers connection)
            flirManager.switchToDevice(deviceId);

            resp.putString("status", "ok");
            resp.putString("message", "Device selected");
            resp.putString("deviceId", deviceId);
        } catch (Exception e) {
            android.util.Log.w("CameraCommand", "[FLIR] Device selection failed", e);
            resp.putString("status", "error");
            resp.putString("message", e.getMessage());
        }
    }

    /**
     * Command 60: setFlirEmulatorType - Set FLIR emulator type
     * Args: [type: "FLIR_ONE" | "FLIR_ONE_EDGE"]
     */
    public static void handleSetFlirEmulatorType(Object root, WritableMap resp, @Nullable ReadableArray args) {
        try {
            if (args == null || args.size() < 1) {
                resp.putString("status", "error");
                resp.putString("message", "emulator type required");
                return;
            }

            String type = args.getString(0);

            if (!"FLIR_ONE".equals(type) && !"FLIR_ONE_EDGE".equals(type)) {
                resp.putString("status", "error");
                resp.putString("message", "Invalid type. Use FLIR_ONE or FLIR_ONE_EDGE");
                return;
            }

            // Get FlirManager instance and set emulator type
            FlirManager flirManager = FlirManager.INSTANCE;
            if (flirManager == null) {
                resp.putString("status", "error");
                resp.putString("message", "FlirManager not initialized");
                return;
            }

            flirManager.setPreferredEmulatorType(type);

            android.util.Log.i("CameraCommand", "[FLIR] Emulator type set to: " + type);

            resp.putString("status", "ok");
            resp.putString("message", "Emulator type set");
            resp.putString("emulatorType", type);
        } catch (Exception e) {
            android.util.Log.w("CameraCommand", "[FLIR] Set emulator type failed", e);
            resp.putString("status", "error");
            resp.putString("message", e.getMessage());
        }
    }

    /**
     * Command 61: setFlirPalette - Set FLIR thermal palette
     * Args: [acol: number] - palette index
     */
    public static void handleSetFlirPalette(Object root, WritableMap resp, @Nullable ReadableArray args) {
        try {
            int acol = args != null && args.size() > 0 ? (int) args.getDouble(0) : 1;

            android.util.Log.i("CameraCommand", "[FLIR] Setting palette acol=" + acol);

            // Get FlirManager instance and set palette by index
            FlirManager flirManager = FlirManager.INSTANCE;
            if (flirManager == null) {
                resp.putString("status", "error");
                resp.putString("message", "FlirManager not initialized");
                return;
            }

            // Map acol index to palette name
            String paletteName = mapAcolToPaletteName(acol);
            flirManager.setPalette(paletteName);

            resp.putString("status", "ok");
            resp.putString("message", "Palette set");
            resp.putInt("acol", acol);
        } catch (Exception e) {
            android.util.Log.w("CameraCommand", "[FLIR] Set palette failed", e);
            resp.putString("status", "error");
            resp.putString("message", e.getMessage());
        }
    }
    
    private static String mapAcolToPaletteName(int acol) {
        switch (acol) {
            case 0: return "gray";
            case 1: return "iron";
            case 2: return "rainbow";
            case 3: return "lava";
            case 4: return "arctic";
            case 5: return "coldest";
            case 6: return "hottest";
            case 7: return "contrast";
            default: return "iron";
        }
    }
}
