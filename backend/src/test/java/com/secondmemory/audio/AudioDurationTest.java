package com.secondmemory.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioDurationTest {
    @TempDir Path dir;

    @Test
    void readsVersion0MovieHeaderRoundingUp() throws IOException {
        // 90.5 s at a 1000 Hz timescale -> 91 s.
        assertEquals(91, AudioDuration.seconds(write(mp4(mvhdV0(1000, 90_500)))));
    }

    @Test
    void readsVersion1MovieHeader() throws IOException {
        assertEquals(3600, AudioDuration.seconds(write(mp4(mvhdV1(44_100, 44_100L * 3600)))));
    }

    @Test
    void rejectsFileWithoutMovieHeader() throws IOException {
        Path noMoov = write(box("ftyp", new byte[8]));
        assertThrows(IllegalArgumentException.class, () -> AudioDuration.seconds(noMoov));
    }

    @Test
    void rejectsEmptyAndCorruptRecordings() throws IOException {
        Path empty = write(mp4(mvhdV0(1000, 0)));
        Path corrupt = write(new byte[] {0, 0, 0, 99, 1});
        assertThrows(IllegalArgumentException.class, () -> AudioDuration.seconds(empty));
        assertThrows(IllegalArgumentException.class, () -> AudioDuration.seconds(corrupt));
    }

    private Path write(byte[] bytes) throws IOException {
        Path file = Files.createTempFile(dir, "rec", ".m4a");
        Files.write(file, bytes);
        return file;
    }

    private static byte[] mp4(byte[] mvhd) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(box("ftyp", new byte[8]));
        out.write(box("mdat", new byte[16]));
        out.write(box("moov", box("mvhd", mvhd)));
        return out.toByteArray();
    }

    private static byte[] mvhdV0(int timescale, int duration) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0);          // version 0 + flags
        out.writeInt(0);          // creation time
        out.writeInt(0);          // modification time
        out.writeInt(timescale);
        out.writeInt(duration);
        out.write(new byte[80]);  // rest of mvhd
        return bytes.toByteArray();
    }

    private static byte[] mvhdV1(int timescale, long duration) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(1 << 24);    // version 1 + flags
        out.writeLong(0);         // creation time
        out.writeLong(0);         // modification time
        out.writeInt(timescale);
        out.writeLong(duration);
        out.write(new byte[80]);
        return bytes.toByteArray();
    }

    private static byte[] box(String type, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(8 + content.length);
        out.write(type.getBytes(StandardCharsets.US_ASCII));
        out.write(content);
        return bytes.toByteArray();
    }
}
