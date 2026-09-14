package com.secondmemory.ai.provider;

import com.secondmemory.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiTranscriptionProviderTest {
    @TempDir Path directory;
    MockRestServiceServer server;
    GeminiTranscriptionProvider provider;

    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeminiTranscriptionProvider(new AiProperties(null, null,
                new AiProperties.Transcription("gemini", "https://example.test/v1beta",
                        "test-key", "test-model", "en")), builder);
    }

    private Path recording(String name) throws Exception {
        return Files.write(directory.resolve(name), name.toLowerCase(Locale.ROOT).endsWith(".mp4")
                ? mp4(0x76696465) : new byte[]{1, 2, 3});
    }

    static byte[] mp4(int handler) {
        return ByteBuffer.allocate(44).putInt(44).putInt(0x6d6f6f76)
                .putInt(36).putInt(0x7472616b).putInt(28).putInt(0x6d646961)
                .putInt(20).putInt(0x68646c72).putInt(0).putInt(0).putInt(handler).array();
    }

    private void expectUpload(String mimeType, String state) {
        expectUpload(mimeType, state, mimeType.equals("video/mp4") ? mp4(0x76696465) : new byte[]{1, 2, 3});
    }

    private void expectUpload(String mimeType, String state, byte[] bytes) {
        server.expect(requestTo("https://example.test/upload/v1beta/files"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(header("X-Goog-Upload-Header-Content-Type", mimeType))
                .andExpect(header("X-Goog-Upload-Header-Content-Length", Integer.toString(bytes.length)))
                .andRespond(withSuccess().header("X-Goog-Upload-URL", "https://example.test/upload-session"));
        server.expect(requestTo("https://example.test/upload-session"))
                .andExpect(header("X-Goog-Upload-Command", "upload, finalize"))
                .andExpect(content().bytes(bytes))
                .andRespond(withSuccess("{\"file\":{\"name\":\"files/test\",\"uri\":\"https://example.test/media\",\"state\":\"" + state + "\"}}", MediaType.APPLICATION_JSON));
    }

    private void expectCleanup() {
        server.expect(requestTo("https://example.test/v1beta/files/test"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
    }

    @Test void sendsMp4AndReadsOnlyTranscriptNotThoughts() throws Exception {
        expectUpload("video/mp4", "ACTIVE");
        server.expect(requestTo("https://example.test/v1beta/models/test-model:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[1].fileData.mimeType").value("video/mp4"))
                .andExpect(jsonPath("$.contents[0].parts[1].fileData.fileUri").value("https://example.test/media"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(org.hamcrest.Matchers.containsString("en")))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[
                        {"thought":true,"text":"Internal reasoning"},
                        {"text":"I promised Ravi"},{"text":"to check the discount tomorrow."}]}}]}
                        """, MediaType.APPLICATION_JSON));
        expectCleanup();
        var result = provider.transcribe(recording("sample.MP4"));
        assertThat(result.text()).isEqualTo("I promised Ravi\nto check the discount tomorrow.");
        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().get(0).text()).isEqualTo(result.text());
        server.verify();
    }

    @Test void supportsAudioM4a() throws Exception {
        expectUpload("audio/mp4", "ACTIVE");
        server.expect(anything())
                .andExpect(jsonPath("$.contents[0].parts[1].fileData.mimeType").value("audio/mp4"))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Hello"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        expectCleanup();
        assertThat(provider.transcribe(recording("sample.m4a")).text()).isEqualTo("Hello");
        server.verify();
    }

    @Test void rejectsUnsupportedFileBeforeNetwork() throws Exception {
        Path file = recording("sample.txt");
        assertThatThrownBy(() -> provider.transcribe(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
        server.verify();
    }

    @Test void rejectsEmptyRecording() throws Exception {
        Path file = Files.createFile(directory.resolve("empty.wav"));
        assertThatThrownBy(() -> provider.transcribe(file)).hasMessageContaining("empty");
        server.verify();
    }

    @Test void rejectsFileAboveApplicationUploadLimit() throws Exception {
        Path file = directory.resolve("large.mp4");
        try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
            out.setLength(500L * 1024 * 1024 + 1);
        }
        assertThatThrownBy(() -> provider.transcribe(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500 MiB");
        server.verify();
    }

    @Test void rejectsTruncatedTranscript() throws Exception {
        expectUpload("audio/wav", "ACTIVE");
        server.expect(anything()).andRespond(withSuccess("""
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"partial"}]}}]}
                """, MediaType.APPLICATION_JSON));
        expectCleanup();
        Path file = recording("sample.wav");
        assertThatThrownBy(() -> provider.transcribe(file)).hasMessageContaining("MAX_TOKENS");
        server.verify();
    }

    @Test void rejectsEmptyTranscript() throws Exception {
        expectUpload("audio/wav", "ACTIVE");
        server.expect(anything()).andRespond(withSuccess("""
                {"candidates":[{"finishReason":"STOP","content":{"parts":[]}}]}
                """, MediaType.APPLICATION_JSON));
        expectCleanup();
        Path file = recording("sample.wav");
        assertThatThrownBy(() -> provider.transcribe(file)).hasMessageContaining("no transcript");
        server.verify();
    }

    @Test void waitsForRemoteProcessingBeforeGeneration() throws Exception {
        expectUpload("video/mp4", "PROCESSING");
        server.expect(requestTo("https://example.test/v1beta/files/test"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                        {"name":"files/test","uri":"https://example.test/media","state":"ACTIVE"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v1beta/models/test-model:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Hello"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        expectCleanup();
        assertThat(provider.transcribe(recording("sample.mp4")).text()).isEqualTo("Hello");
        server.verify();
    }

    @Test void cleansUpAfterRemoteProcessingFailure() throws Exception {
        expectUpload("audio/wav", "FAILED");
        expectCleanup();
        Path file = recording("sample.wav");
        assertThatThrownBy(() -> provider.transcribe(file)).hasMessageContaining("FAILED");
        server.verify();
    }

    @Test void rejectsUploadRedirectToAnotherHost() throws Exception {
        server.expect(anything()).andRespond(withSuccess()
                .header("X-Goog-Upload-URL", "https://other.test/upload"));
        Path file = recording("sample.wav");
        assertThatThrownBy(() -> provider.transcribe(file)).hasMessageContaining("unexpected file upload host");
        server.verify();
    }

    @Test void uploadsAudioOnlyMp4AsAudio() throws Exception {
        byte[] bytes = mp4(0x736f756e);
        expectUpload("audio/mp4", "ACTIVE", bytes);
        server.expect(anything())
                .andExpect(jsonPath("$.contents[0].parts[1].fileData.mimeType").value("audio/mp4"))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Hello"}]}}]}
                        """, MediaType.APPLICATION_JSON));
        expectCleanup();
        assertThat(provider.transcribe(Files.write(directory.resolve("audio.mp4"), bytes)).text()).isEqualTo("Hello");
        server.verify();
    }

    @Test void rejectsMalformedMp4WithoutNetworkCall() throws Exception {
        Path file = Files.write(directory.resolve("bad.mp4"), ByteBuffer.allocate(8).putInt(999).putInt(0).array());
        assertThatThrownBy(() -> provider.transcribe(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MP4 box size");
        server.verify();
    }
}
