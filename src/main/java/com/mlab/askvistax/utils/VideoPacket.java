package com.mlab.askvistax.utils;

public class VideoPacket {
    private final byte[] data;
    private final boolean poisonPill;

    private VideoPacket(byte[] data, boolean poisonPill) {
        this.data = data;
        this.poisonPill = poisonPill;
    }

    public static VideoPacket data(byte[] data) {
        return new VideoPacket(data, false);
    }

    public static VideoPacket poisonPill() {
        return new VideoPacket(null, true);
    }

    public byte[] getData() {
        return data;
    }

    public boolean isPoisonPill() {
        return poisonPill;
    }
}
