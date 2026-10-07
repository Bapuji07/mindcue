package com.secondmemory.ai.provider;

import com.secondmemory.audio.Mp4Boxes;

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
            Mp4Boxes.Box box = Mp4Boxes.read(input, position, end);
            int type = box.type();
            if (type == 0x6d6f6f76 || type == 0x7472616b || type == 0x6d646961) { // moov/trak/mdia
                tracks |= scan(input, box.contentStart(), box.end(), depth + 1);
            } else if (type == 0x68646c72 && depth == 3 && box.size() >= box.headerSize() + 12) { // hdlr
                input.seek(box.contentStart() + 8); // full-box flags + predefined
                int handler = input.readInt();
                if (handler == 0x736f756e) tracks |= 1; // soun
                if (handler == 0x76696465) tracks |= 2; // vide
            }
            position = box.end();
        }
        return tracks;
    }
}
