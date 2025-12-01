package com.flir.thermalsdk.image;

import java.util.ArrayList;
import java.util.List;

public class PaletteManager {
    public static List<Palette> getDefaultPalettes() {
        List<Palette> palettes = new ArrayList<>();
        palettes.add(new Palette("iron", false));
        palettes.add(new Palette("rainbow", false));
        palettes.add(new Palette("arctic", false));
        palettes.add(new Palette("lava", false));
        palettes.add(new Palette("grayscale", false));
        return palettes;
    }
}
