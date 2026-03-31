package com.shipway.ordertracking.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc — {@link LogController} uses {@code @Value}; avoids pulling full web slice for file paths.
 */
class LogControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private Path logFile;

    @BeforeEach
    void setUp() {
        logFile = tempDir.resolve("application.log");
        LogController controller = new LogController();
        ReflectionTestUtils.setField(controller, "logFilePath", logFile.toAbsolutePath().toString());
        ReflectionTestUtils.setField(controller, "maxHistory", 5);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void currentLog_fileMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/logs/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Log file not found"));
    }

    @Test
    void currentLog_filePresent_returnsOk() throws Exception {
        Files.writeString(logFile, "line1\nline2\nline3\n");

        mockMvc.perform(get("/api/logs/current").param("lines", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isArray())
                .andExpect(jsonPath("$.file").exists());
    }

    @Test
    void listFiles_includesCurrentWhenPresent() throws Exception {
        Files.writeString(logFile, "entry");

        mockMvc.perform(get("/api/logs/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directory").exists())
                .andExpect(jsonPath("$.totalFiles").value(1));
    }

    @Test
    void listFiles_directoryMissing_returnsEmptyList() throws Exception {
        Path ghostLog = tempDir.resolve("missing_subdir/nested/application.log");
        LogController controller = new LogController();
        ReflectionTestUtils.setField(controller, "logFilePath", ghostLog.toAbsolutePath().toString());
        ReflectionTestUtils.setField(controller, "maxHistory", 5);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/logs/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles").value(0))
                .andExpect(jsonPath("$.files").isArray());
    }

    @Test
    void downloadCurrent_fileMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/logs/download/current"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadCurrent_filePresent_returnsAttachment() throws Exception {
        Files.writeString(logFile, "log line");

        mockMvc.perform(get("/api/logs/download/current"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    void download_filenameWithDotDot_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/logs/download/foo..bar"))
                .andExpect(status().isBadRequest());
    }
}
