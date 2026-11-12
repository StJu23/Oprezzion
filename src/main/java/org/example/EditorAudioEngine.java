package org.example;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Audio engine used only by the OPREZZION chart editor.
 *
 * Supports variable-speed playback while keeping getPositionMillis()
 * in ORIGINAL song time.
 *
 * Example:
 *
 * If the editor is playing at 0.25x and the source song is currently
 * at 30.000 seconds, getPositionMillis() returns 30000.
 *
 * The simple resampler changes pitch when changing playback speed.
 * That is intentional for the editor and keeps everything dependency-free.
 */
public final class EditorAudioEngine implements AutoCloseable {

    private static final int OUTPUT_FRAMES_PER_CHUNK = 1024;

    private final Object lock = new Object();

    private final Thread audioThread;

    private byte[] audioData;

    private AudioFormat format;

    private SourceDataLine line;

    private int frameSize;

    private int channels;

    private float sampleRate;

    private long totalFrames;

    /**
     * Source-song frame corresponding to lineStartFrame.
     */
    private double baseSourceFrame;

    /**
     * Source-song position used to generate the next output sample.
     *
     * This can be ahead of the audible playback position because
     * SourceDataLine buffers data.
     */
    private double renderSourceFrame;

    private long lineStartFrame;

    private double playbackRate = 1.0;

    private boolean loaded;

    private boolean playing;

    private boolean paused;

    private boolean completed;

    private boolean closed;

    /**
     * Invalidates old writes whenever playback state changes.
     */
    private long generation;

    private String lastError;

    public EditorAudioEngine() {

        audioThread = new Thread(
                this::audioLoop,
                "Oprezzion-Editor-Audio"
        );

        audioThread.setDaemon(true);

        audioThread.start();
    }

    // ============================================================
    // LOAD
    // ============================================================

    public void load(Path path) throws Exception {

        Objects.requireNonNull(
                path,
                "Audio path cannot be null"
        );

        byte[] decodedAudio;

        AudioFormat decodedFormat;

        try (
                AudioInputStream original =
                        AudioSystem.getAudioInputStream(
                                path.toFile()
                        )
        ) {

            AudioFormat source =
                    original.getFormat();

            AudioFormat target =
                    new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,

                            source.getSampleRate(),

                            16,

                            source.getChannels(),

                            source.getChannels() * 2,

                            source.getSampleRate(),

                            false
                    );

            try (
                    AudioInputStream converted =
                            AudioSystem.getAudioInputStream(
                                    target,
                                    original
                            )
            ) {

                decodedAudio =
                        readAll(converted);

                decodedFormat =
                        target;
            }
        }

        if (decodedAudio.length == 0) {

            throw new IOException(
                    "The audio file is empty."
            );
        }

        SourceDataLine newLine = null;

        try {

            DataLine.Info info =
                    new DataLine.Info(
                            SourceDataLine.class,
                            decodedFormat
                    );

            newLine =
                    (SourceDataLine)
                            AudioSystem.getLine(info);

            newLine.open(
                    decodedFormat
            );

        } catch (Exception exception) {

            if (newLine != null) {

                newLine.close();
            }

            throw exception;
        }

        synchronized (lock) {

            ensureNotClosed();

            closeLineLocked();

            audioData =
                    decodedAudio;

            format =
                    decodedFormat;

            line =
                    newLine;

            frameSize =
                    format.getFrameSize();

            channels =
                    format.getChannels();

            sampleRate =
                    format.getSampleRate();

            totalFrames =
                    audioData.length
                            / frameSize;

            baseSourceFrame =
                    0.0;

            renderSourceFrame =
                    0.0;

            lineStartFrame =
                    line.getLongFramePosition();

            playbackRate =
                    1.0;

            loaded =
                    true;

            playing =
                    false;

            paused =
                    false;

            completed =
                    false;

            lastError =
                    null;

            generation++;

            lock.notifyAll();
        }
    }

    // ============================================================
    // PLAYBACK
    // ============================================================

    public void play() {

        synchronized (lock) {

            ensureLoaded();

            if (playing) {

                return;
            }

            if (
                    completed
                            || baseSourceFrame
                            >= totalFrames - 1
            ) {

                baseSourceFrame =
                        0.0;

                renderSourceFrame =
                        0.0;

                completed =
                        false;
            }

            line.stop();

            line.flush();

            lineStartFrame =
                    line.getLongFramePosition();

            renderSourceFrame =
                    baseSourceFrame;

            paused =
                    false;

            playing =
                    true;

            generation++;

            line.start();

            lock.notifyAll();
        }
    }

    public void pause() {

        synchronized (lock) {

            if (
                    !loaded
                            || !playing
            ) {

                return;
            }

            baseSourceFrame =
                    currentSourceFrameLocked();

            renderSourceFrame =
                    baseSourceFrame;

            playing =
                    false;

            paused =
                    true;

            generation++;

            line.stop();

            line.flush();

            lineStartFrame =
                    line.getLongFramePosition();

            lock.notifyAll();
        }
    }

    public void resume() {

        synchronized (lock) {

            if (
                    !loaded
                            || !paused
                            || completed
            ) {

                return;
            }

            line.stop();

            line.flush();

            lineStartFrame =
                    line.getLongFramePosition();

            renderSourceFrame =
                    baseSourceFrame;

            paused =
                    false;

            playing =
                    true;

            generation++;

            line.start();

            lock.notifyAll();
        }
    }

    public void togglePause() {

        if (isPlaying()) {

            pause();

        } else if (isPaused()) {

            resume();

        } else {

            play();
        }
    }

    public void stop() {

        synchronized (lock) {

            if (!loaded) {

                return;
            }

            playing =
                    false;

            paused =
                    false;

            completed =
                    false;

            generation++;

            line.stop();

            line.flush();

            baseSourceFrame =
                    0.0;

            renderSourceFrame =
                    0.0;

            lineStartFrame =
                    line.getLongFramePosition();

            lock.notifyAll();
        }
    }

    // ============================================================
    // SEEK
    // ============================================================

    public void seekMillis(long milliseconds) {

        synchronized (lock) {

            ensureLoaded();

            double requested =
                    milliseconds
                            * sampleRate
                            / 1000.0;

            requested =
                    clamp(
                            requested,
                            0.0,
                            totalFrames
                    );

            boolean wasPlaying =
                    playing;

            line.stop();

            line.flush();

            baseSourceFrame =
                    requested;

            renderSourceFrame =
                    requested;

            completed =
                    requested
                            >= totalFrames - 1;

            generation++;

            lineStartFrame =
                    line.getLongFramePosition();

            if (
                    wasPlaying
                            && !completed
            ) {

                playing =
                        true;

                paused =
                        false;

                line.start();

            } else {

                playing =
                        false;
            }

            lock.notifyAll();
        }
    }

    // ============================================================
    // PLAYBACK RATE
    // ============================================================

    /**
     * Changes playback speed without changing source-song time.
     *
     * Recommended editor rates:
     *
     * 1.00
     * 0.75
     * 0.50
     * 0.25
     */
    public void setPlaybackRate(double rate) {

        if (
                !Double.isFinite(rate)
                        || rate < 0.10
                        || rate > 2.0
        ) {

            throw new IllegalArgumentException(
                    "Playback rate must be between 0.10 and 2.00"
            );
        }

        synchronized (lock) {

            ensureLoaded();

            if (
                    Math.abs(
                            playbackRate
                                    - rate
                    )
                            < 0.000001
            ) {

                return;
            }

            boolean wasPlaying =
                    playing;

            double current =
                    currentSourceFrameLocked();

            line.stop();

            line.flush();

            baseSourceFrame =
                    current;

            renderSourceFrame =
                    current;

            playbackRate =
                    rate;

            lineStartFrame =
                    line.getLongFramePosition();

            generation++;

            if (
                    wasPlaying
                            && !completed
            ) {

                line.start();
            }

            lock.notifyAll();
        }
    }

    public double getPlaybackRate() {

        synchronized (lock) {

            return playbackRate;
        }
    }

    // ============================================================
    // CLOCK
    // ============================================================

    /**
     * Returns ORIGINAL song position.
     *
     * Playback speed does not affect the meaning of this value.
     */
    public long getPositionMillis() {

        synchronized (lock) {

            if (!loaded) {

                return 0L;
            }

            double frame =
                    currentSourceFrameLocked();

            return Math.round(
                    frame
                            * 1000.0
                            / sampleRate
            );
        }
    }

    public long getDurationMillis() {

        synchronized (lock) {

            if (!loaded) {

                return 0L;
            }

            return Math.round(
                    totalFrames
                            * 1000.0
                            / sampleRate
            );
        }
    }

    private double currentSourceFrameLocked() {

        if (!loaded) {

            return 0.0;
        }

        if (
                !playing
                        || line == null
        ) {

            return clamp(
                    baseSourceFrame,
                    0.0,
                    totalFrames
            );
        }

        long outputFramesPlayed =
                Math.max(
                        0L,

                        line.getLongFramePosition()
                                - lineStartFrame
                );

        return clamp(
                baseSourceFrame
                        + outputFramesPlayed
                        * playbackRate,

                0.0,

                totalFrames
        );
    }

    // ============================================================
    // STATUS
    // ============================================================

    public boolean isPlaying() {

        synchronized (lock) {

            return playing;
        }
    }

    public boolean isPaused() {

        synchronized (lock) {

            return paused;
        }
    }

    public boolean isLoaded() {

        synchronized (lock) {

            return loaded;
        }
    }

    public boolean isFinished() {

        synchronized (lock) {

            return completed;
        }
    }

    public String getLastError() {

        synchronized (lock) {

            return lastError;
        }
    }

    // ============================================================
    // AUDIO THREAD
    // ============================================================

    private void audioLoop() {

        byte[] output =
                new byte[
                        OUTPUT_FRAMES_PER_CHUNK
                                * 16
                        ];

        while (true) {

            SourceDataLine localLine;

            byte[] localData;

            int localFrameSize;

            int localChannels;

            long localTotalFrames;

            double localSourceStart;

            double localRate;

            long localGeneration;

            synchronized (lock) {

                try {

                    while (
                            !playing
                                    && !closed
                    ) {

                        lock.wait();
                    }

                } catch (
                        InterruptedException exception
                ) {

                    Thread.currentThread()
                            .interrupt();

                    return;
                }

                if (closed) {

                    return;
                }

                localLine =
                        line;

                localData =
                        audioData;

                localFrameSize =
                        frameSize;

                localChannels =
                        channels;

                localTotalFrames =
                        totalFrames;

                localSourceStart =
                        renderSourceFrame;

                localRate =
                        playbackRate;

                localGeneration =
                        generation;
            }

            int bytesNeeded =
                    OUTPUT_FRAMES_PER_CHUNK
                            * localFrameSize;

            if (
                    output.length
                            < bytesNeeded
            ) {

                output =
                        new byte[
                                bytesNeeded
                                ];
            }

            int generatedFrames =
                    renderChunk(
                            localData,

                            localFrameSize,

                            localChannels,

                            localTotalFrames,

                            localSourceStart,

                            localRate,

                            output,

                            OUTPUT_FRAMES_PER_CHUNK
                    );

            if (
                    generatedFrames <= 0
            ) {

                try {

                    Thread.sleep(
                            2L
                    );

                } catch (
                        InterruptedException exception
                ) {

                    Thread.currentThread()
                            .interrupt();

                    return;
                }

                synchronized (lock) {

                    if (closed) {

                        return;
                    }

                    if (
                            playing
                                    && generation
                                    == localGeneration
                    ) {

                        double current =
                                currentSourceFrameLocked();

                        if (
                                current
                                        >= totalFrames - 1.0
                        ) {

                            baseSourceFrame =
                                    totalFrames;

                            renderSourceFrame =
                                    totalFrames;

                            playing =
                                    false;

                            paused =
                                    false;

                            completed =
                                    true;

                            generation++;

                            line.stop();

                            line.flush();

                            lineStartFrame =
                                    line.getLongFramePosition();
                        }
                    }
                }

                continue;
            }

            int bytesToWrite =
                    generatedFrames
                            * localFrameSize;

            int bytesWritten;

            try {

                bytesWritten =
                        localLine.write(
                                output,
                                0,
                                bytesToWrite
                        );

            } catch (
                    RuntimeException exception
            ) {

                synchronized (lock) {

                    lastError =
                            exception.toString();

                    if (
                            generation
                                    == localGeneration
                    ) {

                        playing =
                                false;

                        paused =
                                false;
                    }
                }

                continue;
            }

            int writtenFrames =
                    bytesWritten
                            / localFrameSize;

            synchronized (lock) {

                if (closed) {

                    return;
                }

                if (
                        generation
                                == localGeneration
                                && playing
                ) {

                    renderSourceFrame =
                            Math.min(
                                    totalFrames,

                                    localSourceStart
                                            + writtenFrames
                                            * localRate
                            );
                }
            }
        }
    }

    // ============================================================
    // RESAMPLER
    // ============================================================

    private static int renderChunk(
            byte[] source,
            int frameSize,
            int channels,
            long totalFrames,
            double startSourceFrame,
            double rate,
            byte[] output,
            int maxOutputFrames
    ) {

        if (
                startSourceFrame
                        >= totalFrames - 1.0
        ) {

            return 0;
        }

        int produced =
                0;

        for (
                int outFrame = 0;
                outFrame < maxOutputFrames;
                outFrame++
        ) {

            double sourcePosition =
                    startSourceFrame
                            + outFrame
                            * rate;

            if (
                    sourcePosition
                            >= totalFrames - 1.0
            ) {

                break;
            }

            long frame0 =
                    (long)
                            Math.floor(
                                    sourcePosition
                            );

            long frame1 =
                    Math.min(
                            frame0 + 1,
                            totalFrames - 1
                    );

            double fraction =
                    sourcePosition
                            - frame0;

            int outputFrameOffset =
                    outFrame
                            * frameSize;

            int sourceOffset0 =
                    Math.toIntExact(
                            frame0
                                    * frameSize
                    );

            int sourceOffset1 =
                    Math.toIntExact(
                            frame1
                                    * frameSize
                    );

            for (
                    int channel = 0;
                    channel < channels;
                    channel++
            ) {

                int channelOffset =
                        channel * 2;

                short sample0 =
                        readShortLE(
                                source,

                                sourceOffset0
                                        + channelOffset
                        );

                short sample1 =
                        readShortLE(
                                source,

                                sourceOffset1
                                        + channelOffset
                        );

                int sample =
                        (int)
                                Math.round(
                                        sample0

                                                + (
                                                sample1
                                                        - sample0
                                        )
                                                * fraction
                                );

                sample =
                        Math.max(
                                Short.MIN_VALUE,

                                Math.min(
                                        Short.MAX_VALUE,
                                        sample
                                )
                        );

                writeShortLE(
                        output,

                        outputFrameOffset
                                + channelOffset,

                        (short) sample
                );
            }

            produced++;
        }

        return produced;
    }

    private static short readShortLE(
            byte[] data,
            int offset
    ) {

        int lo =
                data[offset]
                        & 0xFF;

        int hi =
                data[
                        offset + 1
                        ];

        return (short) (
                (
                        hi << 8
                )
                        | lo
        );
    }

    private static void writeShortLE(
            byte[] data,
            int offset,
            short value
    ) {

        data[offset] =
                (byte) (
                        value
                                & 0xFF
                );

        data[
                offset + 1
                ] =
                (byte) (
                        (
                                value >>> 8
                        )
                                & 0xFF
                );
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static byte[] readAll(
            AudioInputStream stream
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer =
                new byte[
                        16_384
                        ];

        int read;

        while (
                (
                        read =
                                stream.read(
                                        buffer
                                )
                )
                        != -1
        ) {

            output.write(
                    buffer,
                    0,
                    read
            );
        }

        return output.toByteArray();
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private void ensureLoaded() {

        ensureNotClosed();

        if (
                !loaded
                        || line == null
                        || audioData == null
                        || format == null
        ) {

            throw new IllegalStateException(
                    "No audio file is loaded."
            );
        }
    }

    private void ensureNotClosed() {

        if (closed) {

            throw new IllegalStateException(
                    "Audio engine is closed."
            );
        }
    }

    private void closeLineLocked() {

        if (
                line != null
        ) {

            try {

                line.stop();

                line.flush();

            } catch (
                    Exception ignored
            ) {
                // Ignore cleanup errors.
            }

            line.close();

            line =
                    null;
        }
    }

    // ============================================================
    // CLOSE
    // ============================================================

    @Override
    public void close() {

        synchronized (lock) {

            if (closed) {

                return;
            }

            closed =
                    true;

            playing =
                    false;

            paused =
                    false;

            generation++;

            closeLineLocked();

            lock.notifyAll();
        }

        audioThread.interrupt();
    }
}