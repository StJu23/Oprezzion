package org.example;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import java.io.BufferedWriter;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.util.*;
import java.util.List;

/**
 * OPREZZION — Advanced Chart Editor
 *
 * Java 21
 * Swing + Java2D
 * No external dependencies
 *
 * Uses existing EditorAudioEngine.java.
 *
 * Chart format:
 *
 * time_ms,lane,type,duration_ms
 *
 * Examples:
 *
 * 2015,1,TAP,0
 * 2916,2,HOLD,721
 *
 * Features:
 *
 * - Audio waveform visualization
 * - Adjustable timeline zoom
 * - Click waveform to seek
 * - Drag notes horizontally
 * - Drag notes between lanes
 * - Resize hold notes
 * - Beat snapping
 * - Undo and redo
 * - Slow-motion playback
 * - Automatic chart backup
 */
public final class ChartEditor extends JFrame {

    private static final long serialVersionUID = 1L;

    // ============================================================
    // PROJECT CONFIGURATION
    // ============================================================

    private static final String MUSIC =
            "music/out-the-club.wav";

    private static final String CHART =
            "charts/out-the-club.chart";

    private static final double BPM =
            166.3975;

    private static final long OFFSET_MS =
            212L;

    private static final double BEAT_MS =
            60_000.0 / BPM;

    private static final int HISTORY_LIMIT =
            120;

    // ============================================================
    // INPUT
    // ============================================================

    private static final int[] LANE_KEYS = {
            KeyEvent.VK_A,
            KeyEvent.VK_W,
            KeyEvent.VK_D
    };

    private static final String[] LANE_NAMES = {
            "A / LEFT",
            "W / TOP",
            "D / RIGHT"
    };

    // ============================================================
    // VISUAL STYLE
    // ============================================================

    private static final Color BACK =
            new Color(7, 10, 21);

    private static final Color PANEL =
            new Color(17, 23, 40);

    private static final Color TEXT =
            new Color(238, 246, 255);

    private static final Color MUTED =
            new Color(152, 172, 201);

    private static final Color[] LANE_COLORS = {
            new Color(49, 219, 251),
            new Color(255, 207, 107),
            new Color(255, 94, 186)
    };

    // ============================================================
    // NOTE DATA
    // ============================================================

    private static final class Note {

        final long id;

        long time;
        int lane;
        long duration;

        Note(
                long id,
                long time,
                int lane,
                long duration
        ) {
            this.id = id;
            this.time = time;
            this.lane = lane;
            this.duration = duration;
        }

        Note copy() {
            return new Note(
                    id,
                    time,
                    lane,
                    duration
            );
        }

        boolean hold() {
            return duration > 0;
        }

        long end() {
            return time + duration;
        }
    }

    // ============================================================
    // AUDIO / CHART STATE
    // ============================================================

    private final EditorAudioEngine audio =
            new EditorAudioEngine();

    private final List<Note> notes =
            new ArrayList<>();

    private final Deque<List<Note>> undo =
            new ArrayDeque<>();

    private final Deque<List<Note>> redo =
            new ArrayDeque<>();

    private final boolean[] pressed =
            new boolean[3];

    private final long[] holdStarts = {
            -1L, -1L, -1L
    };

    private long nextId = 1L;
    private long selectedId = -1L;

    private boolean audioReady;
    private boolean closed;
    private boolean dirty;

    private Path songPath;
    private Path chartPath;

    // ============================================================
    // WAVEFORM STATE
    // ============================================================

    private volatile float[] waveform =
            new float[0];

    private volatile double waveformBinsPerMs;

    private volatile String waveformStatus =
            "WAVEFORM LOADING";

    // ============================================================
    // TIMELINE STATE
    // ============================================================

    private double viewHalfMs =
            3500.0;

    private int subdivision =
            8;

    // ============================================================
    // SWING COMPONENTS
    // ============================================================

    private final Timeline timeline =
            new Timeline();

    private final JLabel clock =
            label(
                    "00:00.000",
                    20,
                    TEXT
            );

    private final JLabel info =
            label(
                    "Loading project...",
                    11,
                    MUTED
            );

    private final JLabel count =
            label(
                    "0 NOTES",
                    11,
                    TEXT
            );

    private final JLabel zoomLabel =
            label(
                    "7.0s VIEW",
                    11,
                    TEXT
            );

    private final JLabel snapLabel =
            label(
                    "SNAP 1/8",
                    11,
                    TEXT
            );

    private final JButton play =
            button("PLAY");

    private final JButton save =
            button("SAVE CHART");

    private final JCheckBox snapBox =
            new JCheckBox(
                    "SNAP",
                    true
            );

    private final JComboBox<String> speedBox =
            new JComboBox<>(
                    new String[]{
                            "1.00x",
                            "0.75x",
                            "0.50x",
                            "0.25x"
                    }
            );

    private final javax.swing.Timer refresh;

    private KeyEventDispatcher keys;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ChartEditor() {

        super(
                "OPREZZION  /  ADVANCED CHART EDITOR"
        );

        setDefaultCloseOperation(
                DISPOSE_ON_CLOSE
        );

        setMinimumSize(
                new Dimension(
                        940,
                        680
                )
        );

        setSize(
                1270,
                850
        );

        setLocationRelativeTo(null);

        getContentPane().setBackground(BACK);

        setLayout(
                new BorderLayout()
        );

        add(
                topBar(),
                BorderLayout.NORTH
        );

        add(
                timeline,
                BorderLayout.CENTER
        );

        add(
                bottomBar(),
                BorderLayout.SOUTH
        );

        // --------------------------------------------------------
        // BUTTONS
        // --------------------------------------------------------

        play.addActionListener(
                e -> togglePlayback()
        );

        save.addActionListener(
                e -> saveChart()
        );

        speedBox.setSelectedIndex(0);

        speedBox.addActionListener(
                e -> setSpeed(
                        new double[]{
                                1.0,
                                0.75,
                                0.5,
                                0.25
                        }[speedBox.getSelectedIndex()]
                )
        );

        snapBox.setOpaque(false);
        snapBox.setForeground(TEXT);

        snapBox.addActionListener(
                e -> updateSnapLabel()
        );

        // --------------------------------------------------------
        // KEYBOARD
        // --------------------------------------------------------

        installKeyboard();

        // --------------------------------------------------------
        // WINDOW EVENTS
        // --------------------------------------------------------

        addWindowFocusListener(
                new WindowAdapter() {

                    @Override
                    public void windowLostFocus(WindowEvent e) {

                        Arrays.fill(
                                pressed,
                                false
                        );

                        Arrays.fill(
                                holdStarts,
                                -1L
                        );
                    }
                }
        );

        addWindowListener(
                new WindowAdapter() {

                    @Override
                    public void windowClosed(WindowEvent e) {

                        closed = true;

                        refresh.stop();

                        KeyboardFocusManager
                                .getCurrentKeyboardFocusManager()
                                .removeKeyEventDispatcher(
                                        keys
                                );

                        audio.close();
                    }
                }
        );

        // --------------------------------------------------------
        // LOAD
        // --------------------------------------------------------

        openProject();

        // --------------------------------------------------------
        // UPDATE TIMER
        // --------------------------------------------------------

        refresh =
                new javax.swing.Timer(
                        25,
                        e -> tick()
                );

        refresh.start();

        setVisible(true);

        SwingUtilities.invokeLater(
                timeline::requestFocusInWindow
        );
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private static JLabel label(
            String text,
            int size,
            Color color
    ) {

        JLabel result =
                new JLabel(text);

        result.setForeground(color);

        result.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        size
                )
        );

        return result;
    }

    private static JButton button(String title) {

        JButton result =
                new JButton(title);

        result.setFocusable(false);
        result.setFocusPainted(false);

        result.setForeground(TEXT);

        result.setBackground(
                new Color(
                        36,
                        48,
                        72
                )
        );

        result.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        11
                )
        );

        result.setBorder(
                new EmptyBorder(
                        9,
                        15,
                        9,
                        15
                )
        );

        return result;
    }

    // ============================================================
    // TOP BAR
    // ============================================================

    private JPanel topBar() {

        JPanel bar =
                new JPanel(
                        new BorderLayout(
                                15,
                                0
                        )
                );

        bar.setBackground(PANEL);

        bar.setBorder(
                new EmptyBorder(
                        12,
                        16,
                        12,
                        16
                )
        );

        JLabel title =
                label(
                        "OPREZZION  /  MAPPER STUDIO",
                        16,
                        TEXT
                );

        JPanel center =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                18,
                                2
                        )
                );

        center.setOpaque(false);

        center.add(clock);
        center.add(count);
        center.add(zoomLabel);
        center.add(snapLabel);

        JPanel controls =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                7,
                                0
                        )
                );

        controls.setOpaque(false);

        controls.add(snapBox);
        controls.add(speedBox);
        controls.add(play);
        controls.add(save);

        bar.add(
                title,
                BorderLayout.WEST
        );

        bar.add(
                center,
                BorderLayout.CENTER
        );

        bar.add(
                controls,
                BorderLayout.EAST
        );

        return bar;
    }

    // ============================================================
    // BOTTOM BAR
    // ============================================================

    private JPanel bottomBar() {

        JPanel bar =
                new JPanel(
                        new BorderLayout(
                                10,
                                6
                        )
                );

        bar.setBackground(PANEL);

        bar.setBorder(
                new EmptyBorder(
                        10,
                        15,
                        12,
                        15
                )
        );

        JLabel help =
                label(
                        "A/W/D TAP  ·  SHIFT+LANE HOLD  ·  DRAG MOVE  ·  DRAG HOLD END RESIZE  ·  WHEEL ZOOM  ·  CLICK WAVEFORM SEEK",
                        10,
                        MUTED
                );

        JLabel help2 =
                label(
                        "SPACE PLAY  ·  ←/→ SEEK  ·  Q SNAP  ·  [/] GRID  ·  DELETE REMOVE  ·  CTRL+Z/Y UNDO/REDO  ·  CTRL+S SAVE",
                        10,
                        MUTED
                );

        JPanel lines =
                new JPanel(
                        new GridLayout(
                                2,
                                1,
                                0,
                                4
                        )
                );

        lines.setOpaque(false);

        lines.add(help);
        lines.add(help2);

        bar.add(
                lines,
                BorderLayout.CENTER
        );

        bar.add(
                info,
                BorderLayout.SOUTH
        );

        return bar;
    }

    // ============================================================
    // PROJECT LOADING
    // ============================================================

    private void openProject() {

        try {

            songPath =
                    resourcePath(
                            MUSIC,
                            false
                    );

            chartPath =
                    resourcePath(
                            CHART,
                            true
                    );

            if (
                    !Files.isRegularFile(
                            songPath
                    )
            ) {

                throw new IOException(
                        "Missing " + songPath
                );
            }

            audio.load(songPath);

            audioReady = true;

            loadChart();

            status(
                    "READY · "
                            + notes.size()
                            + " notes · click a note, drag it, or record new ones"
            );

            // ----------------------------------------------------
            // WAVEFORM ANALYSIS
            // ----------------------------------------------------
            //
            // This runs outside Swing's event thread.
            // The waveform is stored as peak data, not full audio.
            //
            // ----------------------------------------------------

            new SwingWorker<float[], Void>() {

                private double binsPerMs;

                @Override
                protected float[] doInBackground()
                        throws Exception {

                    WaveData data =
                            analyzeWav(
                                    songPath
                            );

                    binsPerMs =
                            data.binsPerMs();

                    return data.peaks();
                }

                @Override
                protected void done() {

                    if (closed) {
                        return;
                    }

                    try {

                        waveform = get();

                        waveformBinsPerMs =
                                binsPerMs;

                        waveformStatus =
                                "WAVEFORM READY";

                    } catch (Exception ex) {

                        waveformStatus =
                                "WAVEFORM UNAVAILABLE";

                        status(
                                "Audio works, but waveform couldn't load: "
                                        + ex.getMessage()
                        );
                    }

                    timeline.repaint();
                }

            }.execute();

        } catch (Exception ex) {

            status(
                    "LOAD ERROR: "
                            + ex.getMessage()
            );

            ex.printStackTrace();
        }
    }

    // ============================================================
    // RESOURCE PATHS
    // ============================================================

    private Path resourcePath(
            String resource,
            boolean create
    ) throws IOException {

        Path root =
                Path.of(
                        System.getProperty(
                                "user.dir"
                        )
                ).toAbsolutePath();

        for (
                int i = 0;
                i < 8 && root != null;
                i++, root = root.getParent()
        ) {

            Path folder =
                    root.resolve(
                            "src/main/resources"
                    );

            if (
                    Files.isDirectory(folder)
            ) {

                Path candidate =
                        folder.resolve(
                                resource
                        );

                if (
                        Files.exists(candidate)
                                || create
                ) {

                    if (create) {

                        Files.createDirectories(
                                candidate.getParent()
                        );
                    }

                    return candidate;
                }
            }
        }

        Path fallback =
                Path.of(
                                "src/main/resources"
                        )
                        .toAbsolutePath()
                        .resolve(resource);

        if (create) {

            Files.createDirectories(
                    fallback.getParent()
            );
        }

        return fallback;
    }

    // ============================================================
    // LOAD CHART
    // ============================================================

    private void loadChart() throws IOException {

        notes.clear();
        undo.clear();
        redo.clear();

        selectedId = -1L;

        if (
                !Files.exists(chartPath)
        ) {
            return;
        }

        int lineNo = 0;

        for (
                String raw
                : Files.readAllLines(
                chartPath,
                StandardCharsets.UTF_8
        )
        ) {

            lineNo++;

            String line =
                    raw.split(
                            "#",
                            2
                    )[0].trim();

            if (
                    line.isEmpty()
                            || line.startsWith("//")
                            || line.toLowerCase(
                            Locale.ROOT
                    ).startsWith("time_ms")
            ) {
                continue;
            }

            String[] p =
                    line.split(
                            ",",
                            -1
                    );

            try {

                if (p.length != 4) {

                    throw new IllegalArgumentException(
                            "Expected 4 columns"
                    );
                }

                long time =
                        Long.parseLong(
                                p[0].trim()
                        );

                int lane =
                        Integer.parseInt(
                                p[1].trim()
                        );

                String type =
                        p[2]
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );

                long duration =
                        Long.parseLong(
                                p[3].trim()
                        );

                if (
                        time < 0
                                || lane < 0
                                || lane > 2
                                || duration < 0
                ) {

                    throw new IllegalArgumentException(
                            "Invalid time, lane or duration"
                    );
                }

                if (type.equals("TAP")) {

                    if (duration != 0) {

                        throw new IllegalArgumentException(
                                "TAP duration must be 0"
                        );
                    }

                } else if (
                        type.equals("HOLD")
                ) {

                    if (duration == 0) {

                        throw new IllegalArgumentException(
                                "HOLD needs duration > 0"
                        );
                    }

                } else {

                    throw new IllegalArgumentException(
                            "Unknown note type " + type
                    );
                }

                notes.add(
                        new Note(
                                nextId++,
                                time,
                                lane,
                                duration
                        )
                );

            } catch (RuntimeException ex) {

                throw new IOException(
                        "Chart line "
                                + lineNo
                                + ": "
                                + ex.getMessage(),
                        ex
                );
            }
        }

        sort();

        dirty = false;
    }

    // ============================================================
    // WAVEFORM DATA
    // ============================================================

    private record WaveData(
            float[] peaks,
            double binsPerMs
    ) {}

    /**
     * Reads audio and calculates peak values.
     *
     * Uses one peak for every 256 source frames.
     *
     * Example:
     *
     * 48000 Hz / 256 = 187.5 waveform bins per second.
     *
     * A two-minute song therefore requires only about
     * 22,500 floating-point values for waveform display.
     *
     * This avoids storing another full decoded audio copy
     * inside the chart editor.
     */
    private static WaveData analyzeWav(
            Path path
    ) throws Exception {

        try (
                AudioInputStream original =
                        AudioSystem.getAudioInputStream(
                                path.toFile()
                        )
        ) {

            AudioFormat in =
                    original.getFormat();

            AudioFormat pcm =
                    new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            in.getSampleRate(),
                            16,
                            in.getChannels(),
                            in.getChannels() * 2,
                            in.getSampleRate(),
                            false
                    );

            try (
                    AudioInputStream decoded =
                            AudioSystem.getAudioInputStream(
                                    pcm,
                                    original
                            )
            ) {

                int channels =
                        pcm.getChannels();

                int stride =
                        pcm.getFrameSize();

                final int framesPerBin =
                        256;

                byte[] buffer =
                        new byte[
                                stride * 4096
                                ];

                float[] values =
                        new float[
                                32768
                                ];

                int length = 0;

                float peak = 0;

                int binFrames = 0;

                int read;

                while (
                        (
                                read =
                                        decoded.read(
                                                buffer
                                        )
                        ) != -1
                ) {

                    int frames =
                            read / stride;

                    for (
                            int frame = 0;
                            frame < frames;
                            frame++
                    ) {

                        int off =
                                frame * stride;

                        for (
                                int channel = 0;
                                channel < channels;
                                channel++
                        ) {

                            int sampleOff =
                                    off
                                            + channel * 2;

                            int sample =
                                    (short) (
                                            (
                                                    (
                                                            buffer[sampleOff + 1]
                                                                    & 0xff
                                                    ) << 8
                                            )
                                                    | (
                                                    buffer[sampleOff]
                                                            & 0xff
                                            )
                                    );

                            peak =
                                    Math.max(
                                            peak,
                                            Math.abs(sample)
                                                    / 32768f
                                    );
                        }

                        binFrames++;

                        if (
                                binFrames
                                        == framesPerBin
                        ) {

                            if (
                                    length
                                            == values.length
                            ) {

                                values =
                                        Arrays.copyOf(
                                                values,
                                                values.length * 2
                                        );
                            }

                            values[length++] =
                                    peak;

                            peak = 0;

                            binFrames = 0;
                        }
                    }
                }

                if (
                        binFrames != 0
                ) {

                    if (
                            length == values.length
                    ) {

                        values =
                                Arrays.copyOf(
                                        values,
                                        values.length + 1
                                );
                    }

                    values[length++] =
                            peak;
                }

                return new WaveData(
                        Arrays.copyOf(
                                values,
                                length
                        ),

                        pcm.getSampleRate()
                                / (
                                1000.0
                                        * framesPerBin
                        )
                );
            }
        }
    }

    // ============================================================
    // KEYBOARD
    // ============================================================

    private void installKeyboard() {

        keys =
                e -> {

                    if (
                            closed
                                    || !isActive()
                    ) {
                        return false;
                    }

                    int code =
                            e.getKeyCode();

                    if (
                            e.getID()
                                    == KeyEvent.KEY_PRESSED
                    ) {

                        // ------------------------------------------------
                        // SAVE
                        // ------------------------------------------------

                        if (
                                e.isControlDown()
                                        && code
                                        == KeyEvent.VK_S
                        ) {

                            saveChart();

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // UNDO / REDO
                        // ------------------------------------------------

                        if (
                                e.isControlDown()
                                        && code
                                        == KeyEvent.VK_Z
                        ) {

                            undo();

                            return consume(e);
                        }

                        if (
                                e.isControlDown()
                                        && code
                                        == KeyEvent.VK_Y
                        ) {

                            redo();

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // SELECTION
                        // ------------------------------------------------

                        if (
                                code
                                        == KeyEvent.VK_ESCAPE
                        ) {

                            selectedId = -1L;

                            timeline.repaint();

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // PLAYBACK
                        // ------------------------------------------------

                        if (
                                code
                                        == KeyEvent.VK_SPACE
                        ) {

                            togglePlayback();

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // SEEK
                        // ------------------------------------------------

                        if (
                                code
                                        == KeyEvent.VK_HOME
                        ) {

                            seek(0);

                            return consume(e);
                        }

                        if (
                                code == KeyEvent.VK_LEFT
                                        || code == KeyEvent.VK_RIGHT
                        ) {

                            long step =
                                    e.isControlDown()
                                            ? 5000L
                                            : e.isShiftDown()
                                            ? 50L
                                            : 500L;

                            seek(
                                    position()
                                            + (
                                            code
                                                    == KeyEvent.VK_LEFT
                                                    ? -step
                                                    : step
                                    )
                            );

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // DELETE
                        // ------------------------------------------------

                        if (
                                code == KeyEvent.VK_DELETE
                                        || code == KeyEvent.VK_BACK_SPACE
                        ) {

                            deleteSelectedOrNearest();

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // PLAYBACK SPEED
                        // ------------------------------------------------

                        if (
                                code >= KeyEvent.VK_1
                                        && code <= KeyEvent.VK_4
                        ) {

                            speedBox.setSelectedIndex(
                                    code - KeyEvent.VK_1
                            );

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // SNAP
                        // ------------------------------------------------

                        if (
                                code == KeyEvent.VK_Q
                        ) {

                            snapBox.setSelected(
                                    !snapBox.isSelected()
                            );

                            updateSnapLabel();

                            return consume(e);
                        }

                        if (
                                code == KeyEvent.VK_OPEN_BRACKET
                        ) {

                            changeSubdivision(-1);

                            return consume(e);
                        }

                        if (
                                code == KeyEvent.VK_CLOSE_BRACKET
                        ) {

                            changeSubdivision(1);

                            return consume(e);
                        }

                        // ------------------------------------------------
                        // RECORD NOTES
                        // ------------------------------------------------

                        int lane =
                                laneIndex(code);

                        if (
                                lane >= 0
                                        && !e.isControlDown()
                                        && !e.isAltDown()
                        ) {

                            if (
                                    !pressed[lane]
                            ) {

                                pressed[lane] = true;

                                long time =
                                        noteTime(
                                                position()
                                        );

                                if (
                                        e.isShiftDown()
                                ) {

                                    holdStarts[lane] =
                                            time;

                                    status(
                                            "Recording HOLD "
                                                    + LANE_NAMES[lane]
                                    );

                                } else {

                                    place(
                                            time,
                                            lane,
                                            0L
                                    );
                                }
                            }

                            return consume(e);
                        }

                    } else if (
                            e.getID()
                                    == KeyEvent.KEY_RELEASED
                    ) {

                        int lane =
                                laneIndex(code);

                        if (
                                lane >= 0
                        ) {

                            pressed[lane] = false;

                            if (
                                    holdStarts[lane]
                                            >= 0L
                            ) {

                                long begin =
                                        holdStarts[lane];

                                holdStarts[lane] = -1L;

                                long length =
                                        Math.max(
                                                0L,

                                                noteTime(
                                                        position()
                                                )
                                                        - begin
                                        );

                                place(
                                        begin,

                                        lane,

                                        length < 60L
                                                ? 0L
                                                : length
                                );
                            }

                            return consume(e);
                        }
                    }

                    return false;
                };

        KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(
                        keys
                );
    }

    private static boolean consume(
            KeyEvent e
    ) {

        e.consume();

        return true;
    }

    private static int laneIndex(
            int key
    ) {

        for (
                int i = 0;
                i < 3;
                i++
        ) {

            if (
                    key == LANE_KEYS[i]
            ) {

                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // AUDIO CONTROLS
    // ============================================================

    private long position() {

        return audioReady
                ? audio.getPositionMillis()
                : 0L;
    }

    private long duration() {

        return audioReady
                ? audio.getDurationMillis()
                : 0L;
    }

    private void seek(
            long ms
    ) {

        if (
                audioReady
        ) {

            audio.seekMillis(
                    Math.max(
                            0L,

                            Math.min(
                                    duration(),
                                    ms
                            )
                    )
            );
        }
    }

    private void togglePlayback() {

        if (audioReady) {

            audio.togglePause();
        }
    }

    private void setSpeed(
            double rate
    ) {

        if (audioReady) {

            audio.setPlaybackRate(rate);
        }
    }

    // ============================================================
    // SNAP
    // ============================================================

    private void updateSnapLabel() {

        snapLabel.setText(
                snapBox.isSelected()
                        ? "SNAP 1/" + subdivision
                        : "SNAP OFF"
        );
    }

    private void changeSubdivision(
            int direction
    ) {

        int[] sizes = {
                1,
                2,
                4,
                8,
                16,
                32,
                64
        };

        int index = 3;

        for (
                int i = 0;
                i < sizes.length;
                i++
        ) {

            if (
                    sizes[i] == subdivision
            ) {

                index = i;
            }
        }

        subdivision =
                sizes[
                        Math.max(
                                0,

                                Math.min(
                                        sizes.length - 1,
                                        index + direction
                                )
                        )
                        ];

        snapBox.setSelected(true);

        updateSnapLabel();
    }

    private long noteTime(
            long raw
    ) {

        if (
                !snapBox.isSelected()
        ) {

            return Math.max(
                    0L,
                    raw
            );
        }

        double step =
                BEAT_MS
                        * 4.0
                        / subdivision;

        return Math.max(
                0L,

                Math.round(
                        OFFSET_MS
                                + Math.round(
                                (
                                        raw - OFFSET_MS
                                )
                                        / step
                        )
                                * step
                )
        );
    }

    // ============================================================
    // HISTORY
    // ============================================================

    private List<Note> snapshot() {

        List<Note> copy =
                new ArrayList<>(
                        notes.size()
                );

        for (
                Note note : notes
        ) {

            copy.add(
                    note.copy()
            );
        }

        return copy;
    }

    private void beforeEdit() {

        undo.push(
                snapshot()
        );

        if (
                undo.size()
                        > HISTORY_LIMIT
        ) {

            undo.removeLast();
        }

        redo.clear();
    }

    private void restore(
            List<Note> data
    ) {

        notes.clear();

        for (
                Note note : data
        ) {

            notes.add(
                    note.copy()
            );
        }

        sort();

        if (
                findSelected() == null
        ) {

            selectedId = -1L;
        }

        dirty = true;
    }

    private void undo() {

        if (
                undo.isEmpty()
        ) {

            status(
                    "Nothing to undo"
            );

            return;
        }

        redo.push(
                snapshot()
        );

        restore(
                undo.pop()
        );

        status("UNDO");
    }

    private void redo() {

        if (
                redo.isEmpty()
        ) {

            status(
                    "Nothing to redo"
            );

            return;
        }

        undo.push(
                snapshot()
        );

        restore(
                redo.pop()
        );

        status("REDO");
    }

    // ============================================================
    // NOTE MANAGEMENT
    // ============================================================

    private Note findSelected() {

        for (
                Note note : notes
        ) {

            if (
                    note.id == selectedId
            ) {

                return note;
            }
        }

        return null;
    }

    private void sort() {

        notes.sort(
                Comparator

                        .comparingLong(
                                (Note n) -> n.time
                        )

                        .thenComparingInt(
                                n -> n.lane
                        )
        );
    }

    private void place(
            long time,
            int lane,
            long length
    ) {

        if (
                !audioReady
        ) {

            return;
        }

        beforeEdit();

        Note note =
                new Note(
                        nextId++,
                        time,
                        lane,
                        length
                );

        notes.add(note);

        sort();

        selectedId =
                note.id;

        dirty = true;

        status(
                (
                        note.hold()
                                ? "HOLD "
                                + length
                                + "ms"
                                : "TAP"
                )
                        + " at "
                        + timeString(time)
        );
    }

    private void removeNote(
            Note note
    ) {

        if (
                note == null
        ) {

            status(
                    "No note selected / near playhead"
            );

            return;
        }

        beforeEdit();

        notes.remove(note);

        if (
                selectedId == note.id
        ) {

            selectedId = -1L;
        }

        dirty = true;

        status(
                "Deleted "
                        + timeString(note.time)
                        + " · CTRL+Z restores it"
        );
    }

    private void deleteSelectedOrNearest() {

        Note target =
                findSelected();

        if (
                target == null
        ) {

            long here =
                    position();

            long best =
                    1001L;

            for (
                    Note note : notes
            ) {

                long distance =
                        note.hold()
                                && here >= note.time
                                && here <= note.end()

                                ? 0L

                                : Math.min(
                                Math.abs(
                                        here - note.time
                                ),

                                Math.abs(
                                        here - note.end()
                                )
                        );

                if (
                        distance < best
                ) {

                    best = distance;

                    target = note;
                }
            }
        }

        removeNote(target);
    }

    // ============================================================
    // SAVE CHART
    // ============================================================

    /**
     * Saves using a temporary file.
     *
     * Existing chart is copied to:
     *
     * out-the-club.chart.bak
     *
     * before replacing it.
     */
    private void saveChart() {

        if (
                chartPath == null
        ) {

            status(
                    "No chart path"
            );

            return;
        }

        Path temp = null;

        try {

            Files.createDirectories(
                    chartPath.getParent()
            );

            sort();

            temp =
                    Files.createTempFile(
                            chartPath.getParent(),
                            "oprezzion-chart-",
                            ".tmp"
                    );

            try (
                    BufferedWriter out =
                            Files.newBufferedWriter(
                                    temp,
                                    StandardCharsets.UTF_8
                            )
            ) {

                out.write(
                        "# OPREZZION CHART\n"
                                + "# OUT THE CLUB - RomancePlanet\n"
                                + "# BPM: 166.3975\n"
                                + "# Offset: 212 ms\n"
                                + "# time_ms,lane,type,duration_ms\n\n"
                );

                for (
                        Note note : notes
                ) {

                    out.write(
                            note.time
                                    + ","
                                    + note.lane
                                    + ","
                                    + (
                                    note.hold()
                                            ? "HOLD"
                                            : "TAP"
                            )
                                    + ","
                                    + note.duration
                    );

                    out.newLine();
                }
            }

            // ----------------------------------------------------
            // BACKUP EXISTING CHART
            // ----------------------------------------------------

            if (
                    Files.exists(
                            chartPath
                    )
            ) {

                Files.copy(
                        chartPath,

                        chartPath.resolveSibling(
                                chartPath.getFileName()
                                        + ".bak"
                        ),

                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            // ----------------------------------------------------
            // REPLACE CHART
            // ----------------------------------------------------

            try {

                Files.move(
                        temp,
                        chartPath,

                        StandardCopyOption.ATOMIC_MOVE,

                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (
                    AtomicMoveNotSupportedException ignored
            ) {

                Files.move(
                        temp,
                        chartPath,

                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            dirty = false;

            status(
                    "SAVED "
                            + notes.size()
                            + " notes · previous save in .chart.bak"
            );

        } catch (
                IOException ex
        ) {

            status(
                    "SAVE ERROR: "
                            + ex.getMessage()
            );

            ex.printStackTrace();

        } finally {

            if (
                    temp != null
            ) {

                try {

                    Files.deleteIfExists(
                            temp
                    );

                } catch (
                        IOException ignored
                ) {
                    // Cleanup only.
                }
            }
        }
    }

    // ============================================================
    // UI UPDATE
    // ============================================================

    private void tick() {

        if (
                closed
        ) {
            return;
        }

        clock.setText(
                timeString(
                        position()
                )
        );

        count.setText(
                notes.size()
                        + " NOTES"
                        + (
                        dirty
                                ? " *"
                                : ""
                )
        );

        zoomLabel.setText(
                String.format(
                        Locale.ROOT,
                        "%.1fs VIEW",
                        viewHalfMs
                                * 2.0
                                / 1000.0
                )
        );

        play.setText(
                audioReady
                        && audio.isPlaying()
                        ? "PAUSE"
                        : "PLAY"
        );

        timeline.repaint();
    }

    private void status(
            String message
    ) {

        info.setText(message);
    }

    private static String timeString(
            long ms
    ) {

        ms =
                Math.max(
                        0L,
                        ms
                );

        return String.format(
                Locale.ROOT,

                "%02d:%02d.%03d",

                ms / 60_000L,

                ms / 1000L % 60L,

                ms % 1000L
        );
    }

    // ============================================================
    // TIMELINE
    // ============================================================

    private final class Timeline extends JPanel {

        private static final long serialVersionUID =
                1L;

        private static final int WAVE_TOP =
                46;

        private static final int WAVE_HEIGHT =
                126;

        private static final int LANE_TOP =
                220;

        // --------------------------------------------------------
        // DRAGGING STATE
        // --------------------------------------------------------

        private long dragId = -1L;

        private boolean resizing;
        private boolean dragging;
        private boolean changed;

        private long dragStartTime;

        private int dragStartLane;

        private long dragStartDuration;

        private int mouseDownX;
        private int mouseDownY;

        private List<Note> dragBefore;

        // ========================================================
        // CONSTRUCTOR
        // ========================================================

        Timeline() {

            setBackground(BACK);

            setFocusable(true);

            MouseAdapter mouse =
                    new MouseAdapter() {

                        // ----------------------------------------
                        // MOUSE PRESS
                        // ----------------------------------------

                        @Override
                        public void mousePressed(
                                MouseEvent e
                        ) {

                            requestFocusInWindow();

                            long cursor =
                                    position();

                            Note hit =
                                    hitTest(
                                            e.getX(),
                                            e.getY(),
                                            cursor
                                    );

                            // ------------------------------------
                            // RIGHT CLICK DELETE
                            // ------------------------------------

                            if (
                                    SwingUtilities
                                            .isRightMouseButton(e)
                            ) {

                                if (
                                        hit != null
                                ) {

                                    removeNote(hit);
                                }

                                return;
                            }

                            if (
                                    !SwingUtilities
                                            .isLeftMouseButton(e)
                            ) {
                                return;
                            }

                            // ------------------------------------
                            // SELECT / BEGIN DRAG
                            // ------------------------------------

                            if (
                                    hit != null
                            ) {

                                selectedId =
                                        hit.id;

                                dragId =
                                        hit.id;

                                dragStartTime =
                                        hit.time;

                                dragStartLane =
                                        hit.lane;

                                dragStartDuration =
                                        hit.duration;

                                mouseDownX =
                                        e.getX();

                                mouseDownY =
                                        e.getY();

                                dragBefore =
                                        snapshot();

                                dragging = false;
                                changed = false;

                                // --------------------------------
                                // HOLD-END RESIZE HANDLE
                                // --------------------------------

                                resizing =
                                        hit.hold()

                                                && Math.abs(
                                                e.getX()
                                                        - xAt(
                                                        hit.end(),
                                                        cursor
                                                )
                                        ) <= 16.0;

                                status(
                                        (
                                                resizing
                                                        ? "Resize HOLD"
                                                        : "Drag note"
                                        )
                                                + " · "
                                                + timeString(
                                                hit.time
                                        )
                                );

                            } else {

                                selectedId = -1L;
                                dragId = -1L;

                                // --------------------------------
                                // CLICK WAVEFORM TO SEEK
                                // --------------------------------

                                if (
                                        e.getY()
                                                >= WAVE_TOP

                                                && e.getY()
                                                <= WAVE_TOP
                                                + WAVE_HEIGHT
                                ) {

                                    seek(
                                            timeAt(
                                                    e.getX(),
                                                    cursor
                                            )
                                    );
                                }
                            }
                        }

                        // ----------------------------------------
                        // MOUSE DRAG
                        // ----------------------------------------

                        @Override
                        public void mouseDragged(
                                MouseEvent e
                        ) {

                            if (
                                    dragId < 0L
                            ) {

                                return;
                            }

                            Note note =
                                    findSelected();

                            if (
                                    note == null
                                            || note.id != dragId
                            ) {

                                return;
                            }

                            int deltaX =
                                    e.getX()
                                            - mouseDownX;

                            int deltaY =
                                    e.getY()
                                            - mouseDownY;

                            if (
                                    !dragging

                                            && Math.hypot(
                                            deltaX,
                                            deltaY
                                    ) < 4.0
                            ) {

                                return;
                            }

                            dragging = true;

                            long deltaMs =
                                    Math.round(
                                            deltaX
                                                    / pixelsPerMs()
                                    );

                            // ------------------------------------
                            // RESIZE HOLD
                            // ------------------------------------

                            if (
                                    resizing
                            ) {

                                long proposedEnd =
                                        noteTime(
                                                dragStartTime
                                                        + dragStartDuration
                                                        + deltaMs
                                        );

                                note.duration =
                                        Math.max(
                                                1L,

                                                proposedEnd
                                                        - note.time
                                        );

                            } else {

                                // --------------------------------
                                // MOVE TIME
                                // --------------------------------

                                note.time =
                                        Math.max(
                                                0L,

                                                noteTime(
                                                        dragStartTime
                                                                + deltaMs
                                                )
                                        );

                                // --------------------------------
                                // MOVE LANE
                                // --------------------------------

                                note.lane =
                                        nearestLane(
                                                e.getY()
                                        );
                            }

                            changed =
                                    note.time
                                            != dragStartTime

                                            || note.lane
                                            != dragStartLane

                                            || note.duration
                                            != dragStartDuration;

                            if (
                                    changed
                            ) {

                                status(
                                        (
                                                resizing
                                                        ? "HOLD END "
                                                        : "NOTE "
                                        )
                                                + timeString(
                                                resizing
                                                        ? note.end()
                                                        : note.time
                                        )
                                );
                            }

                            repaint();
                        }

                        // ----------------------------------------
                        // MOUSE RELEASE
                        // ----------------------------------------

                        @Override
                        public void mouseReleased(
                                MouseEvent e
                        ) {

                            if (
                                    dragId >= 0L
                                            && dragging
                                            && changed
                            ) {

                                undo.push(
                                        dragBefore
                                );

                                if (
                                        undo.size()
                                                > HISTORY_LIMIT
                                ) {

                                    undo.removeLast();
                                }

                                redo.clear();

                                dirty = true;

                                sort();

                                status(
                                        "Edited note · CTRL+Z to undo"
                                );
                            }

                            dragId = -1L;

                            dragBefore = null;

                            dragging = false;

                            changed = false;
                        }

                        // ----------------------------------------
                        // MOUSE WHEEL ZOOM
                        // ----------------------------------------

                        @Override
                        public void mouseWheelMoved(
                                MouseWheelEvent e
                        ) {

                            double factor =
                                    Math.pow(
                                            1.25,

                                            e.getPreciseWheelRotation()
                                    );

                            viewHalfMs =
                                    Math.max(
                                            350.0,

                                            Math.min(
                                                    25_000.0,

                                                    viewHalfMs
                                                            * factor
                                            )
                                    );

                            repaint();
                        }
                    };

            addMouseListener(mouse);

            addMouseMotionListener(mouse);

            addMouseWheelListener(mouse);
        }

        // ========================================================
        // COORDINATE HELPERS
        // ========================================================

        private int laneY(
                int lane
        ) {

            int bottom =
                    Math.max(
                            LANE_TOP + 160,

                            getHeight() - 100
                    );

            return LANE_TOP

                    + (
                    bottom - LANE_TOP
            )
                    * lane
                    / 2;
        }

        private int nearestLane(
                int y
        ) {

            int best = 0;

            for (
                    int i = 1;
                    i < 3;
                    i++
            ) {

                if (
                        Math.abs(
                                y - laneY(i)
                        )

                                < Math.abs(
                                y - laneY(best)
                        )
                ) {

                    best = i;
                }
            }

            return best;
        }

        private double pixelsPerMs() {

            return Math.max(
                    50,

                    getWidth() - 130
            )

                    / (
                    viewHalfMs
                            * 2.0
            );
        }

        private double xAt(
                long ms,
                long cursor
        ) {

            return getWidth()
                    / 2.0

                    + (
                    ms - cursor
            )
                    * pixelsPerMs();
        }

        private long timeAt(
                double x,
                long cursor
        ) {

            return Math.max(
                    0L,

                    Math.round(
                            cursor

                                    + (
                                    x
                                            - getWidth() / 2.0
                            )
                                    / pixelsPerMs()
                    )
            );
        }

        // ========================================================
        // HIT TESTING
        // ========================================================

        private Note hitTest(
                int mx,
                int my,
                long cursor
        ) {

            Note best = null;

            double bestDist =
                    Double.POSITIVE_INFINITY;

            for (
                    Note note : notes
            ) {

                double dy =
                        Math.abs(
                                my - laneY(
                                        note.lane
                                )
                        );

                if (
                        dy > 24.0
                ) {
                    continue;
                }

                double x1 =
                        xAt(
                                note.time,
                                cursor
                        );

                double x2 =
                        xAt(
                                note.end(),
                                cursor
                        );

                double dx;

                if (
                        note.hold()
                ) {

                    double left =
                            Math.min(x1, x2);

                    double right =
                            Math.max(x1, x2);

                    dx =
                            Math.max(
                                    0.0,

                                    Math.max(
                                            left - mx,

                                            mx - right
                                    )
                            );

                } else {

                    dx =
                            Math.abs(
                                    mx - x1
                            );
                }

                if (
                        dx > 21.0
                ) {
                    continue;
                }

                double dist =
                        Math.hypot(
                                dx,
                                dy
                        );

                if (
                        dist < bestDist
                ) {

                    bestDist = dist;

                    best = note;
                }
            }

            return best;
        }

        // ========================================================
        // PAINT
        // ========================================================

        @Override
        protected void paintComponent(
                Graphics graphics
        ) {

            super.paintComponent(graphics);

            Graphics2D g =
                    (Graphics2D)
                            graphics.create();

            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,

                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,

                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            int w =
                    getWidth();

            int h =
                    getHeight();

            long now =
                    position();

            g.setPaint(
                    new GradientPaint(
                            0,
                            0,

                            new Color(
                                    14,
                                    19,
                                    36
                            ),

                            w,
                            h,

                            BACK
                    )
            );

            g.fillRect(
                    0,
                    0,
                    w,
                    h
            );

            drawWaveform(
                    g,
                    w,
                    now
            );

            drawGrid(
                    g,
                    w,
                    h,
                    now
            );

            drawLanes(
                    g,
                    w
            );

            drawNotes(
                    g,
                    w,
                    now
            );

            drawHeld(
                    g,
                    now
            );

            drawCursor(
                    g,
                    h,
                    now
            );

            g.setColor(MUTED);

            g.setFont(
                    new Font(
                            "Monospaced",
                            Font.PLAIN,
                            10
                    )
            );

            g.drawString(
                    "WHEEL: ZOOM  |  CLICK WAVEFORM: SEEK  |  LEFT DRAG: MOVE  |  HOLD END: RESIZE",
                    18,
                    h - 22
            );

            g.dispose();
        }

        // ========================================================
        // WAVEFORM RENDERING
        // ========================================================

        private void drawWaveform(
                Graphics2D g,
                int w,
                long now
        ) {

            g.setColor(
                    new Color(
                            17,
                            27,
                            49
                    )
            );

            g.fillRoundRect(
                    16,

                    WAVE_TOP,

                    w - 32,

                    WAVE_HEIGHT,

                    12,

                    12
            );

            g.setColor(MUTED);

            g.setFont(
                    new Font(
                            "Monospaced",
                            Font.BOLD,
                            10
                    )
            );

            g.drawString(
                    "AUDIO WAVEFORM  ·  "
                            + waveformStatus,

                    26,

                    WAVE_TOP + 15
            );

            float[] peaks =
                    waveform;

            double binsPerMs =
                    waveformBinsPerMs;

            if (
                    peaks.length == 0
                            || binsPerMs <= 0.0
            ) {

                return;
            }

            int mid =
                    WAVE_TOP
                            + WAVE_HEIGHT / 2
                            + 9;

            int maxHeight =
                    WAVE_HEIGHT / 2
                            - 24;

            g.setColor(
                    new Color(
                            79,
                            205,
                            239,
                            175
                    )
            );

            for (
                    int x = 18;
                    x < w - 18;
                    x++
            ) {

                long t0 =
                        timeAt(
                                x,
                                now
                        );

                long t1 =
                        timeAt(
                                x + 1,
                                now
                        );

                int b0 =
                        Math.max(
                                0,

                                (int) Math.floor(
                                        t0 * binsPerMs
                                )
                        );

                int b1 =
                        Math.min(
                                peaks.length,

                                Math.max(
                                        b0 + 1,

                                        (int) Math.ceil(
                                                t1 * binsPerMs
                                        )
                                )
                        );

                if (
                        b0 >= peaks.length
                ) {

                    continue;
                }

                float peak = 0f;

                for (
                        int i = b0;
                        i < b1;
                        i++
                ) {

                    peak =
                            Math.max(
                                    peak,
                                    peaks[i]
                            );
                }

                int amplitude =
                        Math.max(
                                1,

                                (int) (
                                        peak * maxHeight
                                )
                        );

                g.drawLine(
                        x,

                        mid - amplitude,

                        x,

                        mid + amplitude
                );
            }
        }

        // ========================================================
        // BEAT GRID
        // ========================================================

        private void drawGrid(
                Graphics2D g,
                int w,
                int h,
                long now
        ) {

            double step =
                    BEAT_MS / 4.0;

            long first =
                    (long) Math.floor(
                            (
                                    now
                                            - viewHalfMs
                                            - OFFSET_MS
                            )
                                    / step
                    ) - 1L;

            long last =
                    (long) Math.ceil(
                            (
                                    now
                                            + viewHalfMs
                                            - OFFSET_MS
                            )
                                    / step
                    ) + 1L;

            for (
                    long i = first;
                    i <= last;
                    i++
            ) {

                long time =
                        Math.round(
                                OFFSET_MS
                                        + i * step
                        );

                double x =
                        xAt(
                                time,
                                now
                        );

                if (
                        x < 10.0
                                || x > w - 10.0
                ) {

                    continue;
                }

                boolean beat =
                        Math.floorMod(
                                i,
                                4
                        ) == 0;

                boolean half =
                        Math.floorMod(
                                i,
                                2
                        ) == 0;

                g.setColor(
                        new Color(
                                110,
                                135,
                                179,

                                beat
                                        ? 70
                                        : half
                                        ? 34
                                        : 16
                        )
                );

                g.setStroke(
                        new BasicStroke(
                                beat
                                        ? 1.4f
                                        : 1f
                        )
                );

                g.draw(
                        new Line2D.Double(
                                x,

                                WAVE_TOP
                                        + WAVE_HEIGHT
                                        + 10,

                                x,

                                h - 64
                        )
                );

                if (
                        beat
                ) {

                    g.setColor(MUTED);

                    g.setFont(
                            new Font(
                                    "Monospaced",
                                    Font.PLAIN,
                                    9
                            )
                    );

                    g.drawString(
                            Long.toString(
                                    Math.floorDiv(
                                            i,
                                            4
                                    )
                            ),

                            (int) x + 3,

                            WAVE_TOP
                                    + WAVE_HEIGHT
                                    + 27
                    );
                }
            }
        }

        // ========================================================
        // LANE RENDERING
        // ========================================================

        private void drawLanes(
                Graphics2D g,
                int w
        ) {

            for (
                    int i = 0;
                    i < 3;
                    i++
            ) {

                int y =
                        laneY(i);

                Color c =
                        LANE_COLORS[i];

                g.setStroke(
                        new BasicStroke(
                                17f,

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        new Color(
                                c.getRed(),
                                c.getGreen(),
                                c.getBlue(),
                                22
                        )
                );

                g.drawLine(
                        75,
                        y,

                        w - 25,
                        y
                );

                g.setStroke(
                        new BasicStroke(
                                2f
                        )
                );

                g.setColor(
                        new Color(
                                c.getRed(),
                                c.getGreen(),
                                c.getBlue(),
                                95
                        )
                );

                g.drawLine(
                        75,
                        y,

                        w - 25,
                        y
                );

                g.setColor(c);

                g.setFont(
                        new Font(
                                "SansSerif",
                                Font.BOLD,
                                11
                        )
                );

                g.drawString(
                        LANE_NAMES[i],

                        12,

                        y + 4
                );
            }
        }

        // ========================================================
        // NOTE RENDERING
        // ========================================================

        private void drawNotes(
                Graphics2D g,
                int w,
                long now
        ) {

            for (
                    Note note : notes
            ) {

                double x =
                        xAt(
                                note.time,
                                now
                        );

                double endX =
                        xAt(
                                note.end(),
                                now
                        );

                if (
                        Math.max(
                                x,
                                endX
                        ) < -20.0

                                || Math.min(
                                x,
                                endX
                        ) > w + 20.0
                ) {

                    continue;
                }

                int y =
                        laneY(
                                note.lane
                        );

                Color color =
                        LANE_COLORS[
                                note.lane
                                ];

                boolean chosen =
                        note.id == selectedId;

                // ------------------------------------------------
                // HOLD BODY
                // ------------------------------------------------

                if (
                        note.hold()
                ) {

                    double left =
                            Math.min(
                                    x,
                                    endX
                            );

                    double len =
                            Math.max(
                                    2.0,

                                    Math.abs(
                                            endX - x
                                    )
                            );

                    g.setColor(
                            new Color(
                                    color.getRed(),
                                    color.getGreen(),
                                    color.getBlue(),

                                    chosen
                                            ? 105
                                            : 48
                            )
                    );

                    g.fill(
                            new RoundRectangle2D.Double(
                                    left,

                                    y - 11,

                                    len,

                                    22,

                                    14,

                                    14
                            )
                    );

                    g.setColor(
                            chosen
                                    ? Color.WHITE
                                    : color
                    );

                    g.setStroke(
                            new BasicStroke(
                                    3.5f,

                                    BasicStroke.CAP_ROUND,

                                    BasicStroke.JOIN_ROUND
                            )
                    );

                    g.draw(
                            new Line2D.Double(
                                    x,
                                    y,

                                    endX,
                                    y
                            )
                    );

                    // --------------------------------------------
                    // RESIZE HANDLE
                    // --------------------------------------------

                    g.setColor(
                            Color.WHITE
                    );

                    g.fill(
                            new RoundRectangle2D.Double(
                                    endX - 4,

                                    y - 14,

                                    8,

                                    28,

                                    5,

                                    5
                            )
                    );
                }

                // ------------------------------------------------
                // SELECTION RING
                // ------------------------------------------------

                if (
                        chosen
                ) {

                    g.setColor(
                            Color.WHITE
                    );

                    g.setStroke(
                            new BasicStroke(
                                    2f
                            )
                    );

                    g.draw(
                            new Ellipse2D.Double(
                                    x - 17,

                                    y - 17,

                                    34,

                                    34
                            )
                    );
                }

                // ------------------------------------------------
                // NOTE GLOW
                // ------------------------------------------------

                g.setColor(
                        new Color(
                                color.getRed(),
                                color.getGreen(),
                                color.getBlue(),
                                65
                        )
                );

                g.fill(
                        new Ellipse2D.Double(
                                x - 13,

                                y - 13,

                                26,

                                26
                        )
                );

                // ------------------------------------------------
                // NOTE HEAD
                // ------------------------------------------------

                g.setColor(
                        color
                );

                g.fill(
                        new Ellipse2D.Double(
                                x - 7,

                                y - 7,

                                14,

                                14
                        )
                );

                g.setColor(
                        Color.WHITE
                );

                g.fill(
                        new Ellipse2D.Double(
                                x - 2,

                                y - 2,

                                4,

                                4
                        )
                );
            }
        }

        // ========================================================
        // ACTIVE HOLD PREVIEW
        // ========================================================

        private void drawHeld(
                Graphics2D g,
                long now
        ) {

            for (
                    int lane = 0;
                    lane < 3;
                    lane++
            ) {

                long begin =
                        holdStarts[lane];

                if (
                        begin < 0L
                ) {

                    continue;
                }

                int y =
                        laneY(lane);

                g.setColor(
                        LANE_COLORS[lane]
                );

                g.setStroke(
                        new BasicStroke(
                                6f,

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                g.draw(
                        new Line2D.Double(
                                xAt(
                                        begin,
                                        now
                                ),

                                y,

                                getWidth() / 2.0,

                                y
                        )
                );
            }
        }

        // ========================================================
        // PLAYHEAD
        // ========================================================

        private void drawCursor(
                Graphics2D g,
                int h,
                long now
        ) {

            int x =
                    getWidth() / 2;

            g.setStroke(
                    new BasicStroke(
                            5f
                    )
            );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,
                            30
                    )
            );

            g.drawLine(
                    x,

                    WAVE_TOP - 8,

                    x,

                    h - 60
            );

            g.setStroke(
                    new BasicStroke(
                            1.6f
                    )
            );

            g.setColor(
                    Color.WHITE
            );

            g.drawLine(
                    x,

                    WAVE_TOP - 8,

                    x,

                    h - 60
            );

            String label =
                    timeString(now);

            g.setFont(
                    new Font(
                            "Monospaced",
                            Font.BOLD,
                            12
                    )
            );

            FontMetrics fm =
                    g.getFontMetrics();

            g.setColor(
                    new Color(
                            3,
                            8,
                            20,
                            235
                    )
            );

            g.fillRoundRect(
                    x
                            - fm.stringWidth(label) / 2
                            - 12,

                    12,

                    fm.stringWidth(label)
                            + 24,

                    27,

                    10,

                    10
            );

            g.setColor(TEXT);

            g.drawString(
                    label,

                    x
                            - fm.stringWidth(label) / 2,

                    30
            );
        }
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(
            String[] args
    ) {

        SwingUtilities.invokeLater(
                ChartEditor::new
        );
    }
}