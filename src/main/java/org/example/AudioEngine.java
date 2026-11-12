
package org.example;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class AudioEngine implements AutoCloseable {

    private static final int WRITE_CHUNK_SIZE = 1024;

    private final Object lock = new Object();

    private byte[] audioData;
    private AudioFormat format;
    private SourceDataLine line;

    private int frameSize;
    private long totalFrames;

    // Bytes submitted to the audio device.
    private long cursorBytes;

    // Playback position when the current playback segment began.
    private long baseFrame;

    // Audio device position when the current segment began.
    private long lineStartFrame;

    private long generation;

    private boolean playing;
    private boolean paused;
    private boolean loaded;
    private boolean completed;
    private boolean closed;

    private String lastError;

    private final Thread audioThread;

    public AudioEngine() {
        audioThread = new Thread(
                this::audioLoop,
                "Oprezzion-Audio"
        );

        audioThread.setDaemon(true);
        audioThread.start();
    }

    // --------------------------------------------------
    // LOAD
    // --------------------------------------------------

    public void load(Path path) throws Exception {
        Objects.requireNonNull(path, "Audio path cannot be null");

        byte[] decodedAudio;
        AudioFormat decodedFormat;

        try (AudioInputStream original =
                     AudioSystem.getAudioInputStream(path.toFile())) {

            AudioFormat sourceFormat = original.getFormat();

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream converted =
                         AudioSystem.getAudioInputStream(
                                 targetFormat,
                                 original
                         )) {

                decodedAudio = readAll(converted);
                decodedFormat = targetFormat;
            }
        }

        if (decodedAudio.length == 0) {
            throw new IOException("The audio file is empty.");
        }

        if (decodedFormat.getFrameSize() <= 0) {
            throw new IOException("Invalid audio frame size.");
        }

        synchronized (lock) {
            ensureNotClosed();

            generation++;

            playing = false;
            paused = false;
            completed = false;
            loaded = false;

            closeLine();

            DataLine.Info info = new DataLine.Info(
                    SourceDataLine.class,
                    decodedFormat
            );

            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(decodedFormat);

            audioData = decodedAudio;
            format = decodedFormat;

            frameSize = decodedFormat.getFrameSize();
            totalFrames = decodedAudio.length / frameSize;

            cursorBytes = 0;
            baseFrame = 0;
            lineStartFrame = line.getLongFramePosition();

            lastError = null;
            loaded = true;

            lock.notifyAll();
        }
    }

    private byte[] readAll(AudioInputStream stream)
            throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = stream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }

        return output.toByteArray();
    }

    // --------------------------------------------------
    // PLAYBACK
    // --------------------------------------------------

    public void play() {
        synchronized (lock) {
            ensureLoaded();

            if (playing) {
                return;
            }

            if (completed || cursorBytes >= audioData.length) {
                cursorBytes = 0;
                baseFrame = 0;
                completed = false;
            }

            generation++;

            paused = false;
            playing = true;

            lineStartFrame = line.getLongFramePosition();
            line.start();

            lastError = null;

            lock.notifyAll();
        }
    }

    public void pause() {
        synchronized (lock) {
            if (!loaded || !playing) {
                return;
            }

            long currentFrame = getCurrentFrameLocked();

            generation++;

            playing = false;
            paused = true;

            line.stop();
            line.flush();

            baseFrame = currentFrame;
            cursorBytes = currentFrame * frameSize;
            lineStartFrame = line.getLongFramePosition();

            lock.notifyAll();
        }
    }

    public void resume() {
        synchronized (lock) {
            if (!loaded || !paused || completed) {
                return;
            }

            generation++;

            paused = false;
            playing = true;

            lineStartFrame = line.getLongFramePosition();
            line.start();

            lastError = null;

            lock.notifyAll();
        }
    }

    public void stop() {
        synchronized (lock) {
            if (!loaded) {
                return;
            }

            generation++;

            playing = false;
            paused = false;
            completed = false;

            line.stop();
            line.flush();

            cursorBytes = 0;
            baseFrame = 0;
            lineStartFrame = line.getLongFramePosition();

            lock.notifyAll();
        }
    }

    // --------------------------------------------------
    // SEEKING
    // --------------------------------------------------

    public void seekMillis(long milliseconds) {
        synchronized (lock) {
            ensureLoaded();

            long requestedFrame = (long) (
                    milliseconds * format.getFrameRate() / 1000.0
            );

            requestedFrame = Math.max(
                    0,
                    Math.min(requestedFrame, totalFrames)
            );

            boolean wasPlaying = playing;
            boolean wasPaused = paused;

            generation++;

            line.stop();
            line.flush();

            cursorBytes = requestedFrame * frameSize;
            baseFrame = requestedFrame;

            completed = requestedFrame >= totalFrames;

            lineStartFrame = line.getLongFramePosition();

            if (wasPlaying && !completed) {
                playing = true;
                paused = false;
                line.start();
            } else {
                playing = false;
                paused = wasPaused && !completed;
            }

            lock.notifyAll();
        }
    }

    // --------------------------------------------------
    // PLAYBACK CLOCK
    // --------------------------------------------------

    public long getPositionMillis() {
        synchronized (lock) {
            if (!loaded) {
                return 0;
            }

            long currentFrame = getCurrentFrameLocked();

            return Math.round(
                    currentFrame * 1000.0 / format.getFrameRate()
            );
        }
    }

    private long getCurrentFrameLocked() {
        if (!playing || line == null) {
            return baseFrame;
        }

        long lineFrame =
                line.getLongFramePosition() - lineStartFrame;

        return Math.max(
                baseFrame,
                Math.min(baseFrame + lineFrame, totalFrames)
        );
    }

    public long getDurationMillis() {
        synchronized (lock) {
            if (!loaded) {
                return 0;
            }

            return Math.round(
                    totalFrames * 1000.0 / format.getFrameRate()
            );
        }
    }

    // --------------------------------------------------
    // STATUS
    // --------------------------------------------------

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

    // --------------------------------------------------
    // AUDIO THREAD
    // --------------------------------------------------

    private void audioLoop() {
        while (true) {

            byte[] data;
            SourceDataLine outputLine;
            long currentGeneration;
            int offset;
            int bytesToWrite;

            synchronized (lock) {
                try {
                    while (!playing && !closed) {
                        lock.wait();
                    }

                    if (closed) {
                        return;
                    }

                    if (!loaded || line == null || audioData == null) {
                        playing = false;
                        continue;
                    }

                    // All audio has been submitted.
                    // Wait until the device has actually played it.
                    if (cursorBytes >= audioData.length) {

                        long currentFrame = getCurrentFrameLocked();

                        if (currentFrame >= totalFrames) {
                            playing = false;
                            paused = false;
                            completed = true;

                            baseFrame = totalFrames;
                            cursorBytes = audioData.length;

                            lock.notifyAll();
                        } else {
                            lock.wait(5);
                        }

                        continue;
                    }

                    currentGeneration = generation;
                    data = audioData;
                    outputLine = line;
                    offset = (int) cursorBytes;

                    int remaining = data.length - offset;

                    bytesToWrite = Math.min(
                            WRITE_CHUNK_SIZE,
                            remaining
                    );

                    // Never split an audio frame.
                    bytesToWrite -= bytesToWrite % frameSize;

                    if (bytesToWrite <= 0) {
                        playing = false;
                        lastError = "Invalid audio frame alignment.";
                        continue;
                    }

                } catch (InterruptedException exception) {
                    if (closed) {
                        return;
                    }

                    continue;
                }
            }

            // IMPORTANT:
            // Audio writes happen OUTSIDE the main lock.
            // This prevents blocking playback from freezing
            // pause, resume, position and status methods.

            try {
                int written = outputLine.write(
                        data,
                        offset,
                        bytesToWrite
                );

                synchronized (lock) {
                    // Ignore writes belonging to an old playback
                    // session, such as one interrupted by a seek.
                    if (generation == currentGeneration
                            && playing
                            && line == outputLine) {

                        if (written > 0) {
                            cursorBytes = offset + written;
                        } else {
                            playing = false;
                            lastError = "Audio device wrote zero bytes.";
                        }

                        lock.notifyAll();
                    }
                }

            } catch (Exception exception) {
                synchronized (lock) {
                    if (generation == currentGeneration) {
                        playing = false;
                        paused = false;
                        lastError = exception.toString();
                        lock.notifyAll();
                    }
                }

                exception.printStackTrace();
            }
        }
    }

    // --------------------------------------------------
    // CLEANUP
    // --------------------------------------------------

    private void closeLine() {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    private void ensureLoaded() {
        if (!loaded || line == null) {
            throw new IllegalStateException(
                    "No audio file has been loaded."
            );
        }
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException(
                    "AudioEngine has been closed."
            );
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }

            generation++;

            closed = true;
            playing = false;
            paused = false;
            loaded = false;

            closeLine();

            audioData = null;

            lock.notifyAll();
        }

        audioThread.interrupt();
    }
}
