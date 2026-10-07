package com.secondmemory.audio;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Measures an MP4 / M4A recording's length from its movie header (moov/mvhd), so usage is
 * charged on what the server received rather than on what the client claims.
 */
public final class AudioDuration {
    private static final int MOOV = 0x6d6f6f76;
    private static final int MVHD = 0x6d766864;

    private AudioDuration() {}

    /** Whole seconds, rounded up. Throws IllegalArgumentException for unreadable or empty files. */
    public static int seconds(Path file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long length = input.length();
            for (long position = 0; position < length;) {
                Mp4Boxes.Box box = Mp4Boxes.read(input, position, length);
                if (box.type() == MOOV) return fromMoov(input, box);
                position = box.end();
            }
        }
        throw new IllegalArgumentException("Recording has no MP4 movie header; it may be incomplete");
    }

    private static int fromMoov(RandomAccessFile input, Mp4Boxes.Box moov) throws IOException {
        for (long position = moov.contentStart(); position < moov.end();) {
            Mp4Boxes.Box box = Mp4Boxes.read(input, position, moov.end());
            if (box.type() == MVHD) return fromMvhd(input, box);
            position = box.end();
        }
        throw new IllegalArgumentException("Recording has no MP4 movie header; it may be incomplete");
    }

    private static int fromMvhd(RandomAccessFile input, Mp4Boxes.Box mvhd) throws IOException {
        long content = mvhd.contentStart();
        input.seek(content);
        int version = input.readUnsignedByte();
        long timescale;
        long duration;
        if (version == 1) {
            if (mvhd.size() - mvhd.headerSize() < 32) throw new IllegalArgumentException("Invalid MP4 movie header");
            input.seek(content + 20);
            timescale = Integer.toUnsignedLong(input.readInt());
            duration = input.readLong();
            if (duration == -1) throw new IllegalArgumentException("Recording length is unknown");
        } else {
            if (mvhd.size() - mvhd.headerSize() < 20) throw new IllegalArgumentException("Invalid MP4 movie header");
            input.seek(content + 12);
            timescale = Integer.toUnsignedLong(input.readInt());
            duration = Integer.toUnsignedLong(input.readInt());
            if (duration == 0xFFFFFFFFL) throw new IllegalArgumentException("Recording length is unknown");
        }
        if (timescale == 0 || duration <= 0) throw new IllegalArgumentException("Recording is empty");
        long seconds = (duration + timescale - 1) / timescale;
        if (seconds > Integer.MAX_VALUE) throw new IllegalArgumentException("Recording length is invalid");
        return (int) seconds;
    }
}
