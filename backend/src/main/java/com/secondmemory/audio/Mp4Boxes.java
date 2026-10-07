package com.secondmemory.audio;

import java.io.IOException;
import java.io.RandomAccessFile;

/** Reads ISO BMFF (MP4 / M4A) box headers with bounds checks. */
public final class Mp4Boxes {
    private Mp4Boxes() {}

    public record Box(int type, long start, long headerSize, long size) {
        public long contentStart() { return start + headerSize; }
        public long end() { return start + size; }
    }

    /** Reads the box header at {@code position}; the box must fit before {@code end}. */
    public static Box read(RandomAccessFile input, long position, long end) throws IOException {
        if (end - position < 8) throw new IllegalArgumentException("Incomplete MP4 box header");
        input.seek(position);
        long size = Integer.toUnsignedLong(input.readInt());
        int type = input.readInt();
        long header = 8;
        if (size == 1) {
            if (end - position < 16) throw new IllegalArgumentException("Incomplete MP4 extended header");
            size = input.readLong();
            header = 16;
        } else if (size == 0) {
            size = end - position;
        }
        if (size < header || size > end - position) {
            throw new IllegalArgumentException("Invalid MP4 box size");
        }
        return new Box(type, position, header, size);
    }
}
