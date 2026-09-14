package com.secondmemory.ai.provider;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/** Reads ISO BMFF track handlers; .mp4 recordings can contain audio only. */
final class Mp4MediaType {
    private Mp4MediaType() {}

    static String detect(Path path) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "r")) {
            int tracks = scan(input, 0, input.length(), 0);
            if ((tracks & 2) != 0) return "video/mp4";
            if ((tracks & 1) != 0) return "audio/mp4";
            throw new IllegalArgumentException("MP4 recording has no readable audio or video tracks");
        }
    }

    private static int scan(RandomAccessFile input, long start, long end, int depth) throws IOException {
        if (depth > 4) throw new IllegalArgumentException("Invalid MP4 container nesting");
        int tracks = 0;
        for (long position = start; position < end;) {
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
            if (type == 0x6d6f6f76 || type == 0x7472616b || type == 0x6d646961) { // moov/trak/mdia
                tracks |= scan(input, position + header, position + size, depth + 1);
            } else if (type == 0x68646c72 && depth == 3 && size >= header + 12) { // hdlr
                input.seek(position + header + 8); // full-box flags + predefined
                int handler = input.readInt();
                if (handler == 0x736f756e) tracks |= 1; // soun
                if (handler == 0x76696465) tracks |= 2; // vide
            }
            position += size;
        }
        return tracks;
    }
}
