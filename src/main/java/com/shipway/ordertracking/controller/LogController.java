package com.shipway.ordertracking.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    @Value("${logging.file.name:logs/application.log}")
    private String logFilePath;

    @Value("${logging.file.max-history:5}")
    private int maxHistory;

    /**
     * Get current log file content
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentLogs(
            @RequestParam(defaultValue = "100") int lines) {

        try {
            // Limit lines to max 1000
            int maxLines = Math.min(lines, 1000);

            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Log file not found", "path", logFilePath));
            }

            List<String> logLines = readLastLines(logFile, maxLines);

            Map<String, Object> response = new HashMap<>();
            response.put("file", logFilePath);
            response.put("totalLines", logLines.size());
            response.put("requestedLines", maxLines);
            response.put("logs", logLines);
            response.put("fileSize", logFile.length());
            response.put("lastModified", new Date(logFile.lastModified()));

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading log file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error reading log file: " + e.getMessage()));
        }
    }

    /**
     * List all log files (current and archived zip files)
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listLogFiles() {
        try {
            File logFile = new File(logFilePath);
            File logDir = logFile.getParentFile();

            // Handle case where log directory doesn't exist yet
            if (logDir == null || !logDir.exists()) {
                Map<String, Object> response = new HashMap<>();
                response.put("directory", logDir != null ? logDir.getAbsolutePath() : "unknown");
                response.put("totalFiles", 0);
                response.put("files", Collections.emptyList());
                return ResponseEntity.ok(response);
            }

            List<Map<String, Object>> files = new ArrayList<>();

            // Add current log file
            if (logFile.exists()) {
                files.add(createFileInfo(logFile, false));
            }

            // Add archived zip files
            File[] zipFiles = logDir
                    .listFiles((dir, name) -> name.startsWith(logFile.getName()) && name.endsWith(".zip"));

            if (zipFiles != null) {
                Arrays.sort(zipFiles, Comparator.comparingLong(File::lastModified).reversed());
                for (File zipFile : zipFiles) {
                    files.add(createFileInfo(zipFile, true));
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("directory", logDir.getAbsolutePath());
            response.put("totalFiles", files.size());
            response.put("files", files);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing log files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error listing log files: " + e.getMessage()));
        }
    }

    /**
     * Download a specific log file (current or archived zip)
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadLogFile(@PathVariable String filename) {
        try {
            File logFile;

            if ("current".equalsIgnoreCase(filename)) {
                logFile = new File(logFilePath);
            } else {
                File logDir = new File(logFilePath).getParentFile();
                if (logDir == null) {
                    return ResponseEntity.notFound().build();
                }
                // Security check to prevent directory traversal
                if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    return ResponseEntity.badRequest().build();
                }
                logFile = new File(logDir, filename);
            }

            if (!logFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(logFile);
            String contentType = filename.endsWith(".zip") ? "application/zip" : "text/plain";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + logFile.getName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading log file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Read last N lines from a file efficiently using RandomAccessFile
     */
    private List<String> readLastLines(File file, int lines) throws IOException {
        List<String> result = new ArrayList<>();

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            long filePointer = fileLength - 1;
            StringBuilder sb = new StringBuilder();
            int linesRead = 0;

            // Iterate backwards from the end of the file
            while (filePointer >= 0 && linesRead < lines) {
                raf.seek(filePointer);
                int readByte = raf.readByte();

                if (readByte == 0xA) { // Line feed (\n)
                    if (filePointer < fileLength - 1) {
                        // We found a line, add it to the list
                        // Note: We reverse the string builder because we read backwards
                        result.add(sb.reverse().toString());
                        sb.setLength(0);
                        linesRead++;
                    }
                } else if (readByte == 0xD) { // Carriage return (\r)
                    // Ignore, usually handled with \n
                } else {
                    sb.append((char) readByte);
                }
                filePointer--;
            }

            // Add the last remaining line (which is actually the first line we read)
            if (sb.length() > 0 && linesRead < lines) {
                result.add(sb.reverse().toString());
            }
        }

        // The lines were added in reverse order (bottom to top), so we don't need to
        // reverse the list
        // if we want newest first. But usually logs are displayed oldest to newest or
        // we might want them reverse.
        // Let's keep them as detected (newest first) since we scanned from bottom.
        // If the UI expects chronological order, we should reverse the list.
        // Based on typical "tail" behavior, newest first (top of response) is often
        // good for APIs,
        // but let's check if the previous implementation did any sorting.
        // Previous implementation: Files.readAllLines() -> all lines. subList ->
        // chronological.
        // So we should probably reverse this list to match chronological order (oldest
        // -> newest).
        Collections.reverse(result);

        return result;
    }

    /**
     * Create file information map
     */
    private Map<String, Object> createFileInfo(File file, boolean isArchived) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", file.getName());
        info.put("path", file.getAbsolutePath());
        info.put("size", file.length());
        info.put("sizeFormatted", formatFileSize(file.length()));
        info.put("lastModified", new Date(file.lastModified()));
        info.put("isArchived", isArchived);
        return info;
    }

    /**
     * Format file size in human-readable format
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
