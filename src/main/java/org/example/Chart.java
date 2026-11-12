package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Loads Oprezzion's deliberately simple CSV chart format.
 *
 * Format:
 *   time_ms,lane,type,duration_ms
 *
 * Examples:
 *   2015,1,TAP,0
 *   2916,0,HOLD,721
 *
 * Blank lines and comment lines are ignored.
 */
public final class Chart {
    private final List<ChartNote> notes;
    private final String sourceDescription;

    private Chart(List<ChartNote> notes, String sourceDescription) {
        this.notes = List.copyOf(notes);
        this.sourceDescription = sourceDescription;
    }

    public List<ChartNote> notes() {
        return notes;
    }

    public int noteCount() {
        return notes.size();
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public long lastNoteEndNanos() {
        long last = 0L;

        for (ChartNote note : notes) {
            last = Math.max(last, note.endTimeNanos());
        }

        return last;
    }

    public static Chart load(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath cannot be blank");
        }

        String normalizedResource = resourcePath.startsWith("/")
                ? resourcePath
                : "/" + resourcePath;

        /*
         * First try the actual src/main/resources directory.
         *
         * This makes development much less annoying because changes to the chart
         * can be picked up immediately without relying on IntelliJ/Gradle to copy
         * the resource into build/resources first.
         */
        Path diskPath = findResourceOnDisk(normalizedResource);

        if (diskPath != null) {
            try (
                    BufferedReader reader = Files.newBufferedReader(
                            diskPath,
                            StandardCharsets.UTF_8
                    )
            ) {
                return parse(
                        reader,
                        diskPath.toAbsolutePath().normalize().toString()
                );
            }
        }

        /*
         * Normal packaged-JAR/classpath fallback.
         */
        InputStream input = Chart.class.getResourceAsStream(normalizedResource);

        if (input == null) {
            Path expected = Path.of(
                    System.getProperty("user.dir"),
                    "src",
                    "main",
                    "resources",
                    normalizedResource.substring(1)
            ).toAbsolutePath().normalize();

            throw new IOException(
                    "Could not find chart "
                            + normalizedResource
                            + " (expected something like "
                            + expected
                            + ")"
            );
        }

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                input,
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            return parse(
                    reader,
                    "classpath:" + normalizedResource
            );
        }
    }

    private static Chart parse(
            BufferedReader reader,
            String sourceDescription
    ) throws IOException {
        ArrayList<ChartNote> notes = new ArrayList<>();

        String line;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;

            String trimmed = line.trim();

            if (
                    trimmed.isEmpty()
                            || trimmed.startsWith("#")
                            || trimmed.startsWith("//")
            ) {
                continue;
            }

            int inlineComment = trimmed.indexOf('#');

            if (inlineComment >= 0) {
                trimmed = trimmed.substring(
                        0,
                        inlineComment
                ).trim();

                if (trimmed.isEmpty()) {
                    continue;
                }
            }

            String[] parts = trimmed.split("\\s*,\\s*");

            /*
             * Header can be written either as:
             *
             * # time_ms,lane,type,duration_ms
             *
             * or simply:
             *
             * time_ms,lane,type,duration_ms
             */
            if (
                    parts.length == 4
                            && parts[0].trim().equalsIgnoreCase("time_ms")
                            && parts[1].trim().equalsIgnoreCase("lane")
                            && parts[2].trim().equalsIgnoreCase("type")
                            && parts[3].trim().equalsIgnoreCase("duration_ms")
            ) {
                continue;
            }

            if (parts.length != 4) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "expected 4 comma-separated fields: time_ms,lane,type,duration_ms",
                        line
                );
            }

            long timeMs = parseLong(
                    parts[0],
                    "time_ms",
                    sourceDescription,
                    lineNumber,
                    line
            );

            int lane = parseInt(
                    parts[1],
                    "lane",
                    sourceDescription,
                    lineNumber,
                    line
            );

            ChartNote.Type type;

            try {
                type = ChartNote.Type.valueOf(
                        parts[2]
                                .trim()
                                .toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "type must be TAP or HOLD",
                        line
                );
            }

            long durationMs = parseLong(
                    parts[3],
                    "duration_ms",
                    sourceDescription,
                    lineNumber,
                    line
            );

            if (timeMs < 0L) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "time_ms cannot be negative",
                        line
                );
            }

            if (lane < 0 || lane > 2) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "lane must be 0, 1, or 2",
                        line
                );
            }

            if (durationMs < 0L) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "duration_ms cannot be negative",
                        line
                );
            }

            if (
                    type == ChartNote.Type.TAP
                            && durationMs != 0L
            ) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "TAP duration_ms must be 0",
                        line
                );
            }

            if (
                    type == ChartNote.Type.HOLD
                            && durationMs <= 0L
            ) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "HOLD duration_ms must be greater than 0",
                        line
                );
            }

            try {
                notes.add(
                        new ChartNote(
                                Math.multiplyExact(
                                        timeMs,
                                        1_000_000L
                                ),

                                lane,

                                type,

                                Math.multiplyExact(
                                        durationMs,
                                        1_000_000L
                                )
                        )
                );
            } catch (ArithmeticException exception) {
                throw chartError(
                        sourceDescription,
                        lineNumber,
                        "timestamp is too large",
                        line
                );
            }
        }

        if (notes.isEmpty()) {
            throw new IOException(
                    "Chart contains no notes: "
                            + sourceDescription
            );
        }

        /*
         * You don't actually have to manually keep the chart sorted.
         * The loader sorts by timestamp, then lane.
         */
        notes.sort(
                Comparator
                        .comparingLong(
                                ChartNote::targetTimeNanos
                        )
                        .thenComparingInt(
                                ChartNote::lane
                        )
        );

        return new Chart(
                notes,
                sourceDescription
        );
    }

    private static long parseLong(
            String value,
            String field,
            String source,
            int lineNumber,
            String originalLine
    ) throws IOException {
        try {
            return Long.parseLong(
                    value.trim()
            );
        } catch (NumberFormatException exception) {
            throw chartError(
                    source,
                    lineNumber,
                    field + " must be an integer",
                    originalLine
            );
        }
    }

    private static int parseInt(
            String value,
            String field,
            String source,
            int lineNumber,
            String originalLine
    ) throws IOException {
        try {
            return Integer.parseInt(
                    value.trim()
            );
        } catch (NumberFormatException exception) {
            throw chartError(
                    source,
                    lineNumber,
                    field + " must be an integer",
                    originalLine
            );
        }
    }

    private static IOException chartError(
            String source,
            int lineNumber,
            String message,
            String line
    ) {
        return new IOException(
                "Chart error at line "
                        + lineNumber
                        + " in "
                        + source
                        + ": "
                        + message
                        + " -> "
                        + line.trim()
        );
    }

    private static Path findResourceOnDisk(
            String resourcePath
    ) {
        String relative = resourcePath.startsWith("/")
                ? resourcePath.substring(1)
                : resourcePath;

        Path current = Path.of(
                        System.getProperty("user.dir")
                )
                .toAbsolutePath()
                .normalize();

        /*
         * Search upwards because IntelliJ's working directory can be
         * the project root, module directory, or occasionally something
         * nested inside it.
         */
        for (
                int i = 0;
                i < 8 && current != null;
                i++
        ) {
            Path candidate = current
                    .resolve("src")
                    .resolve("main")
                    .resolve("resources")
                    .resolve(relative)
                    .normalize();

            if (Files.isRegularFile(candidate)) {
                return candidate;
            }

            current = current.getParent();
        }

        return null;
    }
}