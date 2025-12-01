package com.flir.thermalsdk.image;

public class Palette {
    public final String name;
    public final boolean inverted;

    public Palette(String name, boolean inverted) {
        this.name = name;
        this.inverted = inverted;
    }

    public Palette getInverted() {
        return new Palette(name, !inverted);
    }
}
