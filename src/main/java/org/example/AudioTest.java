
package org.example;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.*;

public class AudioTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("================================");
        System.out.println("       OPREZZION AUDIO TEST");
        System.out.println("================================");

        File wav = new File("test.wav");

        test("Generate test WAV", () -> {
            generateWav(wav);
            check(wav.exists(), "WAV file was not created.");
            check(wav.length() > 1000, "WAV file is unexpectedly small.");
        });

        AudioEngine engine = new AudioEngine();

        try {
            test("Load WAV", () -> {
                engine.load(wav.toPath());
                check(engine.isLoaded(), "Engine did not report loaded.");
                check(engine.getDurationMillis() >= 4900,
                        "Unexpected audio duration.");
            });

            test("Initial playback state", () -> {
                check(!engine.isPlaying(), "Already playing.");
                check(!engine.isPaused(), "Already paused.");
                check(engine.getPositionMillis() == 0,
                        "Position should start at zero.");
            });

            test("Start playback", () -> {
                engine.play();
                Thread.sleep(300);

                check(engine.isPlaying(), "Playback did not start.");
            });

            test("Playback clock advances", () -> {
                long first = engine.getPositionMillis();
                Thread.sleep(400);
                long second = engine.getPositionMillis();

                System.out.println("  Position: " + first
                        + " ms -> " + second + " ms");

                check(second > first,
                        "Playback position did not advance.");
            });

            test("Pause", () -> {
                engine.pause();

                check(engine.isPaused(), "Pause state not detected.");
                check(!engine.isPlaying(), "Still playing.");
            });

            test("Position stays frozen while paused", () -> {
                long first = engine.getPositionMillis();
                Thread.sleep(400);
                long second = engine.getPositionMillis();

                System.out.println("  Position: " + first
                        + " ms -> " + second + " ms");

                check(Math.abs(second - first) <= 30,
                        "Position changed while paused.");
            });

            test("Resume", () -> {
                engine.resume();
                Thread.sleep(300);

                check(engine.isPlaying(), "Resume failed.");
            });

            test("Seek", () -> {
                engine.pause();
                engine.seekMillis(2000);

                long position = engine.getPositionMillis();

                System.out.println("  Seek position: "
                        + position + " ms");

                check(Math.abs(position - 2000) <= 30,
                        "Seek did not reach 2000 ms.");
            });

            test("Playback after seeking", () -> {
                engine.play();
                Thread.sleep(300);

                check(engine.isPlaying(),
                        "Playback failed after seeking.");

                check(engine.getPositionMillis() > 2000,
                        "Position did not advance after seeking.");
            });

            test("Stop and reset", () -> {
                engine.stop();

                check(!engine.isPlaying(), "Still playing.");
                check(engine.getPositionMillis() == 0,
                        "Position did not reset to zero.");
            });

        } finally {
            engine.close();
        }

        System.out.println();
        System.out.println("================================");
        System.out.println("             RESULTS");
        System.out.println("================================");
        System.out.println("PASSED: " + passed);
        System.out.println("FAILED: " + failed);
        System.out.println("================================");

        if (failed > 0) {
            System.out.println("AUDIO ENGINE NEEDS FIXING.");
        } else {
            System.out.println("ALL TESTS PASSED!");
        }
    }

    private static void test(String name, TestAction action) {
        System.out.print("[TEST] " + name + " ... ");

        try {
            action.run();
            passed++;
            System.out.println("PASS");
        } catch (Throwable exception) {
            failed++;
            System.out.println("FAIL");
            System.out.println("       Reason: "
                    + exception.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void generateWav(File file) throws Exception {

        float sampleRate = 44100;
        int durationSeconds = 5;
        int sampleCount = (int) (sampleRate * durationSeconds);

        byte[] audio = new byte[sampleCount * 2];

        for (int i = 0; i < sampleCount; i++) {
            double time = i / sampleRate;

            double fade = Math.min(1.0, Math.min(
                    time / 0.1,
                    (durationSeconds - time) / 0.1
            ));

            double value = Math.sin(
                    2 * Math.PI * 440 * time
            ) * 0.35 * fade;

            short sample = (short) (value * Short.MAX_VALUE);

            audio[i * 2] = (byte) sample;
            audio[i * 2 + 1] = (byte) (sample >> 8);
        }

        AudioFormat format = new AudioFormat(
                sampleRate, 16, 1, true, false
        );

        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(audio),
                format,
                sampleCount
        )) {
            AudioSystem.write(
                    stream,
                    AudioFileFormat.Type.WAVE,
                    file
            );
        }
    }

    @FunctionalInterface
    interface TestAction {
        void run() throws Exception;
    }
}
