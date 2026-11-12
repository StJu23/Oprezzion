package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * OPREZZION - a fluid, high-energy three-lane rhythm game.
 * Java 21, Swing + Java2D, no external dependencies.
 * Main game window and renderer.
 */
public class Main extends JFrame {
    private static final long serialVersionUID = 1L;

    // ---------------------------- CONFIG ----------------------------

    private static final int SIMULATION_HZ = 60;

    private static final long STEP_NANOS =
            1_000_000_000L / SIMULATION_HZ;

    private static final double SONG_BPM =
            166.3975;

    private static final long SONG_OFFSET_NANOS =
            212_000_000L;

    private static final long BEAT_NANOS =
            Math.round(
                    60_000_000_000.0
                            / SONG_BPM
            );

    private static final long NOTE_TRAVEL_NANOS =
            1_800_000_000L;

    private static final long ROUND_LENGTH_NANOS =
            90_000_000_000L;

    private static final String MUSIC_RESOURCE =
            "/music/out-the-club.wav";

    private static final String CHART_RESOURCE =
            "/charts/out-the-club.chart";

    private static final int HOLD_START_GRACE_MS =
            45;

    private static final int HOLD_RELEASE_GRACE_MS =
            120;

    private static final int MAX_PARTICLES =
            620;

    private static final Color BG =
            new Color(
                    5,
                    7,
                    15
            );

    private static final Color SURFACE =
            new Color(
                    13,
                    16,
                    27
            );

    private static final Color SURFACE_2 =
            new Color(
                    28,
                    34,
                    51
            );

    private static final Color TEXT =
            new Color(
                    245,
                    245,
                    255
            );

    private static final Color MUTED =
            new Color(
                    151,
                    158,
                    183
            );

    private static final Color ACCENT =
            new Color(
                    198,
                    223,
                    255
            );

    private static final Color ACCENT_SOFT =
            new Color(
                    133,
                    165,
                    218
            );

    private static final Color SUCCESS =
            new Color(
                    172,
                    255,
                    220
            );

    private static final Color WARNING =
            new Color(
                    255,
                    221,
                    153
            );

    private static final Color MISS =
            new Color(
                    255,
                    108,
                    145
            );

    private static final Color[] LANE_COLORS = {
            new Color(
                    47,
                    218,
                    255
            ),

            new Color(
                    255,
                    210,
                    102
            ),

            new Color(
                    255,
                    91,
                    190
            )
    };

    private static final Color[] SPECTRUM = {
            new Color(
                    36,
                    205,
                    255
            ),

            new Color(
                    106,
                    105,
                    255
            ),

            new Color(
                    213,
                    78,
                    255
            ),

            new Color(
                    255,
                    84,
                    172
            ),

            new Color(
                    255,
                    167,
                    77
            ),

            new Color(
                    255,
                    238,
                    147
            ),

            new Color(
                    138,
                    255,
                    204
            )
    };

    private static final String[] LANE_NAMES = {
            "LEFT",
            "TOP",
            "RIGHT"
    };

    private static final int[] DEFAULT_KEYS = {
            KeyEvent.VK_A,
            KeyEvent.VK_W,
            KeyEvent.VK_D
    };

    private static final int[] HIT_WINDOWS_MS = {
            50,
            75,
            100,
            150,
            200
    };

    private enum GameState {
        MENU,
        PLAYING,
        PAUSED,
        RESULTS
    }

    private enum Grade {
        PERFECT,
        GREAT,
        GOOD,
        OK
    }

    // ---------------------------- GAME OBJECTS ----------------------------

    private static final class Note {
        final int lane;
        final boolean hold;
        final long duration;

        long targetTime;
        long pressOffset;

        Grade pressGrade =
                Grade.PERFECT;

        boolean holding;

        Note(
                int lane,
                boolean hold,
                long targetTime,
                long duration
        ) {
            this.lane =
                    lane;

            this.hold =
                    hold;

            this.targetTime =
                    targetTime;

            this.duration =
                    duration;
        }

        long endTime() {
            return targetTime
                    + duration;
        }
    }

    private static final class Particle {
        double x;
        double y;

        double previousX;
        double previousY;

        double vx;
        double vy;

        double rotation;
        double spin;

        final Color color;
        final double maxLife;
        final double size;
        final boolean confetti;

        double life;

        Particle(
                double x,
                double y,
                double vx,
                double vy,
                Color color,
                double life,
                double size,
                boolean confetti,
                Random random
        ) {
            this.x =
                    x;

            this.y =
                    y;

            this.previousX =
                    x;

            this.previousY =
                    y;

            this.vx =
                    vx;

            this.vy =
                    vy;

            this.color =
                    color;

            this.life =
                    life;

            this.maxLife =
                    life;

            this.size =
                    size;

            this.confetti =
                    confetti;

            this.rotation =
                    random.nextDouble()
                            * Math.PI
                            * 2.0;

            this.spin =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 12.0;
        }

        void update(
                double dt
        ) {
            previousX =
                    x;

            previousY =
                    y;

            x +=
                    vx * dt;

            y +=
                    vy * dt;

            vy +=
                    (
                            confetti
                                    ? 92.0
                                    : 17.0
                    )
                            * dt;

            vx *=
                    confetti
                            ? 0.995
                            : 0.988;

            vy *=
                    confetti
                            ? 0.998
                            : 0.988;

            rotation +=
                    spin * dt;

            spin *=
                    0.994;

            life -=
                    dt;
        }
    }

    private static final class Ring {
        final double x;
        final double y;

        final double delay;
        final double maxLife;
        final double maxRadius;

        final Color color;

        double life;

        Ring(
                double x,
                double y,
                Color color,
                double delay,
                double life,
                double radius
        ) {
            this.x =
                    x;

            this.y =
                    y;

            this.color =
                    color;

            this.delay =
                    delay;

            this.maxLife =
                    life;

            this.maxRadius =
                    radius;

            this.life =
                    life
                            + delay;
        }

        void update(
                double dt
        ) {
            life -=
                    dt;
        }

        boolean visible() {
            return life <= maxLife
                    && life > 0.0;
        }

        double progress() {
            return Math.max(
                    0.0,

                    Math.min(
                            1.0,

                            (
                                    maxLife
                                            - life
                            )
                                    / maxLife
                    )
            );
        }
    }

    private static final class RibbonBurst {
        final double x;
        final double y;

        final double angle;
        final double maxLife;

        final Color color;

        final boolean victory;
        final boolean grandPrix;

        final int combo;

        double life;

        RibbonBurst(
                double x,
                double y,
                Color color,
                int combo,
                boolean victory,
                boolean grandPrix,
                double angle
        ) {
            this.x =
                    x;

            this.y =
                    y;

            this.color =
                    color;

            this.combo =
                    combo;

            this.victory =
                    victory;

            this.grandPrix =
                    grandPrix;

            this.angle =
                    angle;

            this.maxLife =
                    grandPrix
                            ? 3.8
                            : victory
                            ? 2.6
                            : 1.0;

            this.life =
                    maxLife;
        }

        void update(
                double dt
        ) {
            life -=
                    dt;
        }

        double progress() {
            return Math.max(
                    0.0,

                    Math.min(
                            1.0,

                            1.0
                                    - life
                                    / maxLife
                    )
            );
        }
    }

    private static final class FloatingText {
        final String text;
        final Color color;

        final double x;

        double y;

        double life =
                0.9;

        final double maxLife =
                0.9;

        FloatingText(
                String text,
                Color color,
                double x,
                double y
        ) {
            this.text =
                    text;

            this.color =
                    color;

            this.x =
                    x;

            this.y =
                    y;
        }

        void update(
                double dt
        ) {
            y -=
                    32.0
                            * dt;

            life -=
                    dt;
        }
    }

    private static final class AmbientMote {
        double x;
        double y;

        final double radius;
        final double speed;
        final double phase;

        final Color color;

        AmbientMote(
                Random random,
                int width,
                int height
        ) {
            x =
                    random.nextDouble()
                            * width;

            y =
                    random.nextDouble()
                            * height;

            radius =
                    0.7
                            + random.nextDouble()
                            * 2.0;

            speed =
                    5.0
                            + random.nextDouble()
                            * 16.0;

            phase =
                    random.nextDouble()
                            * Math.PI
                            * 2.0;

            color =
                    SPECTRUM[
                            random.nextInt(
                                    SPECTRUM.length
                            )
                            ];
        }

        void update(
                double dt,
                int w,
                int h
        ) {
            y -=
                    speed
                            * dt;

            x +=
                    Math.sin(
                            phase
                                    + y
                                    * 0.012
                    )
                            * 7.0
                            * dt;

            if (y < -8) {
                y =
                        h + 8;

                x =
                        (
                                x
                                        + w
                                        * 0.381966
                        )
                                % Math.max(
                                1,
                                w
                        );
            }

            if (x < -8) {
                x =
                        w + 8;
            }

            if (x > w + 8) {
                x =
                        -8;
            }
        }
    }

    private static final class FluidBlob {
        final double seedX;
        final double seedY;

        final double radius;
        final double phase;

        final double driftX;
        final double driftY;

        final Color color;

        FluidBlob(
                double x,
                double y,
                double radius,
                double phase,
                double driftX,
                double driftY,
                Color color
        ) {
            this.seedX =
                    x;

            this.seedY =
                    y;

            this.radius =
                    radius;

            this.phase =
                    phase;

            this.driftX =
                    driftX;

            this.driftY =
                    driftY;

            this.color =
                    color;
        }
    }

    // ---------------------------- STATE ----------------------------

    private final transient Object gameLock =
            new Object();

    private final AudioEngine audioEngine =
            new AudioEngine();

    // Accessibility settings are loaded before the first frame is rendered.
    private final VisualSettings visualSettings = new VisualSettings();

    private final Random random =
            new Random();

    private final ArrayList<Note> notes =
            new ArrayList<>();

    private final ArrayList<Particle> particles =
            new ArrayList<>();

    private final ArrayList<Ring> rings =
            new ArrayList<>();

    private final ArrayList<RibbonBurst> bursts =
            new ArrayList<>();

    private final ArrayList<FloatingText> floatingTexts =
            new ArrayList<>();

    private final ArrayList<AmbientMote> motes =
            new ArrayList<>();

    private final ArrayList<FluidBlob> blobs =
            new ArrayList<>();

    private final int[] keys =
            DEFAULT_KEYS.clone();

    private final boolean[] keyDown =
            new boolean[3];

    private final long[] laneFlashUntil =
            new long[3];

    private volatile boolean running =
            true;

    private volatile int hitWindowMs =
            100;

    private volatile int captureLane =
            -1;

    private boolean escapeDown;
    private boolean enterDown;
    private boolean f3Down;
    private boolean visualsDialogOpen;

    private GameState state =
            GameState.MENU;

    private int score;
    private int combo;
    private int maxCombo;

    private int judged;
    private int successful;
    private int misses;

    private int perfects;
    private int greats;
    private int goods;
    private int oks;

    private long roundStart;
    private long roundEnd;

    private long songDurationNanos =
            ROUND_LENGTH_NANOS;

    private boolean audioReady;

    private Chart chart;
    private boolean chartReady;
    private int nextChartNoteIndex;

    private long lastBeatIndex =
            -1;

    private double beatPulse;

    private double shakeMagnitude;
    private double shakeX;
    private double shakeY;

    private double screenBloomFlash;

    private Color screenFlashColor =
            Color.WHITE;

    // Limits even opt-in full-screen flashes to a restrained cadence.
    private long lastScreenFlashNanos = Long.MIN_VALUE / 2L;

    private long animationClock;

    private transient KeyEventDispatcher keyboardDispatcher;

    // ---------------------------- SWING UI ----------------------------

    private final GamePanel gamePanel =
            new GamePanel();

    private final JLabel scoreValue =
            metricValue(
                    "000000"
            );

    private final JLabel comboValue =
            metricValue(
                    "0"
            );

    private final JLabel accuracyValue =
            metricValue(
                    "100.0%"
            );

    private final JLabel timeValue =
            metricValue(
                    "01:30"
            );

    private final JLabel statusLabel =
            new JLabel(
                    "READY"
            );

    private final JButton startButton =
            new JButton(
                    "PLAY"
            );

    private final JButton pauseButton =
            new JButton(
                    "PAUSE"
            );

    private final JButton restartButton =
            new JButton(
                    "RESTART"
            );

    private final JButton visualsButton =
            new JButton("VISUALS");

    private final JButton[] keyButtons =
            new JButton[3];

    private final JComboBox<String> timingSelector =
            new JComboBox<>(
                    new String[]{
                            "50 ms",
                            "75 ms",
                            "100 ms",
                            "150 ms",
                            "200 ms"
                    }
            );

    private Timer hudTimer;

    // ---------------------------- CONSTRUCTOR ----------------------------

    @SuppressWarnings("this-escape")
    public Main() {
        super(
                "OPREZZION"
        );

        setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        setMinimumSize(
                new Dimension(
                        900,
                        680
                )
        );

        setSize(
                1180,
                840
        );

        setLocationRelativeTo(
                null
        );

        setLayout(
                new BorderLayout()
        );

        getContentPane()
                .setBackground(
                        BG
                );

        add(
                buildHeader(),
                BorderLayout.NORTH
        );

        add(
                gamePanel,
                BorderLayout.CENTER
        );

        add(
                buildFooter(),
                BorderLayout.SOUTH
        );

        timingSelector.setSelectedIndex(
                2
        );

        timingSelector.addActionListener(
                e -> {
                    hitWindowMs =
                            HIT_WINDOWS_MS[
                                    timingSelector.getSelectedIndex()
                                    ];

                    gamePanel.repaint();
                }
        );

        startButton.addActionListener(
                e -> startNewRun()
        );

        pauseButton.addActionListener(
                e -> togglePause()
        );

        restartButton.addActionListener(
                e -> startNewRun()
        );

        visualsButton.addActionListener(e -> openVisualSettings());

        initializeAtmosphere();

        installKeyboardDispatcher();

        loadMusicResource();

        loadChartResource();

        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(
                            WindowEvent e
                    ) {
                        running =
                                false;

                        if (hudTimer != null) {
                            hudTimer.stop();
                        }

                        if (keyboardDispatcher != null) {
                            KeyboardFocusManager
                                    .getCurrentKeyboardFocusManager()
                                    .removeKeyEventDispatcher(
                                            keyboardDispatcher
                                    );
                        }

                        audioEngine.close();
                    }
                }
        );

        addWindowFocusListener(
                new WindowAdapter() {
                    @Override
                    public void windowLostFocus(
                            WindowEvent e
                    ) {
                        cancelHeldKeysOnFocusLoss();
                    }
                }
        );

        hudTimer =
                new Timer(
                        66,
                        e -> refreshHud()
                );

        hudTimer.start();

        setVisible(
                true
        );

        startSimulationThread();

        SwingUtilities.invokeLater(
                gamePanel::requestFocusInWindow
        );
    }

    private void initializeAtmosphere() {
        for (
                int i = 0;
                i < 72;
                i++
        ) {
            motes.add(
                    new AmbientMote(
                            random,
                            1180,
                            840
                    )
            );
        }

        blobs.add(
                new FluidBlob(
                        0.08,
                        0.20,
                        0.48,
                        0.2,
                        0.020,
                        0.011,
                        new Color(
                                45,
                                92,
                                255
                        )
                )
        );

        blobs.add(
                new FluidBlob(
                        0.90,
                        0.22,
                        0.46,
                        2.1,
                        -0.017,
                        0.009,
                        new Color(
                                227,
                                51,
                                178
                        )
                )
        );

        blobs.add(
                new FluidBlob(
                        0.72,
                        0.91,
                        0.49,
                        4.3,
                        -0.012,
                        -0.014,
                        new Color(
                                31,
                                185,
                                218
                        )
                )
        );

        blobs.add(
                new FluidBlob(
                        0.16,
                        0.87,
                        0.38,
                        5.7,
                        0.016,
                        -0.012,
                        new Color(
                                122,
                                68,
                                238
                        )
                )
        );

        blobs.add(
                new FluidBlob(
                        0.52,
                        0.08,
                        0.34,
                        3.2,
                        0.006,
                        0.017,
                        new Color(
                                67,
                                129,
                                255
                        )
                )
        );
    }

    // ---------------------------- UI LAYOUT ----------------------------

    private JPanel buildHeader() {
        JPanel header =
                new JPanel(
                        new BorderLayout(
                                24,
                                0
                        )
                );

        header.setBackground(
                SURFACE
        );

        header.setBorder(
                new EmptyBorder(
                        13,
                        23,
                        13,
                        23
                )
        );

        JPanel brand =
                new JPanel();

        brand.setOpaque(
                false
        );

        brand.setLayout(
                new BoxLayout(
                        brand,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel title =
                new JLabel(
                        "oprezzion"
                );

        title.setForeground(
                TEXT
        );

        title.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        22
                )
        );

        JLabel subtitle =
                new JLabel(
                        "THREE-LANE RHYTHM GAME"
                );

        subtitle.setForeground(
                MUTED
        );

        subtitle.setFont(
                new Font(
                        "SansSerif",
                        Font.PLAIN,
                        9
                )
        );

        brand.add(
                title
        );

        brand.add(
                Box.createVerticalStrut(
                        3
                )
        );

        brand.add(
                subtitle
        );

        JPanel stats =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                26,
                                0
                        )
                );

        stats.setOpaque(
                false
        );

        stats.add(
                statBlock(
                        "SCORE",
                        scoreValue
                )
        );

        stats.add(
                statBlock(
                        "COMBO",
                        comboValue
                )
        );

        stats.add(
                statBlock(
                        "ACCURACY",
                        accuracyValue
                )
        );

        stats.add(
                statBlock(
                        "TIME",
                        timeValue
                )
        );

        JPanel actions =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                6,
                                2
                        )
                );

        actions.setOpaque(
                false
        );

        styleButton(
                startButton,
                true
        );

        styleButton(
                pauseButton,
                false
        );

        styleButton(
                restartButton,
                false
        );

        styleButton(visualsButton, false);

        actions.add(
                startButton
        );

        actions.add(
                pauseButton
        );

        actions.add(
                restartButton
        );

        actions.add(visualsButton);

        header.add(
                brand,
                BorderLayout.WEST
        );

        header.add(
                stats,
                BorderLayout.CENTER
        );

        header.add(
                actions,
                BorderLayout.EAST
        );

        return header;
    }

    private JPanel buildFooter() {
        JPanel footer =
                new JPanel(
                        new BorderLayout(
                                15,
                                0
                        )
                );

        footer.setBackground(
                SURFACE
        );

        footer.setBorder(
                new EmptyBorder(
                        10,
                        21,
                        10,
                        21
                )
        );

        JPanel controls =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                6,
                                0
                        )
                );

        controls.setOpaque(
                false
        );

        for (
                int i = 0;
                i < 3;
                i++
        ) {
            final int lane =
                    i;

            keyButtons[i] =
                    new JButton(
                            LANE_NAMES[i]
                                    + "  ["
                                    + KeyEvent.getKeyText(
                                    keys[i]
                            )
                                    + "]"
                    );

            styleButton(
                    keyButtons[i],
                    false
            );

            keyButtons[i].setToolTipText(
                    "Click to change the "
                            + LANE_NAMES[i]
                            + " key"
            );

            keyButtons[i].addActionListener(
                    e -> beginKeyCapture(
                            lane
                    )
            );

            controls.add(
                    keyButtons[i]
            );
        }

        JPanel settings =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                8,
                                0
                        )
                );

        settings.setOpaque(
                false
        );

        JLabel timingLabel =
                new JLabel(
                        "TIMING WINDOW"
                );

        timingLabel.setForeground(
                MUTED
        );

        timingLabel.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        9
                )
        );

        timingSelector.setFocusable(
                false
        );

        timingSelector.setBackground(
                SURFACE_2
        );

        timingSelector.setForeground(
                TEXT
        );

        timingSelector.setBorder(
                BorderFactory.createEmptyBorder(
                        3,
                        5,
                        3,
                        5
                )
        );

        settings.add(
                timingLabel
        );

        settings.add(
                timingSelector
        );

        statusLabel.setForeground(
                MUTED
        );

        statusLabel.setFont(
                new Font(
                        "SansSerif",
                        Font.PLAIN,
                        10
                )
        );

        footer.add(
                controls,
                BorderLayout.WEST
        );

        footer.add(
                statusLabel,
                BorderLayout.CENTER
        );

        footer.add(
                settings,
                BorderLayout.EAST
        );

        return footer;
    }

    private JPanel statBlock(
            String name,
            JLabel value
    ) {
        JPanel block =
                new JPanel();

        block.setOpaque(
                false
        );

        block.setLayout(
                new BoxLayout(
                        block,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel label =
                new JLabel(
                        name
                );

        label.setForeground(
                MUTED
        );

        label.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        9
                )
        );

        label.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        value.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        block.add(
                label
        );

        block.add(
                Box.createVerticalStrut(
                        4
                )
        );

        block.add(
                value
        );

        return block;
    }

    private JLabel metricValue(
            String value
    ) {
        JLabel label =
                new JLabel(
                        value
                );

        label.setForeground(
                TEXT
        );

        label.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        17
                )
        );

        return label;
    }

    private void styleButton(
            JButton button,
            boolean primary
    ) {
        button.setFocusPainted(
                false
        );

        button.setBorder(
                new EmptyBorder(
                        8,
                        14,
                        8,
                        14
                )
        );

        button.setFont(
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        10
                )
        );

        button.setForeground(
                primary
                        ? BG
                        : TEXT
        );

        button.setBackground(
                primary
                        ? ACCENT
                        : SURFACE_2
        );

        button.setOpaque(
                true
        );

        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );
    }

    private void setStatus(
            String value
    ) {
        SwingUtilities.invokeLater(
                () -> statusLabel.setText(
                        value
                )
        );
    }

    // ---------------------------- AUDIO + CHART ----------------------------

    private void loadMusicResource() {
        try {
            URL resource =
                    Main.class.getResource(
                            MUSIC_RESOURCE
                    );

            if (resource == null) {
                audioReady =
                        false;

                setStatus(
                        "Missing music file: "
                                + MUSIC_RESOURCE
                );

                return;
            }

            Path audioPath;

            if (
                    "file".equalsIgnoreCase(
                            resource.getProtocol()
                    )
            ) {
                audioPath =
                        Path.of(
                                resource.toURI()
                        );
            } else {
                Path tempFile =
                        Files.createTempFile(
                                "oprezzion-track-",
                                ".wav"
                        );

                tempFile
                        .toFile()
                        .deleteOnExit();

                try (
                        InputStream input =
                                Main.class.getResourceAsStream(
                                        MUSIC_RESOURCE
                                )
                ) {
                    if (input == null) {
                        throw new IllegalStateException(
                                "Could not open "
                                        + MUSIC_RESOURCE
                        );
                    }

                    Files.copy(
                            input,
                            tempFile,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                }

                audioPath =
                        tempFile;
            }

            audioEngine.load(
                    audioPath
            );

            songDurationNanos =
                    Math.max(
                            1L,
                            audioEngine.getDurationMillis()
                    )
                            * 1_000_000L;

            audioReady =
                    true;

            setStatus(
                    "Music ready  ·  "
                            + formatTime(
                            songDurationNanos
                    )
            );
        } catch (
                Exception exception
        ) {
            audioReady =
                    false;

            setStatus(
                    "Audio load failed  ·  "
                            + exception.getMessage()
            );

            exception.printStackTrace();
        }
    }

    private void loadChartResource() {
        try {
            chart =
                    Chart.load(
                            CHART_RESOURCE
                    );

            chartReady =
                    true;

            setStatus(
                    "Ready  ·  "
                            + chart.noteCount()
                            + " chart notes loaded · F3 / VISUALS"
            );

            System.out.println(
                    "OPREZZION: Loaded chart from "
                            + chart.sourceDescription()
                            + " with "
                            + chart.noteCount()
                            + " notes."
            );
        } catch (
                Exception exception
        ) {
            chart =
                    null;

            chartReady =
                    false;

            setStatus(
                    "Chart load failed  ·  "
                            + exception.getMessage()
            );

            exception.printStackTrace();
        }
    }

    // ---------------------------- ACCESSIBILITY ----------------------------

    /** Opens the visual settings without changing the song or chart. */
    private void openVisualSettings() {
        if (visualsDialogOpen) return;
        visualsDialogOpen = true;
        try {
            synchronized (gameLock) {
                if (state == GameState.PLAYING) {
                    audioEngine.pause();
                    state = GameState.PAUSED;
                    for (int lane = 0; lane < 3; lane++) keyDown[lane] = false;
                    // Keep paused runs deterministic: active holds are abandoned
                    // exactly as in the existing pause function.
                    notes.removeIf(note -> note.holding);
                }
            }
            visualSettings.showDialog(this, () -> {
                synchronized (gameLock) {
                    // Purge old bright effects immediately after a preset change.
                    particles.clear();
                    rings.clear();
                    bursts.clear();
                    shakeMagnitude = 0.0;
                    shakeX = shakeY = 0.0;
                    beatPulse = 0.0;
                    screenBloomFlash = 0.0;
                }
                setStatus("Visuals: " + visualSettings.get().preset() + " · saved");
                gamePanel.repaint();
            });
        } finally {
            visualsDialogOpen = false;
            gamePanel.requestFocusInWindow();
        }
    }

    private int scaledParticles(int baseCount) {
        return Math.max(0, baseCount * visualSettings.get().effectiveParticles() / 100);
    }

    private void requestScreenFlash(Color color, double magnitude) {
        if (!visualSettings.get().allowFlashes()) return;
        long now = gameplayTimeNanos();
        // No rapid-fire whole-screen flashes, even in Full mode.
        if (now - lastScreenFlashNanos < 650_000_000L) return;
        lastScreenFlashNanos = now;
        screenFlashColor = color;
        screenBloomFlash = Math.max(screenBloomFlash,
                Math.min(0.55, magnitude * visualSettings.get().effectiveIntensity() / 100.0));
    }

    private long gameplayTimeNanos() {
        if (
                !audioReady
                        || !audioEngine.isLoaded()
        ) {
            return 0L;
        }

        return Math.max(
                0L,
                audioEngine.getPositionMillis()
        )
                * 1_000_000L;
    }

    private static String formatTime(
            long nanos
    ) {
        long totalSeconds =
                Math.max(
                        0L,
                        nanos
                )
                        / 1_000_000_000L;

        return String.format(
                "%02d:%02d",
                totalSeconds / 60L,
                totalSeconds % 60L
        );
    }

    // ---------------------------- INPUT ----------------------------

    private void installKeyboardDispatcher() {
        keyboardDispatcher =
                this::dispatchKeyEvent;

        KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(
                        keyboardDispatcher
                );
    }

    private boolean dispatchKeyEvent(
            KeyEvent event
    ) {
        if (!isActive()) {
            return false;
        }

        int id =
                event.getID();

        int code =
                event.getKeyCode();

        if (
                id == KeyEvent.KEY_PRESSED
        ) {
            if (code == KeyEvent.VK_F3) {
                if (!f3Down) {
                    f3Down = true;
                    openVisualSettings();
                }
                event.consume();
                return true;
            }
            if (captureLane >= 0) {
                captureKey(
                        code
                );

                event.consume();

                return true;
            }

            synchronized (gameLock) {
                if (
                        code == KeyEvent.VK_ESCAPE
                ) {
                    if (!escapeDown) {
                        escapeDown =
                                true;

                        togglePause();
                    }

                    event.consume();

                    return true;
                }

                if (
                        code == KeyEvent.VK_ENTER
                ) {
                    if (!enterDown) {
                        enterDown =
                                true;

                        if (
                                state == GameState.MENU
                                        || state == GameState.RESULTS
                        ) {
                            startNewRun();
                        } else if (
                                state == GameState.PAUSED
                        ) {
                            togglePause();
                        }
                    }

                    event.consume();

                    return true;
                }

                for (
                        int lane = 0;
                        lane < 3;
                        lane++
                ) {
                    if (
                            keys[lane] == code
                    ) {
                        pressLane(
                                lane
                        );

                        event.consume();

                        return true;
                    }
                }
            }
        } else if (
                id == KeyEvent.KEY_RELEASED
        ) {
            if (code == KeyEvent.VK_F3) {
                f3Down = false;
                event.consume();
                return true;
            }
            synchronized (gameLock) {
                if (
                        code == KeyEvent.VK_ESCAPE
                ) {
                    escapeDown =
                            false;
                }

                if (
                        code == KeyEvent.VK_ENTER
                ) {
                    enterDown =
                            false;
                }

                for (
                        int lane = 0;
                        lane < 3;
                        lane++
                ) {
                    if (
                            keys[lane] == code
                    ) {
                        releaseLane(
                                lane
                        );

                        event.consume();

                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void captureKey(
            int code
    ) {
        if (
                code == KeyEvent.VK_ESCAPE
        ) {
            cancelKeyCapture();

            return;
        }

        if (
                code == KeyEvent.VK_ENTER
                        || code == KeyEvent.VK_SHIFT
                        || code == KeyEvent.VK_CONTROL
                        || code == KeyEvent.VK_ALT
                        || code == KeyEvent.VK_META
        ) {
            setStatus(
                    "Choose a letter or arrow key"
            );

            return;
        }

        int lane =
                captureLane;

        if (lane < 0) {
            return;
        }

        synchronized (gameLock) {
            for (
                    int i = 0;
                    i < keys.length;
                    i++
            ) {
                if (
                        i != lane
                                && keys[i] == code
                ) {
                    setStatus(
                            "That key is already assigned"
                    );

                    return;
                }
            }

            keys[lane] =
                    code;

            keyDown[lane] =
                    false;

            captureLane =
                    -1;
        }

        SwingUtilities.invokeLater(
                () -> {
                    keyButtons[lane].setText(
                            LANE_NAMES[lane]
                                    + "  ["
                                    + KeyEvent.getKeyText(
                                    code
                            )
                                    + "]"
                    );

                    statusLabel.setText(
                            "Control updated"
                    );

                    gamePanel.requestFocusInWindow();
                }
        );
    }

    private void beginKeyCapture(
            int lane
    ) {
        captureLane =
                lane;

        setStatus(
                "Press a new key for "
                        + LANE_NAMES[lane]
                        + "  ·  ESC to cancel"
        );

        gamePanel.requestFocusInWindow();
    }

    private void cancelKeyCapture() {
        captureLane =
                -1;

        setStatus(
                "Key binding cancelled"
        );

        gamePanel.requestFocusInWindow();
    }

    private void pressLane(
            int lane
    ) {
        synchronized (gameLock) {
            if (
                    captureLane >= 0
                            || state != GameState.PLAYING
                            || keyDown[lane]
            ) {
                return;
            }

            keyDown[lane] =
                    true;

            long now =
                    gameplayTimeNanos();

            laneFlashUntil[lane] =
                    now
                            + 180_000_000L;

            hitNote(
                    lane,
                    now
            );
        }
    }

    private void releaseLane(
            int lane
    ) {
        synchronized (gameLock) {
            if (
                    !keyDown[lane]
            ) {
                return;
            }

            keyDown[lane] =
                    false;

            if (
                    state == GameState.PLAYING
            ) {
                releaseHold(
                        lane,
                        gameplayTimeNanos()
                );
            }
        }
    }

    private void cancelHeldKeysOnFocusLoss() {
        synchronized (gameLock) {
            for (
                    int lane = 0;
                    lane < 3;
                    lane++
            ) {
                keyDown[lane] =
                        false;
            }

            notes.removeIf(
                    note -> note.holding
            );

            escapeDown =
                    false;

            enterDown =
                    false;
            f3Down = false;
        }
    }

    // ---------------------------- ROUND CONTROL ----------------------------

    private void startNewRun() {
        /*
         * Reload every time so editing the chart and pressing RESTART
         * immediately picks up the new chart.
         */
        loadChartResource();

        if (
                !audioReady
                        || !audioEngine.isLoaded()
        ) {
            setStatus(
                    "Cannot start  ·  missing out-the-club.wav"
            );

            return;
        }

        if (
                !chartReady
                        || chart == null
        ) {
            setStatus(
                    "Cannot start  ·  chart failed to load"
            );

            return;
        }

        synchronized (gameLock) {
            state =
                    GameState.MENU;

            notes.clear();
            particles.clear();
            rings.clear();
            bursts.clear();
            floatingTexts.clear();

            shakeMagnitude =
                    0.0;

            shakeX =
                    0.0;

            shakeY =
                    0.0;

            screenBloomFlash =
                    0.0;

            for (
                    int i = 0;
                    i < 3;
                    i++
            ) {
                keyDown[i] =
                        false;

                laneFlashUntil[i] =
                        0L;
            }

            score =
                    0;

            combo =
                    0;

            maxCombo =
                    0;

            judged =
                    0;

            successful =
                    0;

            misses =
                    0;

            perfects =
                    0;

            greats =
                    0;

            goods =
                    0;

            oks =
                    0;

            roundStart =
                    0L;

            roundEnd =
                    songDurationNanos;

            nextChartNoteIndex =
                    0;

            lastBeatIndex =
                    -1;

            lastScreenFlashNanos = Long.MIN_VALUE / 2L;

            beatPulse =
                    0.0;
        }

        try {
            audioEngine.stop();

            audioEngine.seekMillis(
                    0L
            );

            audioEngine.play();
        } catch (
                RuntimeException exception
        ) {
            setStatus(
                    "Audio start failed  ·  "
                            + exception.getMessage()
            );

            exception.printStackTrace();

            return;
        }

        synchronized (gameLock) {
            state =
                    GameState.PLAYING;
        }

        setStatus(
                "Playing OUT THE CLUB  ·  ESC to pause"
        );

        SwingUtilities.invokeLater(
                () -> pauseButton.setText(
                        "PAUSE"
                )
        );

        gamePanel.requestFocusInWindow();

        gamePanel.repaint();
    }

    private void togglePause() {
        synchronized (gameLock) {
            if (
                    state == GameState.PLAYING
            ) {
                audioEngine.pause();

                state =
                        GameState.PAUSED;

                for (
                        int i = 0;
                        i < 3;
                        i++
                ) {
                    keyDown[i] =
                            false;
                }

                notes.removeIf(
                        note -> note.holding
                );

                setStatus(
                        "Paused  ·  ENTER or ESC to resume"
                );

                SwingUtilities.invokeLater(
                        () -> pauseButton.setText(
                                "RESUME"
                        )
                );

            } else if (
                    state == GameState.PAUSED
            ) {
                audioEngine.resume();

                state =
                        GameState.PLAYING;

                setStatus(
                        "Playing OUT THE CLUB  ·  ESC to pause"
                );

                SwingUtilities.invokeLater(
                        () -> pauseButton.setText(
                                "PAUSE"
                        )
                );
            }
        }

        gamePanel.requestFocusInWindow();

        gamePanel.repaint();
    }

    private void finishRun() {
        audioEngine.stop();

        state =
                GameState.RESULTS;

        for (
                int lane = 0;
                lane < 3;
                lane++
        ) {
            keyDown[lane] =
                    false;
        }

        notes.clear();

        spawnVictoryLap(
                gamePanel.getWidth()
                        / 2.0,

                gamePanel.getHeight()
                        / 2.0,

                true
        );

        SwingUtilities.invokeLater(
                () -> {
                    statusLabel.setText(
                            "Run complete  ·  ENTER to play again"
                    );

                    gamePanel.repaint();
                }
        );
    }

    // ---------------------------- SIMULATION ----------------------------

    private void startSimulationThread() {
        Thread thread =
                new Thread(
                        () -> {
                            long previous =
                                    System.nanoTime();

                            long accumulator =
                                    0L;

                            long lastPaint =
                                    0L;

                            while (running) {
                                long now =
                                        System.nanoTime();

                                long elapsed =
                                        now
                                                - previous;

                                previous =
                                        now;

                                accumulator +=
                                        Math.min(
                                                elapsed,
                                                STEP_NANOS
                                                        * 30L
                                        );

                                while (
                                        accumulator
                                                >= STEP_NANOS
                                ) {
                                    synchronized (gameLock) {
                                        updateGame(
                                                gameplayTimeNanos()
                                        );
                                    }

                                    accumulator -=
                                            STEP_NANOS;
                                }

                                if (
                                        now
                                                - lastPaint
                                                >= 16_666_667L
                                ) {
                                    gamePanel.repaint();

                                    lastPaint =
                                            now;
                                }

                                long sleep =
                                        STEP_NANOS
                                                - accumulator;

                                if (
                                        sleep > 0L
                                ) {
                                    java.util.concurrent.locks.LockSupport
                                            .parkNanos(
                                                    sleep
                                            );
                                }
                            }
                        },

                        "Oprezzion-Simulation"
                );

        thread.setDaemon(
                true
        );

        thread.start();
    }

    private void updateGame(
            long now
    ) {
        double dt =
                STEP_NANOS
                        / 1_000_000_000.0;

        animationClock +=
                STEP_NANOS;

        updateAmbient(
                dt
        );

        updateEffects(
                dt
        );

        if (
                state != GameState.PLAYING
        ) {
            return;
        }

        if (
                audioEngine.isFinished()
                        || now >= roundEnd
        ) {
            finishRun();

            return;
        }

        /*
         * Visual beat pulse still uses the measured BPM/offset.
         * Gameplay notes themselves come directly from the chart.
         */
        long beatElapsed =
                now
                        - SONG_OFFSET_NANOS;

        if (
                beatElapsed >= 0L
        ) {
            long beat =
                    beatElapsed
                            / BEAT_NANOS;

            if (
                    beat != lastBeatIndex
            ) {
                lastBeatIndex =
                        beat;

                beatPulse =
                        visualSettings.get().allowBeatPulses() ? 1.0 : 0.0;

                spawnBeatAccents(
                        (int) beat
                );
            }
        }

        /*
         * THIS replaces the old procedural generator.
         */
        spawnDueChartNotes(
                now
        );

        long normalWindow =
                normalWindowNanos();

        long holdStartWindow =
                holdStartWindowNanos();

        long holdReleaseWindow =
                holdReleaseWindowNanos();

        Iterator<Note> iterator =
                notes.iterator();

        while (
                iterator.hasNext()
        ) {
            Note note =
                    iterator.next();

            if (
                    note.holding
            ) {
                if (
                        now
                                > note.endTime()
                                + holdReleaseWindow
                ) {
                    registerMiss(
                            note,
                            "RELEASE MISSED"
                    );

                    iterator.remove();
                }
            } else {
                long allowedWindow =
                        note.hold
                                ? holdStartWindow
                                : normalWindow;

                if (
                        now
                                > note.targetTime
                                + allowedWindow
                ) {
                    registerMiss(
                            note,

                            note.hold
                                    ? "HOLD MISSED"
                                    : "MISS"
                    );

                    iterator.remove();
                }
            }
        }
    }

    private void spawnDueChartNotes(
            long now
    ) {
        if (
                !chartReady
                        || chart == null
        ) {
            return;
        }

        var chartNotes =
                chart.notes();

        /*
         * Notes enter the playfield NOTE_TRAVEL_NANOS before their
         * target timestamp.
         *
         * The actual target timestamp comes DIRECTLY from the chart.
         */
        while (
                nextChartNoteIndex
                        < chartNotes.size()
        ) {
            ChartNote chartNote =
                    chartNotes.get(
                            nextChartNoteIndex
                    );

            if (
                    chartNote.targetTimeNanos()
                            > now
                            + NOTE_TRAVEL_NANOS
            ) {
                break;
            }

            notes.add(
                    new Note(
                            chartNote.lane(),

                            chartNote.isHold(),

                            chartNote.targetTimeNanos(),

                            chartNote.durationNanos()
                    )
            );

            nextChartNoteIndex++;
        }
    }

    private void updateAmbient(
            double dt
    ) {
        if (!visualSettings.get().allowMotion()) return;
        int w =
                Math.max(
                        1,
                        gamePanel.getWidth()
                );

        int h =
                Math.max(
                        1,
                        gamePanel.getHeight()
                );

        for (
                AmbientMote mote
                : motes
        ) {
            mote.update(
                    dt,
                    w,
                    h
            );
        }
    }

    private void updateEffects(
            double dt
    ) {
        Iterator<Particle> particleIterator =
                particles.iterator();

        while (
                particleIterator.hasNext()
        ) {
            Particle p =
                    particleIterator.next();

            p.update(
                    dt
            );

            if (
                    p.life <= 0.0
            ) {
                particleIterator.remove();
            }
        }

        Iterator<Ring> ringIterator =
                rings.iterator();

        while (
                ringIterator.hasNext()
        ) {
            Ring ring =
                    ringIterator.next();

            ring.update(
                    dt
            );

            if (
                    ring.life <= 0.0
            ) {
                ringIterator.remove();
            }
        }

        Iterator<RibbonBurst> burstIterator =
                bursts.iterator();

        while (
                burstIterator.hasNext()
        ) {
            RibbonBurst burst =
                    burstIterator.next();

            burst.update(
                    dt
            );

            if (
                    burst.life <= 0.0
            ) {
                burstIterator.remove();
            }
        }

        Iterator<FloatingText> textIterator =
                floatingTexts.iterator();

        while (
                textIterator.hasNext()
        ) {
            FloatingText text =
                    textIterator.next();

            if (visualSettings.get().restrained()) {
                text.life -= dt; // No decorative movement in precautions mode.
            } else {
                text.update(dt);
            }

            if (
                    text.life <= 0.0
            ) {
                textIterator.remove();
            }
        }

        shakeMagnitude =
                Math.max(
                        0.0,

                        shakeMagnitude
                                - 19.0
                                * dt
                );

        screenBloomFlash =
                Math.max(
                        0.0,

                        screenBloomFlash
                                - 2.4
                                * dt
                );

        beatPulse =
                Math.max(
                        0.0,

                        beatPulse
                                - 3.5
                                * dt
                );

        if (
                visualSettings.get().allowShake() && shakeMagnitude > 0.02
        ) {
            double t =
                    animationClock
                            / 1_000_000_000.0;

            shakeX =
                    Math.sin(
                            t * 48.0
                    )
                            * shakeMagnitude;

            shakeY =
                    Math.cos(
                            t * 39.0
                    )
                            * shakeMagnitude
                            * 0.72;
        } else {
            shakeX =
                    0.0;

            shakeY =
                    0.0;
        }
    }

    private void spawnBeatAccents(
            int beat
    ) {
        if (!visualSettings.get().allowMotion()) return;
        if (
                beat % 2 == 0
                        && motes.size()
                        < 110
        ) {
            int w =
                    Math.max(
                            1,
                            gamePanel.getWidth()
                    );

            int h =
                    Math.max(
                            1,
                            gamePanel.getHeight()
                    );

            for (
                    int i = 0;
                    i < 3;
                    i++
            ) {
                motes.add(
                        new AmbientMote(
                                random,
                                w,
                                h
                        )
                );
            }
        }
    }

    // ---------------------------- TIMING / JUDGEMENT ----------------------------

    private long normalWindowNanos() {
        return hitWindowMs
                * 1_000_000L;
    }

    private long holdStartWindowNanos() {
        return (
                hitWindowMs
                        + HOLD_START_GRACE_MS
        )
                * 1_000_000L;
    }

    private long holdReleaseWindowNanos() {
        return (
                hitWindowMs
                        + HOLD_RELEASE_GRACE_MS
        )
                * 1_000_000L;
    }

    private void hitNote(
            int lane,
            long now
    ) {
        Note nearest =
                null;

        long nearestDifference =
                Long.MAX_VALUE;

        for (
                Note note
                : notes
        ) {
            if (
                    note.lane != lane
                            || note.holding
            ) {
                continue;
            }

            long difference =
                    Math.abs(
                            now
                                    - note.targetTime
                    );

            if (
                    difference
                            < nearestDifference
            ) {
                nearestDifference =
                        difference;

                nearest =
                        note;
            }
        }

        if (
                nearest == null
        ) {
            return;
        }

        long allowed =
                nearest.hold
                        ? holdStartWindowNanos()
                        : normalWindowNanos();

        if (
                nearestDifference
                        > allowed
        ) {
            return;
        }

        long offset =
                now
                        - nearest.targetTime;

        if (
                nearest.hold
        ) {
            nearest.holding =
                    true;

            nearest.pressOffset =
                    offset;

            nearest.pressGrade =
                    Math.abs(
                            offset
                    )
                            <= normalWindowNanos()

                            ? gradeForOffset(
                            offset
                    )

                            : Grade.OK;

            addFloatingText(
                    "HOLD",
                    LANE_COLORS[lane],
                    lane
            );

            spawnCatchEffect(
                    lane,
                    true,
                    nearest.pressGrade
            );
        } else {
            Grade grade =
                    gradeForOffset(
                            offset
                    );

            registerHit(
                    grade,
                    lane,
                    offset
            );

            notes.remove(
                    nearest
            );
        }
    }

    private void releaseHold(
            int lane,
            long now
    ) {
        Note held =
                null;

        for (
                Note note
                : notes
        ) {
            if (
                    note.lane == lane
                            && note.holding
            ) {
                held =
                        note;

                break;
            }
        }

        if (
                held == null
        ) {
            return;
        }

        long offset =
                now
                        - held.endTime();

        long normalWindow =
                normalWindowNanos();

        long releaseWindow =
                holdReleaseWindowNanos();

        notes.remove(
                held
        );

        Grade releaseGrade;

        if (
                Math.abs(
                        offset
                )
                        <= normalWindow
        ) {
            releaseGrade =
                    gradeForOffset(
                            offset
                    );

        } else if (
                Math.abs(
                        offset
                )
                        <= releaseWindow
        ) {
            releaseGrade =
                    Grade.OK;

        } else {
            registerMiss(
                    held,
                    "HOLD BROKEN"
            );

            return;
        }

        Grade finalGrade =
                lowerGrade(
                        held.pressGrade,
                        releaseGrade
                );

        score +=
                pointsFor(
                        held.pressGrade
                )
                        + pointsFor(
                        releaseGrade
                );

        judged++;

        successful++;

        combo++;

        maxCombo =
                Math.max(
                        maxCombo,
                        combo
                );

        countGrade(
                finalGrade
        );

        String message =
                releaseGrade == Grade.OK

                        ? "HOLD OK · GRACE"

                        : "HOLD "
                        + finalGrade;

        addFloatingText(
                message,
                gradeColor(
                        finalGrade
                ),
                lane
        );

        spawnCatchEffect(
                lane,
                false,
                finalGrade
        );
    }

    private Grade gradeForOffset(
            long offset
    ) {
        long abs =
                Math.abs(
                        offset
                );

        long window =
                normalWindowNanos();

        long perfectLimit =
                Math.min(
                        35_000_000L,
                        window / 3L
                );

        long greatLimit =
                Math.min(
                        75_000_000L,

                        window
                                * 2L
                                / 3L
                );

        if (
                abs <= perfectLimit
        ) {
            return Grade.PERFECT;
        }

        if (
                abs <= greatLimit
        ) {
            return Grade.GREAT;
        }

        return Grade.GOOD;
    }

    private Grade lowerGrade(
            Grade a,
            Grade b
    ) {
        return gradeRank(a)
                >= gradeRank(b)

                ? a
                : b;
    }

    private int gradeRank(
            Grade grade
    ) {
        return switch (grade) {
            case PERFECT -> 0;
            case GREAT -> 1;
            case GOOD -> 2;
            case OK -> 3;
        };
    }

    private int pointsFor(
            Grade grade
    ) {
        return switch (grade) {
            case PERFECT -> 300;
            case GREAT -> 200;
            case GOOD -> 100;
            case OK -> 50;
        };
    }

    private Color gradeColor(
            Grade grade
    ) {
        return switch (grade) {
            case PERFECT ->
                    SUCCESS;

            case GREAT ->
                    new Color(
                            170,
                            203,
                            255
                    );

            case GOOD ->
                    WARNING;

            case OK ->
                    ACCENT_SOFT;
        };
    }

    private void registerHit(
            Grade grade,
            int lane,
            long offset
    ) {
        score +=
                pointsFor(
                        grade
                );

        judged++;

        successful++;

        combo++;

        maxCombo =
                Math.max(
                        maxCombo,
                        combo
                );

        countGrade(
                grade
        );

        String timing =
                offset < 0

                        ? "EARLY"

                        : offset > 0

                        ? "LATE"

                        : "ON TIME";

        addFloatingText(
                grade
                        + "  "
                        + timing,

                gradeColor(
                        grade
                ),

                lane
        );

        spawnCatchEffect(
                lane,
                false,
                grade
        );
    }

    private void registerMiss(
            Note note,
            String message
    ) {
        judged++;

        misses++;

        combo =
                0;

        addFloatingText(
                message,
                MISS,
                note.lane
        );

        Point receptor =
                receptor(
                        note.lane
                );

        if (visualSettings.get().effectiveIntensity() > 0) {
            rings.add(
                    new Ring(
                            receptor.x,
                            receptor.y,
                            MISS,
                            0.0,
                            0.34,
                            65
                    )
            );

            rings.add(
                    new Ring(
                            receptor.x,
                            receptor.y,
                            Color.WHITE,
                            0.04,
                            0.22,
                            35
                    )
            );

            for (
                    int i = 0;
                    i < scaledParticles(20);
                    i++
            ) {
                double angle =
                        random.nextDouble()
                                * Math.PI
                                * 2.0;

                double speed =
                        25.0
                                + random.nextDouble()
                                * 130.0;

                particles.add(
                        new Particle(
                                receptor.x,
                                receptor.y,

                                Math.cos(
                                        angle
                                )
                                        * speed,

                                Math.sin(
                                        angle
                                )
                                        * speed,

                                MISS,

                                0.25
                                        + random.nextDouble()
                                        * 0.30,

                                1.0
                                        + random.nextDouble()
                                        * 2.0,

                                false,

                                random
                        )
                );
            }

        }

        if (visualSettings.get().allowShake()) {
            shakeMagnitude = Math.max(shakeMagnitude, 1.6);
        }
        requestScreenFlash(MISS, 0.13);
    }

    private void countGrade(
            Grade grade
    ) {
        switch (grade) {
            case PERFECT ->
                    perfects++;

            case GREAT ->
                    greats++;

            case GOOD ->
                    goods++;

            case OK ->
                    oks++;
        }
    }

    private void addFloatingText(
            String text,
            Color color,
            int lane
    ) {
        Point p =
                receptor(
                        lane
                );

        floatingTexts.add(
                new FloatingText(
                        text,
                        color,
                        p.x,
                        p.y - 35
                )
        );
    }

    // ---------------------------- CATCH / CELEBRATION ----------------------------

    private void spawnCatchEffect(
            int lane,
            boolean holdStart,
            Grade grade
    ) {
        if (visualSettings.get().effectiveIntensity() == 0) return;
        Point p =
                receptor(
                        lane
                );

        int x =
                p.x;

        int y =
                p.y;

        Color color =
                LANE_COLORS[
                        lane
                        ];

        boolean milestone =
                !holdStart
                        && combo > 0
                        && combo % 10 == 0;

        boolean grandPrix =
                !holdStart
                        && combo > 0
                        && combo % 25 == 0;

        boolean perfect =
                grade == Grade.PERFECT;

        rings.add(
                new Ring(
                        x,
                        y,
                        color,
                        0.00,
                        0.48,

                        holdStart
                                ? 75
                                : 140
                )
        );

        rings.add(
                new Ring(
                        x,
                        y,
                        Color.WHITE,
                        0.02,
                        0.25,

                        holdStart
                                ? 42
                                : 82
                )
        );

        rings.add(
                new Ring(
                        x,
                        y,

                        SPECTRUM[
                                (
                                        combo + 2
                                )
                                        % SPECTRUM.length
                                ],

                        0.08,
                        0.70,

                        holdStart
                                ? 105
                                : 245
                )
        );

        rings.add(
                new Ring(
                        x,
                        y,
                        color,
                        0.17,
                        0.90,

                        holdStart
                                ? 145
                                : 330
                )
        );

        rings.add(
                new Ring(
                        x,
                        y,
                        Color.WHITE,
                        0.29,
                        0.65,

                        holdStart
                                ? 165
                                : 390
                )
        );

        bursts.add(
                new RibbonBurst(
                        x,
                        y,
                        color,
                        combo,

                        milestone
                                || grandPrix,

                        grandPrix,

                        random.nextDouble()
                                * Math.PI
                                * 2.0
                )
        );

        int count =
                holdStart

                        ? 30

                        : grandPrix

                        ? 330

                        : milestone

                        ? 245

                        : perfect

                        ? 155

                        : 110;

        for (
                int i = 0;
                i < scaledParticles(count);
                i++
        ) {
            double angle =
                    random.nextDouble()
                            * Math.PI
                            * 2.0;

            double speed =
                    (
                            holdStart
                                    ? 55.0
                                    : 110.0
                    )
                            + random.nextDouble()
                            * (
                            grandPrix
                                    ? 520.0
                                    : 360.0
                    );

            double size =
                    1.1
                            + random.nextDouble()
                            * (
                            grandPrix
                                    ? 4.8
                                    : 3.8
                    );

            Color particleColor;

            if (
                    i % 8 == 0
            ) {
                particleColor =
                        Color.WHITE;

            } else if (
                    i % 3 == 0
            ) {
                particleColor =
                        SPECTRUM[
                                (
                                        i + combo
                                )
                                        % SPECTRUM.length
                                ];

            } else {
                particleColor =
                        color;
            }

            particles.add(
                    new Particle(
                            x
                                    + Math.cos(
                                    angle
                            )
                                    * 5.0,

                            y
                                    + Math.sin(
                                    angle
                            )
                                    * 5.0,

                            Math.cos(
                                    angle
                            )
                                    * speed,

                            Math.sin(
                                    angle
                            )
                                    * speed,

                            particleColor,

                            0.36
                                    + random.nextDouble()
                                    * (
                                    grandPrix
                                            ? 1.45
                                            : 0.88
                            ),

                            size,

                            false,

                            random
                    )
            );
        }

        if (
                milestone
                        || grandPrix
        ) {
            int confettiCount =
                    grandPrix
                            ? 260
                            : 150;

            for (
                    int i = 0;
                    i < scaledParticles(confettiCount);
                    i++
            ) {
                double startX =
                        random.nextDouble()
                                * Math.max(
                                1,
                                gamePanel.getWidth()
                        );

                double startY =
                        -random.nextDouble()
                                * gamePanel.getHeight()
                                * 0.7;

                double vx =
                        (
                                random.nextDouble()
                                        - 0.5
                        )
                                * 120.0;

                double vy =
                        70.0
                                + random.nextDouble()
                                * 210.0;

                particles.add(
                        new Particle(
                                startX,
                                startY,
                                vx,
                                vy,

                                SPECTRUM[
                                        random.nextInt(
                                                SPECTRUM.length
                                        )
                                        ],

                                1.3
                                        + random.nextDouble()
                                        * 2.3,

                                3.0
                                        + random.nextDouble()
                                        * 5.0,

                                true,

                                random
                        )
                );
            }

            addFloatingText(
                    grandPrix
                            ? "GRAND PRIX WINNER"
                            : "VICTORY LAP",

                    Color.WHITE,

                    lane
            );
        }

        if (visualSettings.get().allowShake()) {
            shakeMagnitude = Math.max(shakeMagnitude,
                    grandPrix ? 13.0 : milestone ? 8.0 : holdStart ? 2.0 : perfect ? 5.2 : 3.2);
        }
        requestScreenFlash(grandPrix ? Color.WHITE : color,
                grandPrix ? 1.55 : milestone ? 1.1 : holdStart ? 0.22 : perfect ? 0.85 : 0.62);

        if (
                particles.size()
                        > MAX_PARTICLES
        ) {
            particles
                    .subList(
                            0,

                            particles.size()
                                    - MAX_PARTICLES
                    )
                    .clear();
        }

        if (
                rings.size() > 90
        ) {
            rings
                    .subList(
                            0,

                            rings.size()
                                    - 90
                    )
                    .clear();
        }

        if (
                bursts.size() > 10
        ) {
            bursts
                    .subList(
                            0,

                            bursts.size()
                                    - 10
                    )
                    .clear();
        }
    }

    private void spawnVictoryLap(
            double x,
            double y,
            boolean grandPrix
    ) {
        if (visualSettings.get().effectiveIntensity() == 0) return;
        bursts.add(
                new RibbonBurst(
                        x,
                        y,

                        Color.WHITE,

                        combo,

                        true,

                        grandPrix,

                        random.nextDouble()
                                * Math.PI
                                * 2.0
                )
        );

        for (
                int i = 0;
                i < scaledParticles(grandPrix ? 340 : 180);
                i++
        ) {
            double px =
                    random.nextDouble()
                            * Math.max(
                            1,
                            gamePanel.getWidth()
                    );

            double py =
                    -random.nextDouble()
                            * Math.max(
                            1,
                            gamePanel.getHeight()
                    );

            particles.add(
                    new Particle(
                            px,
                            py,

                            (
                                    random.nextDouble()
                                            - 0.5
                            )
                                    * 120.0,

                            90.0
                                    + random.nextDouble()
                                    * 240.0,

                            SPECTRUM[
                                    random.nextInt(
                                            SPECTRUM.length
                                    )
                                    ],

                            1.8
                                    + random.nextDouble()
                                    * 2.0,

                            3.0
                                    + random.nextDouble()
                                    * 5.0,

                            true,

                            random
                    )
            );
        }
    }

    // ---------------------------- HUD ----------------------------

    private void refreshHud() {
        synchronized (gameLock) {
            scoreValue.setText(
                    String.format(
                            "%06d",
                            score
                    )
            );

            comboValue.setText(
                    Integer.toString(
                            combo
                    )
            );

            double accuracy =
                    judged == 0

                            ? 100.0

                            : successful
                            * 100.0
                            / judged;

            accuracyValue.setText(
                    String.format(
                            "%.1f%%",
                            accuracy
                    )
            );

            if (
                    state == GameState.PLAYING
            ) {
                long remaining =
                        Math.max(
                                0L,

                                roundEnd
                                        - gameplayTimeNanos()
                        );

                long seconds =
                        (
                                remaining
                                        + 999_999_999L
                        )
                                / 1_000_000_000L;

                timeValue.setText(
                        String.format(
                                "%02d:%02d",

                                seconds / 60,

                                seconds % 60
                        )
                );

            } else if (
                    state == GameState.MENU
            ) {
                timeValue.setText(
                        audioReady

                                ? formatTime(
                                songDurationNanos
                        )

                                : "--:--"
                );

            } else if (
                    state == GameState.PAUSED
            ) {
                timeValue.setText(
                        "PAUSE"
                );

            } else {
                timeValue.setText(
                        "DONE"
                );
            }

            pauseButton.setEnabled(
                    state == GameState.PLAYING
                            || state == GameState.PAUSED
            );

            pauseButton.setText(
                    state == GameState.PAUSED
                            ? "RESUME"
                            : "PAUSE"
            );

            startButton.setEnabled(
                    audioReady
                            && chartReady
                            && (
                            state == GameState.MENU
                                    || state == GameState.RESULTS
                    )
            );

            startButton.setText(
                    state == GameState.RESULTS
                            ? "PLAY AGAIN"
                            : "PLAY"
            );
        }
    }

    // ---------------------------- CURVED LANE GEOMETRY ----------------------------

    private static final class Point {
        final int x;
        final int y;

        Point(
                int x,
                int y
        ) {
            this.x =
                    x;

            this.y =
                    y;
        }
    }

    private Point receptor(
            int lane
    ) {
        int cx =
                gamePanel.getWidth()
                        / 2;

        int cy =
                gamePanel.getHeight()
                        / 2
                        + 6;

        int r =
                Math.min(
                        132,

                        Math.max(
                                94,

                                (int) (
                                        Math.min(
                                                gamePanel.getWidth(),
                                                gamePanel.getHeight()
                                        )
                                                * 0.205
                                )
                        )
                );

        return switch (lane) {
            case 0 ->
                    new Point(
                            cx - r,
                            cy + 30
                    );

            case 1 ->
                    new Point(
                            cx + 15,
                            cy - r
                    );

            default ->
                    new Point(
                            cx + r,
                            cy + 30
                    );
        };
    }

    private CubicCurve2D.Double laneCurve(
            int lane,
            int w,
            int h
    ) {
        double cx =
                w / 2.0;

        double cy =
                h / 2.0
                        + 6.0;

        double r =
                Math.min(
                        132,

                        Math.max(
                                94,

                                Math.min(
                                        w,
                                        h
                                )
                                        * 0.205
                        )
                );

        if (
                lane == 0
        ) {
            return new CubicCurve2D.Double(
                    -30,
                    cy + h * 0.20,

                    cx - w * 0.21,
                    cy - h * 0.38,

                    cx - w * 0.39,
                    cy + h * 0.27,

                    cx - r,
                    cy + 30
            );
        }

        if (
                lane == 1
        ) {
            return new CubicCurve2D.Double(
                    cx - w * 0.12,
                    -45,

                    cx + w * 0.28,
                    cy - h * 0.30,

                    cx - w * 0.30,
                    cy - h * 0.30,

                    cx + 15,
                    cy - r
            );
        }

        return new CubicCurve2D.Double(
                w + 30,
                cy + h * 0.20,

                cx + w * 0.21,
                cy - h * 0.38,

                cx + w * 0.39,
                cy + h * 0.27,

                cx + r,
                cy + 30
        );
    }

    private static Point2D.Double curvePoint(
            CubicCurve2D curve,
            double t
    ) {
        double u =
                1.0
                        - t;

        double x =
                u * u * u
                        * curve.getX1()

                        + 3
                        * u
                        * u
                        * t
                        * curve.getCtrlX1()

                        + 3
                        * u
                        * t
                        * t
                        * curve.getCtrlX2()

                        + t
                        * t
                        * t
                        * curve.getX2();

        double y =
                u * u * u
                        * curve.getY1()

                        + 3
                        * u
                        * u
                        * t
                        * curve.getCtrlY1()

                        + 3
                        * u
                        * t
                        * t
                        * curve.getCtrlY2()

                        + t
                        * t
                        * t
                        * curve.getY2();

        return new Point2D.Double(
                x,
                y
        );
    }

    // ---------------------------- GAME PANEL ----------------------------

    private class GamePanel extends JPanel {
        private static final long serialVersionUID =
                1L;

        private transient BufferedImage backdrop;
        private transient BufferedImage frameBuffer;
        private transient BufferedImage bloomLow;
        private transient BufferedImage bloomFull;

        private transient int[] bloomScratch;

        private int backdropWidth =
                -1;

        private int backdropHeight =
                -1;

        private int frameWidth =
                -1;

        private int frameHeight =
                -1;

        private int bloomWidth =
                -1;

        private int bloomHeight =
                -1;

        GamePanel() {
            setOpaque(
                    true
            );

            setFocusable(
                    true
            );

            setBackground(
                    BG
            );

            setPreferredSize(
                    new Dimension(
                            1000,
                            640
                    )
            );
        }

        @Override
        protected void paintComponent(
                Graphics graphics
        ) {
            super.paintComponent(
                    graphics
            );

            int w =
                    getWidth();

            int h =
                    getHeight();

            if (
                    w <= 0
                            || h <= 0
            ) {
                return;
            }

            ensureBuffers(
                    w,
                    h
            );

            Graphics2D g =
                    frameBuffer.createGraphics();

            g.setComposite(
                    AlphaComposite.Src
            );

            g.setColor(
                    BG
            );

            g.fillRect(
                    0,
                    0,
                    w,
                    h
            );

            g.setComposite(
                    AlphaComposite.SrcOver
            );

            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED
            );

            drawAtmosphere(
                    g,
                    w,
                    h
            );

            synchronized (gameLock) {
                if (
                        state == GameState.MENU
                ) {
                    drawMenu(
                            g,
                            w,
                            h
                    );

                } else {
                    Graphics2D field =
                            (Graphics2D) g.create();

                    if (visualSettings.get().allowShake()) {
                        field.translate(shakeX, shakeY);
                    }

                    drawPlayfield(
                            field,
                            w,
                            h
                    );

                    field.dispose();

                    if (
                            state == GameState.PAUSED
                    ) {
                        drawOverlay(
                                g,
                                w,
                                h,

                                "PAUSED",

                                "ENTER OR ESCAPE TO RESUME"
                        );

                    } else if (
                            state == GameState.RESULTS
                    ) {
                        drawResults(
                                g,
                                w,
                                h
                        );
                    }
                }
            }

            g.dispose();

            if (visualSettings.get().effectiveBloom() > 0) applyBloom();

            Graphics2D output =
                    (Graphics2D) graphics.create();

            output.setComposite(
                    AlphaComposite.SrcOver
            );

            output.drawImage(
                    frameBuffer,
                    0,
                    0,
                    null
            );

            output.dispose();
        }

        private void ensureBuffers(
                int w,
                int h
        ) {
            if (
                    frameBuffer == null
                            || frameWidth != w
                            || frameHeight != h
            ) {
                if (
                        frameBuffer != null
                ) {
                    frameBuffer.flush();
                }

                frameBuffer =
                        new BufferedImage(
                                w,
                                h,
                                BufferedImage.TYPE_INT_ARGB
                        );

                frameWidth =
                        w;

                frameHeight =
                        h;
            }

            int bw =
                    Math.max(
                            1,

                            (
                                    w + 3
                            )
                                    / 4
                    );

            int bh =
                    Math.max(
                            1,

                            (
                                    h + 3
                            )
                                    / 4
                    );

            if (
                    bloomLow == null
                            || bloomWidth != bw
                            || bloomHeight != bh
            ) {
                if (
                        bloomLow != null
                ) {
                    bloomLow.flush();
                }

                if (
                        bloomFull != null
                ) {
                    bloomFull.flush();
                }

                bloomLow =
                        new BufferedImage(
                                bw,
                                bh,
                                BufferedImage.TYPE_INT_ARGB
                        );

                bloomFull =
                        new BufferedImage(
                                w,
                                h,
                                BufferedImage.TYPE_INT_ARGB
                        );

                bloomScratch =
                        new int[
                                bw * bh
                                ];

                bloomWidth =
                        bw;

                bloomHeight =
                        bh;
            }
        }

        // ---------------------- REAL ADDITIVE BLOOM ----------------------

        private void applyBloom() {
            Graphics2D down =
                    bloomLow.createGraphics();

            down.setComposite(
                    AlphaComposite.Src
            );

            down.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            down.drawImage(
                    frameBuffer,
                    0,
                    0,
                    bloomWidth,
                    bloomHeight,
                    null
            );

            down.dispose();

            int[] low =
                    (
                            (DataBufferInt)
                                    bloomLow
                                            .getRaster()
                                            .getDataBuffer()
                    )
                            .getData();

            for (
                    int i = 0;
                    i < low.length;
                    i++
            ) {
                int pixel =
                        low[i];

                int r =
                        (
                                pixel >>> 16
                        )
                                & 255;

                int gr =
                        (
                                pixel >>> 8
                        )
                                & 255;

                int b =
                        pixel
                                & 255;

                int luminance =
                        (
                                r * 54
                                        + gr * 183
                                        + b * 19
                        )
                                >> 8;

                double threshold =
                        Math.max(
                                0.0,

                                Math.min(
                                        1.0,

                                        (
                                                luminance
                                                        - 72.0
                                        )
                                                / 150.0
                                )
                        );

                double strength =
                        threshold
                                * threshold
                                * 1.28;

                low[i] =
                        0xFF000000

                                | (
                                (int) Math.min(
                                        255,

                                        r
                                                * strength
                                )
                                        << 16
                        )

                                | (
                                (int) Math.min(
                                        255,

                                        gr
                                                * strength
                                )
                                        << 8
                        )

                                | (int) Math.min(
                                255,

                                b
                                        * strength
                        );
            }

            blurBloom(
                    low,
                    bloomScratch,
                    bloomWidth,
                    bloomHeight,
                    4
            );

            blurBloom(
                    low,
                    bloomScratch,
                    bloomWidth,
                    bloomHeight,
                    4
            );

            Graphics2D up =
                    bloomFull.createGraphics();

            up.setComposite(
                    AlphaComposite.Src
            );

            up.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            up.drawImage(
                    bloomLow,
                    0,
                    0,
                    frameWidth,
                    frameHeight,
                    null
            );

            up.dispose();

            int[] frame =
                    (
                            (DataBufferInt)
                                    frameBuffer
                                            .getRaster()
                                            .getDataBuffer()
                    )
                            .getData();

            int[] glow =
                    (
                            (DataBufferInt)
                                    bloomFull
                                            .getRaster()
                                            .getDataBuffer()
                    )
                            .getData();

            final int gain = 188 * visualSettings.get().effectiveBloom() / 100;

            for (
                    int i = 0;
                    i < frame.length;
                    i++
            ) {
                int base =
                        frame[i];

                int bloom =
                        glow[i];

                int r =
                        Math.min(
                                255,

                                (
                                        (
                                                base >>> 16
                                        )
                                                & 255
                                )

                                        + (
                                        (
                                                (
                                                        bloom >>> 16
                                                )
                                                        & 255
                                        )
                                                * gain
                                                >> 8
                                )
                        );

                int gr =
                        Math.min(
                                255,

                                (
                                        (
                                                base >>> 8
                                        )
                                                & 255
                                )

                                        + (
                                        (
                                                (
                                                        bloom >>> 8
                                                )
                                                        & 255
                                        )
                                                * gain
                                                >> 8
                                )
                        );

                int b =
                        Math.min(
                                255,

                                (
                                        base
                                                & 255
                                )

                                        + (
                                        (
                                                bloom
                                                        & 255
                                        )
                                                * gain
                                                >> 8
                                )
                        );

                frame[i] =
                        (
                                base
                                        & 0xFF000000
                        )
                                | (
                                r << 16
                        )
                                | (
                                gr << 8
                        )
                                | b;
            }
        }

        private void blurBloom(
                int[] pixels,
                int[] scratch,
                int width,
                int height,
                int radius
        ) {
            int span =
                    radius
                            * 2
                            + 1;

            for (
                    int y = 0;
                    y < height;
                    y++
            ) {
                int row =
                        y
                                * width;

                long sr =
                        0;

                long sg =
                        0;

                long sb =
                        0;

                for (
                        int k = -radius;
                        k <= radius;
                        k++
                ) {
                    int p =
                            pixels[
                                    row
                                            + Math.max(
                                            0,

                                            Math.min(
                                                    width - 1,
                                                    k
                                            )
                                    )
                                    ];

                    sr +=
                            (
                                    p >>> 16
                            )
                                    & 255;

                    sg +=
                            (
                                    p >>> 8
                            )
                                    & 255;

                    sb +=
                            p
                                    & 255;
                }

                for (
                        int x = 0;
                        x < width;
                        x++
                ) {
                    scratch[
                            row + x
                            ] =
                            0xFF000000

                                    | (
                                    (int) (
                                            sr / span
                                    )
                                            << 16
                            )

                                    | (
                                    (int) (
                                            sg / span
                                    )
                                            << 8
                            )

                                    | (int) (
                                    sb / span
                            );

                    int remove =
                            pixels[
                                    row
                                            + Math.max(
                                            0,
                                            x - radius
                                    )
                                    ];

                    int add =
                            pixels[
                                    row
                                            + Math.min(
                                            width - 1,
                                            x + radius + 1
                                    )
                                    ];

                    sr +=
                            (
                                    (
                                            add >>> 16
                                    )
                                            & 255
                            )
                                    - (
                                    (
                                            remove >>> 16
                                    )
                                            & 255
                            );

                    sg +=
                            (
                                    (
                                            add >>> 8
                                    )
                                            & 255
                            )
                                    - (
                                    (
                                            remove >>> 8
                                    )
                                            & 255
                            );

                    sb +=
                            (
                                    add
                                            & 255
                            )
                                    - (
                                    remove
                                            & 255
                            );
                }
            }

            for (
                    int x = 0;
                    x < width;
                    x++
            ) {
                long sr =
                        0;

                long sg =
                        0;

                long sb =
                        0;

                for (
                        int k = -radius;
                        k <= radius;
                        k++
                ) {
                    int py =
                            Math.max(
                                    0,

                                    Math.min(
                                            height - 1,
                                            k
                                    )
                            );

                    int p =
                            scratch[
                                    py
                                            * width
                                            + x
                                    ];

                    sr +=
                            (
                                    p >>> 16
                            )
                                    & 255;

                    sg +=
                            (
                                    p >>> 8
                            )
                                    & 255;

                    sb +=
                            p
                                    & 255;
                }

                for (
                        int y = 0;
                        y < height;
                        y++
                ) {
                    pixels[
                            y
                                    * width
                                    + x
                            ] =
                            0xFF000000

                                    | (
                                    (int) (
                                            sr / span
                                    )
                                            << 16
                            )

                                    | (
                                    (int) (
                                            sg / span
                                    )
                                            << 8
                            )

                                    | (int) (
                                    sb / span
                            );

                    int remove =
                            scratch[
                                    Math.max(
                                            0,
                                            y - radius
                                    )
                                            * width
                                            + x
                                    ];

                    int add =
                            scratch[
                                    Math.min(
                                            height - 1,
                                            y + radius + 1
                                    )
                                            * width
                                            + x
                                    ];

                    sr +=
                            (
                                    (
                                            add >>> 16
                                    )
                                            & 255
                            )
                                    - (
                                    (
                                            remove >>> 16
                                    )
                                            & 255
                            );

                    sg +=
                            (
                                    (
                                            add >>> 8
                                    )
                                            & 255
                            )
                                    - (
                                    (
                                            remove >>> 8
                                    )
                                            & 255
                            );

                    sb +=
                            (
                                    add
                                            & 255
                            )
                                    - (
                                    remove
                                            & 255
                            );
                }
            }
        }

        // ---------------------- FLUID BACKGROUND ----------------------

        private void drawAtmosphere(
                Graphics2D g,
                int w,
                int h
        ) {
            double time =
                    animationClock
                            / 1_000_000_000.0;

            if (
                    backdrop == null
                            || backdropWidth != w
                            || backdropHeight != h
            ) {
                rebuildNebula(
                        w,
                        h
                );
            }

            g.drawImage(
                    backdrop,
                    0,
                    0,
                    null
            );

            if (visualSettings.get().allowMotion()) {
                for (
                        FluidBlob blob
                        : blobs
                ) {
                    drawFluidBlob(
                            g,
                            blob,
                            w,
                            h,
                            time
                    );
                }

                drawLiquidVeils(
                        g,
                        w,
                        h,
                        time
                );

                drawMotes(
                        g,
                        time
                );

            }

            drawAtmosphereVignette(
                    g,
                    w,
                    h
            );

            if (
                    visualSettings.get().allowBeatPulses() && beatPulse > 0.02
            ) {
                int alpha =
                        (int) (
                                beatPulse
                                        * 13.0
                        );

                g.setColor(
                        new Color(
                                134,
                                168,
                                255,
                                alpha
                        )
                );

                g.fillRect(
                        0,
                        0,
                        w,
                        h
                );
            }
        }

        private void rebuildNebula(
                int w,
                int h
        ) {
            if (
                    backdrop != null
            ) {
                backdrop.flush();
            }

            int tw =
                    Math.max(
                            220,
                            w / 2
                    );

            int th =
                    Math.max(
                            150,
                            h / 2
                    );

            backdrop =
                    new BufferedImage(
                            tw,
                            th,
                            BufferedImage.TYPE_INT_RGB
                    );

            backdropWidth =
                    w;

            backdropHeight =
                    h;

            int[] pixels =
                    (
                            (DataBufferInt)
                                    backdrop
                                            .getRaster()
                                            .getDataBuffer()
                    )
                            .getData();

            for (
                    int y = 0;
                    y < th;
                    y++
            ) {
                double ny =
                        y
                                / (double) th;

                for (
                        int x = 0;
                        x < tw;
                        x++
                ) {
                    double nx =
                            x
                                    / (double) tw;

                    double n1 =
                            fbm(
                                    nx * 4.0,
                                    ny * 4.0,
                                    5
                            );

                    double n2 =
                            fbm(
                                    nx * 8.5 + 3.7,
                                    ny * 7.2 - 1.9,
                                    4
                            );

                    double n3 =
                            fbm(
                                    nx * 18.0 - 2.2,
                                    ny * 16.0 + 5.4,
                                    3
                            );

                    double turbulence =
                            n1 * 0.58
                                    + n2 * 0.30
                                    + n3 * 0.12;

                    double wisps =
                            Math.pow(
                                    Math.max(
                                            0.0,

                                            1.0
                                                    - Math.abs(
                                                    turbulence
                                                            - 0.55
                                            )
                                                    * 2.35
                                    ),

                                    2.0
                            );

                    double cloud =
                            Math.max(
                                    0.0,

                                    Math.min(
                                            1.0,

                                            (
                                                    turbulence
                                                            - 0.31
                                            )
                                                    * 1.65
                                    )
                            );

                    double edge =
                            Math.min(
                                    1.0,

                                    Math.min(
                                            Math.min(
                                                    nx * 2.4,
                                                    (
                                                            1.0 - nx
                                                    )
                                                            * 2.4
                                            ),

                                            Math.min(
                                                    ny * 2.2,
                                                    (
                                                            1.0 - ny
                                                    )
                                                            * 2.2
                                            )
                                    )
                            );

                    double centerGlow =
                            Math.exp(
                                    -(
                                            Math.pow(
                                                    (
                                                            nx - 0.52
                                                    )
                                                            * 2.2,

                                                    2.0
                                            )

                                                    + Math.pow(
                                                    (
                                                            ny - 0.48
                                                    )
                                                            * 2.1,

                                                    2.0
                                            )
                                    )
                                            * 1.1
                            );

                    double intensity =
                            (
                                    cloud * 0.72
                                            + wisps * 0.48
                            )
                                    * edge;

                    double r =
                            5
                                    + intensity * 23
                                    + centerGlow * 5;

                    double gr =
                            7
                                    + intensity * 28
                                    + centerGlow * 8;

                    double b =
                            17
                                    + intensity * 62
                                    + centerGlow * 20;

                    double cyanPool =
                            Math.exp(
                                    -(
                                            Math.pow(
                                                    (
                                                            nx - 0.12
                                                    )
                                                            * 3.1,

                                                    2.0
                                            )

                                                    + Math.pow(
                                                    (
                                                            ny - 0.66
                                                    )
                                                            * 2.7,

                                                    2.0
                                            )
                                    )
                                            * 2.2
                            )
                                    * intensity;

                    double violetPool =
                            Math.exp(
                                    -(
                                            Math.pow(
                                                    (
                                                            nx - 0.85
                                                    )
                                                            * 2.5,

                                                    2.0
                                            )

                                                    + Math.pow(
                                                    (
                                                            ny - 0.28
                                                    )
                                                            * 3.0,

                                                    2.0
                                            )
                                    )
                                            * 2.0
                            )
                                    * intensity;

                    r +=
                            violetPool * 45
                                    + wisps * 4;

                    gr +=
                            cyanPool * 36
                                    + violetPool * 5;

                    b +=
                            cyanPool * 66
                                    + violetPool * 55;

                    pixels[
                            y * tw
                                    + x
                            ] =
                            0xFF000000

                                    | (
                                    (int) clamp(
                                            r,
                                            0,
                                            255
                                    )
                                            << 16
                            )

                                    | (
                                    (int) clamp(
                                            gr,
                                            0,
                                            255
                                    )
                                            << 8
                            )

                                    | (int) clamp(
                                    b,
                                    0,
                                    255
                            );
                }
            }
        }

        private double fbm(
                double x,
                double y,
                int octaves
        ) {
            double value =
                    0.0;

            double amplitude =
                    0.5;

            double total =
                    0.0;

            for (
                    int i = 0;
                    i < octaves;
                    i++
            ) {
                value +=
                        valueNoise(
                                x,
                                y
                        )
                                * amplitude;

                total +=
                        amplitude;

                x *=
                        2.03;

                y *=
                        2.01;

                amplitude *=
                        0.5;
            }

            return value
                    / total;
        }

        private double valueNoise(
                double x,
                double y
        ) {
            int ix =
                    (int) Math.floor(
                            x
                    );

            int iy =
                    (int) Math.floor(
                            y
                    );

            double fx =
                    x
                            - ix;

            double fy =
                    y
                            - iy;

            fx =
                    fx
                            * fx
                            * (
                            3.0
                                    - 2.0
                                    * fx
                    );

            fy =
                    fy
                            * fy
                            * (
                            3.0
                                    - 2.0
                                    * fy
                    );

            double a =
                    hash(
                            ix,
                            iy
                    );

            double b =
                    hash(
                            ix + 1,
                            iy
                    );

            double c =
                    hash(
                            ix,
                            iy + 1
                    );

            double d =
                    hash(
                            ix + 1,
                            iy + 1
                    );

            return lerp(
                    lerp(
                            a,
                            b,
                            fx
                    ),

                    lerp(
                            c,
                            d,
                            fx
                    ),

                    fy
            );
        }

        private double hash(
                int x,
                int y
        ) {
            long n =
                    x * 374761393L
                            + y * 668265263L
                            + 1442695040888963407L;

            n =
                    (
                            n
                                    ^ (
                                    n >> 13
                            )
                    )
                            * 1274126177L;

            n ^=
                    n >> 16;

            return (
                    n
                            & 0x7fffffffL
            )
                    / (double) 0x7fffffffL;
        }

        private void drawFluidBlob(
                Graphics2D g,
                FluidBlob blob,
                int w,
                int h,
                double time
        ) {
            double cx =
                    blob.seedX
                            * w
                            + Math.sin(
                            time
                                    * blob.driftX
                                    + blob.phase
                    )
                            * 55.0;

            double cy =
                    blob.seedY
                            * h
                            + Math.cos(
                            time
                                    * blob.driftY
                                    + blob.phase
                    )
                            * 42.0;

            double radius =
                    blob.radius
                            * Math.min(
                            w,
                            h
                    )
                            * (
                            0.91
                                    + 0.07
                                    * Math.sin(
                                    time * 0.23
                                            + blob.phase
                            )
                    );

            Shape shape =
                    organicShape(
                            cx,
                            cy,
                            radius,
                            blob.phase,
                            time * 0.19
                    );

            Graphics2D layer =
                    (Graphics2D) g.create();

            layer.clip(
                    shape
            );

            float rr =
                    (float) Math.max(
                            1.0,
                            radius * 1.35
                    );

            layer.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Float(
                                    (float) (
                                            cx
                                                    - radius
                                                    * 0.1
                                    ),

                                    (float) (
                                            cy
                                                    - radius
                                                    * 0.15
                                    )
                            ),

                            rr,

                            new float[]{
                                    0f,
                                    0.28f,
                                    0.58f,
                                    0.84f,
                                    1f
                            },

                            new Color[]{
                                    withAlpha(
                                            blob.color,
                                            48
                                    ),

                                    withAlpha(
                                            blob.color,
                                            32
                                    ),

                                    withAlpha(
                                            blob.color,
                                            20
                                    ),

                                    withAlpha(
                                            blob.color,
                                            8
                                    ),

                                    withAlpha(
                                            blob.color,
                                            0
                                    )
                            }
                    )
            );

            layer.fillRect(
                    (int) (
                            cx
                                    - radius
                                    * 1.5
                    ),

                    (int) (
                            cy
                                    - radius
                                    * 1.5
                    ),

                    (int) (
                            radius
                                    * 3.0
                    ),

                    (int) (
                            radius
                                    * 3.0
                    )
            );

            layer.dispose();

            g.setStroke(
                    new BasicStroke(
                            1.0f
                    )
            );

            g.setColor(
                    withAlpha(
                            blob.color,
                            12
                    )
            );

            g.draw(
                    shape
            );
        }

        private Shape organicShape(
                double cx,
                double cy,
                double radius,
                double phase,
                double time
        ) {
            final int count =
                    18;

            double[] px =
                    new double[
                            count
                            ];

            double[] py =
                    new double[
                            count
                            ];

            for (
                    int i = 0;
                    i < count;
                    i++
            ) {
                double a =
                        i
                                * Math.PI
                                * 2.0
                                / count;

                double wave =
                        1.0

                                + 0.16
                                * Math.sin(
                                a * 3.0
                                        + phase
                                        + time
                        )

                                + 0.105
                                * Math.sin(
                                a * 5.0
                                        - phase * 1.4
                                        - time * 0.8
                        )

                                + 0.055
                                * Math.cos(
                                a * 7.0
                                        + phase * 0.7
                                        + time * 0.55
                        );

                double r =
                        radius
                                * wave;

                px[i] =
                        cx
                                + Math.cos(
                                a
                        )
                                * r;

                py[i] =
                        cy
                                + Math.sin(
                                a
                        )
                                * r
                                * 0.84;
            }

            Path2D path =
                    new Path2D.Double();

            path.moveTo(
                    (
                            px[0]
                                    + px[
                                    count - 1
                                    ]
                    )
                            / 2.0,

                    (
                            py[0]
                                    + py[
                                    count - 1
                                    ]
                    )
                            / 2.0
            );

            for (
                    int i = 0;
                    i < count;
                    i++
            ) {
                int prev =
                        (
                                i - 1
                                        + count
                        )
                                % count;

                int next =
                        (
                                i + 1
                        )
                                % count;

                int after =
                        (
                                i + 2
                        )
                                % count;

                double midX =
                        (
                                px[i]
                                        + px[next]
                        )
                                / 2.0;

                double midY =
                        (
                                py[i]
                                        + py[next]
                        )
                                / 2.0;

                double c1x =
                        px[i]
                                + (
                                px[next]
                                        - px[prev]
                        )
                                / 6.0;

                double c1y =
                        py[i]
                                + (
                                py[next]
                                        - py[prev]
                        )
                                / 6.0;

                double c2x =
                        px[next]
                                - (
                                px[after]
                                        - px[i]
                        )
                                / 6.0;

                double c2y =
                        py[next]
                                - (
                                py[after]
                                        - py[i]
                        )
                                / 6.0;

                path.curveTo(
                        c1x,
                        c1y,
                        c2x,
                        c2y,
                        midX,
                        midY
                );
            }

            path.closePath();

            return path;
        }

        private void drawLiquidVeils(
                Graphics2D g,
                int w,
                int h,
                double time
        ) {
            drawVeil(
                    g,
                    w,
                    h,

                    new Point2D.Double(
                            -w * 0.12,
                            h * 0.42
                    ),

                    new Point2D.Double(
                            w * 0.19,
                            h * 0.12
                    ),

                    new Point2D.Double(
                            w * 0.30,
                            h * 0.91
                    ),

                    new Point2D.Double(
                            w * 0.55,
                            h * 0.65
                    ),

                    new Color(
                            61,
                            115,
                            255
                    ),

                    time,
                    74f,
                    12
            );

            drawVeil(
                    g,
                    w,
                    h,

                    new Point2D.Double(
                            w * 0.54,
                            h * 0.02
                    ),

                    new Point2D.Double(
                            w * 0.42,
                            h * 0.45
                    ),

                    new Point2D.Double(
                            w * 0.94,
                            h * 0.18
                    ),

                    new Point2D.Double(
                            w * 1.08,
                            h * 0.60
                    ),

                    new Color(
                            222,
                            64,
                            192
                    ),

                    time + 1.8,
                    58f,
                    9
            );

            drawVeil(
                    g,
                    w,
                    h,

                    new Point2D.Double(
                            w * 0.12,
                            h * 1.08
                    ),

                    new Point2D.Double(
                            w * 0.47,
                            h * 0.63
                    ),

                    new Point2D.Double(
                            w * 0.52,
                            h * 1.13
                    ),

                    new Point2D.Double(
                            w * 0.88,
                            h * 0.75
                    ),

                    new Color(
                            28,
                            195,
                            227
                    ),

                    time + 3.1,
                    48f,
                    8
            );
        }

        private void drawVeil(
                Graphics2D g,
                int w,
                int h,
                Point2D.Double a,
                Point2D.Double b,
                Point2D.Double c,
                Point2D.Double d,
                Color color,
                double time,
                float width,
                int strands
        ) {
            double drift =
                    Math.sin(
                            time * 0.17
                    )
                            * 25.0;

            CubicCurve2D curve =
                    new CubicCurve2D.Double(
                            a.x + drift,
                            a.y,

                            b.x - drift * 0.5,
                            b.y
                                    + Math.cos(
                                    time * 0.15
                            )
                                    * 18,

                            c.x + drift * 0.3,
                            c.y
                                    + Math.sin(
                                    time * 0.13
                            )
                                    * 22,

                            d.x - drift,
                            d.y
                    );

            g.setStroke(
                    new BasicStroke(
                            width,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            g.setColor(
                    withAlpha(
                            color,
                            7
                    )
            );

            g.draw(
                    curve
            );

            for (
                    int i = 0;
                    i < strands;
                    i++
            ) {
                double offset =
                        (
                                i
                                        - strands
                                        / 2.0
                        )
                                * 3.2;

                CubicCurve2D strand =
                        new CubicCurve2D.Double(
                                a.x
                                        + offset
                                        + drift,

                                a.y
                                        + offset
                                        * 0.5,

                                b.x
                                        + offset
                                        - drift
                                        * 0.5,

                                b.y
                                        - offset,

                                c.x
                                        - offset
                                        + drift
                                        * 0.3,

                                c.y
                                        + offset,

                                d.x
                                        - offset
                                        - drift,

                                d.y
                                        - offset
                                        * 0.3
                        );

                g.setStroke(
                        new BasicStroke(
                                i % 3 == 0
                                        ? 1.4f
                                        : 0.65f,

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                color,

                                5
                                        + (int) (
                                        3
                                                * (
                                                0.5
                                                        + 0.5
                                                        * Math.sin(
                                                        time
                                                                + i
                                                )
                                        )
                                )
                        )
                );

                g.draw(
                        strand
                );
            }
        }

        private void drawMotes(
                Graphics2D g,
                double time
        ) {
            for (
                    AmbientMote mote
                    : motes
            ) {
                double twinkle =
                        0.55
                                + 0.45
                                * Math.sin(
                                time * 1.8
                                        + mote.x
                                        * 0.01
                        );

                int alpha =
                        (int) (
                                25
                                        * twinkle
                        );

                int size =
                        Math.max(
                                1,
                                (int) mote.radius
                        );

                g.setColor(
                        withAlpha(
                                mote.color,
                                alpha / 3
                        )
                );

                g.fillOval(
                        (int) mote.x
                                - size * 2,

                        (int) mote.y
                                - size * 2,

                        size * 4,

                        size * 4
                );

                g.setColor(
                        withAlpha(
                                mote.color,
                                alpha
                        )
                );

                g.fillOval(
                        (int) mote.x,
                        (int) mote.y,
                        size,
                        size
                );
            }
        }

        private void drawAtmosphereVignette(
                Graphics2D g,
                int w,
                int h
        ) {
            Paint old =
                    g.getPaint();

            g.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Float(
                                    w * 0.5f,
                                    h * 0.46f
                            ),

                            Math.max(
                                    w,
                                    h
                            )
                                    * 0.78f,

                            new float[]{
                                    0f,
                                    0.58f,
                                    0.84f,
                                    1f
                            },

                            new Color[]{
                                    new Color(
                                            4,
                                            7,
                                            17,
                                            0
                                    ),

                                    new Color(
                                            3,
                                            5,
                                            15,
                                            15
                                    ),

                                    new Color(
                                            2,
                                            4,
                                            12,
                                            86
                                    ),

                                    new Color(
                                            1,
                                            2,
                                            8,
                                            165
                                    )
                            }
                    )
            );

            g.fillRect(
                    0,
                    0,
                    w,
                    h
            );

            g.setPaint(
                    old
            );
        }

        // ---------------- MENU ----------------

        private void drawMenu(
                Graphics2D g,
                int w,
                int h
        ) {
            int cx =
                    w / 2;

            int cy =
                    h / 2;

            double time = visualSettings.get().allowMotion()
                    ? animationClock / 1_000_000_000.0 : 0.0;

            drawSoftOrb(
                    g,
                    cx - 175,
                    cy - 60,
                    170,

                    new Color(
                            35,
                            128,
                            255
                    ),

                    42
            );

            drawSoftOrb(
                    g,
                    cx + 180,
                    cy + 40,
                    160,

                    new Color(
                            236,
                            56,
                            180
                    ),

                    37
            );

            drawSoftOrb(
                    g,
                    cx + 25,
                    cy - 170,
                    130,

                    new Color(
                            39,
                            224,
                            220
                    ),

                    22
            );

            drawCentered(
                    g,

                    "oprezzion",

                    cx,

                    cy - 85,

                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            60
                    ),

                    TEXT
            );

            drawCentered(
                    g,

                    "OUT THE CLUB · ROMANCEPLANET",

                    cx,

                    cy - 49,

                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            10
                    ),

                    MUTED
            );

            drawCentered(
                    g,

                    "A / W / D",

                    cx,

                    cy + 22,

                    new Font(
                            "Monospaced",
                            Font.BOLD,
                            15
                    ),

                    ACCENT
            );

            drawCentered(
                    g,

                    "CATCH THE GLOW · HOLD THE LONG NOTES · KEEP THE FLOW",

                    cx,

                    cy + 50,

                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            10
                    ),

                    TEXT
            );

            drawCentered(
                    g,

                    "166.3975 BPM     ·     ENTER TO START     ·     ESC TO PAUSE",

                    cx,

                    cy + 102,

                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            9
                    ),

                    ACCENT_SOFT
            );

            Shape halo =
                    organicShape(
                            cx,
                            cy - 37,
                            250,
                            time,
                            time * 0.12
                    );

            g.setStroke(
                    new BasicStroke(
                            1.0f
                    )
            );

            g.setColor(
                    new Color(
                            164,
                            190,
                            255,
                            16
                    )
            );

            g.draw(
                    halo
            );
        }

        // ---------------- PLAYFIELD ----------------

        private void drawPlayfield(
                Graphics2D g,
                int w,
                int h
        ) {
            long now =
                    gameplayTimeNanos();

            double time =
                    (
                            now
                                    - roundStart
                    )
                            / 1_000_000_000.0;

            long visualNow = visualSettings.get().allowMotion() ? now : 0L;

            if (visualSettings.get().allowMotion()) {
                drawFluidStage(g, w, h, time);
            }

            drawCentralVortex(
                    g,
                    w,
                    h,
                    visualNow
            );

            drawLaneRibbons(
                    g,
                    w,
                    h,
                    visualNow
            );

            for (
                    Note note
                    : notes
            ) {
                if (
                        note.holding
                ) {
                    drawActiveHold(
                            g,
                            note,
                            now,
                            w,
                            h
                    );

                } else {
                    drawIncomingNote(
                            g,
                            note,
                            now,
                            w,
                            h
                    );
                }
            }

            for (
                    int lane = 0;
                    lane < 3;
                    lane++
            ) {
                drawReceptor(
                        g,
                        lane,
                        visualNow
                );
            }

            if (visualSettings.get().effectiveIntensity() > 0) {
                Graphics2D fx = (Graphics2D) g.create();
                fx.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER,
                        visualSettings.get().effectiveIntensity() / 100f));
                drawRings(fx);
                drawBurstRibbons(fx, w, h);
                drawParticles(fx);
                fx.dispose();
            }

            drawFloatingTexts(
                    g
            );

            drawComboText(
                    g,
                    w,
                    h,
                    now
            );

            drawProgress(
                    g,
                    w,
                    h,
                    now
            );

            if (visualSettings.get().allowFlashes()) {
                drawHitFlash(g, w, h);
            }

            drawLegend(
                    g,
                    w,
                    h
            );
        }

        private void drawFluidStage(
                Graphics2D g,
                int w,
                int h,
                double time
        ) {
            drawSoftOrb(
                    g,

                    (int) (
                            w * 0.12
                                    + Math.sin(
                                    time * 0.14
                            )
                                    * 22
                    ),

                    (int) (
                            h * 0.48
                                    + Math.cos(
                                    time * 0.17
                            )
                                    * 15
                    ),

                    145,

                    new Color(
                            32,
                            139,
                            255
                    ),

                    23
                            + (int) (
                            beatPulse * 8
                    )
            );

            drawSoftOrb(
                    g,

                    (int) (
                            w * 0.88
                                    + Math.cos(
                                    time * 0.13
                            )
                                    * 24
                    ),

                    (int) (
                            h * 0.45
                                    + Math.sin(
                                    time * 0.15
                            )
                                    * 18
                    ),

                    160,

                    new Color(
                            244,
                            63,
                            181
                    ),

                    22
                            + (int) (
                            beatPulse * 9
                    )
            );

            drawSoftOrb(
                    g,

                    (int) (
                            w * 0.52
                                    + Math.sin(
                                    time * 0.09
                            )
                                    * 25
                    ),

                    (int) (
                            h * 0.04
                                    + Math.cos(
                                    time * 0.12
                            )
                                    * 15
                    ),

                    115,

                    new Color(
                            57,
                            218,
                            225
                    ),

                    16
            );

            drawFlowingCurtain(
                    g,
                    w,
                    h,
                    time
            );
        }

        private void drawFlowingCurtain(
                Graphics2D g,
                int w,
                int h,
                double time
        ) {
            Graphics2D layer =
                    (Graphics2D) g.create();

            layer.setComposite(
                    AlphaComposite.SrcOver
            );

            for (
                    int i = 0;
                    i < 6;
                    i++
            ) {
                double phase =
                        time
                                * (
                                0.11
                                        + i * 0.009
                        )
                                + i * 1.8;

                Path2D path =
                        new Path2D.Double();

                double startX =
                        -w * 0.08;

                double startY =
                        h
                                * (
                                0.18
                                        + i * 0.13
                        );

                path.moveTo(
                        startX,
                        startY
                );

                path.curveTo(
                        w
                                * (
                                0.17
                                        + 0.08
                                        * Math.sin(
                                        phase
                                )
                        ),

                        h
                                * (
                                0.02
                                        + i * 0.15
                                        + 0.13
                                        * Math.sin(
                                        phase + 1.1
                                )
                        ),

                        w
                                * (
                                0.61
                                        + 0.06
                                        * Math.cos(
                                        phase * 0.75
                                )
                        ),

                        h
                                * (
                                0.83
                                        - i * 0.11
                                        + 0.10
                                        * Math.cos(
                                        phase + 2.0
                                )
                        ),

                        w * 1.08,

                        h
                                * (
                                0.21
                                        + i * 0.12
                                        + 0.09
                                        * Math.sin(
                                        phase + 2.7
                                )
                        )
                );

                Color c =
                        SPECTRUM[
                                (
                                        i
                                                + (int) (
                                                time
                                                        * 0.18
                                        )
                                )
                                        % SPECTRUM.length
                                ];

                layer.setStroke(
                        new BasicStroke(
                                29f
                                        - i * 2.2f,

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                layer.setColor(
                        withAlpha(
                                c,

                                3
                                        + (int) (
                                        beatPulse
                                                * 2
                                )
                        )
                );

                layer.draw(
                        path
                );

                layer.setStroke(
                        new BasicStroke(
                                1.0f
                                        + (
                                        i % 2
                                )
                                        * 0.4f,

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                layer.setColor(
                        withAlpha(
                                c,

                                13
                                        + (int) (
                                        beatPulse
                                                * 4
                                )
                        )
                );

                layer.draw(
                        path
                );
            }

            layer.dispose();
        }

        private void drawCentralVortex(
                Graphics2D g,
                int w,
                int h,
                long now
        ) {
            double time =
                    now
                            / 1_000_000_000.0;

            int cx =
                    w / 2;

            int cy =
                    h / 2
                            + 9;

            double pulse = visualSettings.get().allowBeatPulses() ? beatPulse : 0.0;

            int radius =
                    64
                            + (int) (
                            pulse * 7.0
                    );

            drawSoftOrb(
                    g,
                    cx,
                    cy,

                    165
                            + (int) (
                            pulse * 14.0
                    ),

                    new Color(
                            68,
                            104,
                            255
                    ),

                    38
                            + (int) (
                            pulse * 30.0
                    )
            );

            Shape outside =
                    organicShape(
                            cx,
                            cy,
                            radius * 1.45,
                            1.2,
                            time * 0.55
                    );

            g.setColor(
                    new Color(
                            8,
                            11,
                            25,
                            185
                    )
            );

            g.fill(
                    outside
            );

            Paint old =
                    g.getPaint();

            g.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Float(
                                    cx - radius * 0.22f,
                                    cy - radius * 0.30f
                            ),

                            radius * 1.55f,

                            new float[]{
                                    0f,
                                    0.22f,
                                    0.54f,
                                    0.82f,
                                    1f
                            },

                            new Color[]{
                                    new Color(
                                            255,
                                            255,
                                            255,
                                            245
                                    ),

                                    new Color(
                                            139,
                                            205,
                                            255,
                                            230
                                    ),

                                    new Color(
                                            67,
                                            88,
                                            204,
                                            210
                                    ),

                                    new Color(
                                            87,
                                            44,
                                            165,
                                            165
                                    ),

                                    new Color(
                                            20,
                                            18,
                                            53,
                                            220
                                    )
                            }
                    )
            );

            Shape inner =
                    organicShape(
                            cx,
                            cy,
                            radius,
                            0.3,
                            -time * 0.72
                    );

            g.fill(
                    inner
            );

            g.setPaint(
                    old
            );

            Graphics2D core =
                    (Graphics2D) g.create();

            core.clip(
                    inner
            );

            for (
                    int i = 0;
                    i < 5;
                    i++
            ) {
                double phase =
                        time
                                * (
                                0.42
                                        + i * 0.08
                        )
                                + i * 1.4;

                CubicCurve2D fold =
                        new CubicCurve2D.Double(
                                cx - radius * 1.3,

                                cy
                                        + Math.sin(
                                        phase
                                )
                                        * radius
                                        * 0.7,

                                cx - radius * 0.5,

                                cy
                                        - radius
                                        * Math.cos(
                                        phase + 0.5
                                ),

                                cx + radius * 0.4,

                                cy
                                        + radius
                                        * Math.sin(
                                        phase + 2.0
                                ),

                                cx + radius * 1.4,

                                cy
                                        - radius
                                        * Math.cos(
                                        phase + 1.2
                                )
                        );

                core.setStroke(
                        new BasicStroke(
                                4.0f
                                        + (
                                        i % 2
                                )
                                        * 2.0f,

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                core.setColor(
                        new Color(
                                255,
                                255,
                                255,

                                17
                                        + (int) (
                                        pulse * 22
                                )
                        )
                );

                core.draw(
                        fold
                );
            }

            core.dispose();

            g.setStroke(
                    new BasicStroke(
                            2.0f
                    )
            );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,
                            105
                    )
            );

            g.draw(
                    organicShape(
                            cx,
                            cy,

                            radius
                                    * 0.72,

                            2.2,

                            time
                                    * 0.38
                    )
            );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,
                            185
                    )
            );

            g.fill(
                    new Ellipse2D.Double(
                            cx - 4,
                            cy - 4,
                            8,
                            8
                    )
            );

            double glintAngle =
                    time
                            * 0.7;

            int gx =
                    (int) (
                            cx
                                    + Math.cos(
                                    glintAngle
                            )
                                    * radius
                                    * 0.6
                    );

            int gy =
                    (int) (
                            cy
                                    + Math.sin(
                                    glintAngle
                            )
                                    * radius
                                    * 0.35
                    );

            drawSoftOrb(
                    g,
                    gx,
                    gy,
                    22,
                    Color.WHITE,
                    80
            );
        }

        private void drawLaneRibbons(
                Graphics2D g,
                int w,
                int h,
                long now
        ) {
            double time =
                    now
                            / 1_000_000_000.0;

            for (
                    int lane = 0;
                    lane < 3;
                    lane++
            ) {
                CubicCurve2D curve =
                        laneCurve(
                                lane,
                                w,
                                h
                        );

                Color color =
                        LANE_COLORS[
                                lane
                                ];

                g.setStroke(
                        new BasicStroke(
                                35f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                color,

                                13
                                        + (int) (
                                        beatPulse * 7
                                )
                        )
                );

                g.draw(
                        curve
                );

                g.setStroke(
                        new BasicStroke(
                                18f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        new Color(
                                8,
                                12,
                                27,
                                150
                        )
                );

                g.draw(
                        curve
                );

                g.setStroke(
                        new BasicStroke(
                                6f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                color,

                                43
                                        + (int) (
                                        beatPulse * 19
                                )
                        )
                );

                g.draw(
                        curve
                );

                g.setStroke(
                        new BasicStroke(
                                1.2f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        new Color(
                                245,
                                249,
                                255,
                                112
                        )
                );

                g.draw(
                        curve
                );

                double progress =
                        (
                                time * 0.14
                                        + lane * 0.31
                        )
                                % 1.0;

                Point2D.Double light =
                        curvePoint(
                                curve,
                                progress
                        );

                drawSoftOrb(
                        g,

                        (int) light.x,

                        (int) light.y,

                        28,

                        color,

                        56
                );

                for (
                        int i = 0;
                        i < 4;
                        i++
                ) {
                    double p =
                            (
                                    progress
                                            + i * 0.23
                            )
                                    % 1.0;

                    Point2D.Double droplet =
                            curvePoint(
                                    curve,
                                    p
                            );

                    double size =
                            2.0
                                    + 2.3
                                    * (
                                    0.5
                                            + 0.5
                                            * Math.sin(
                                            time * 1.7
                                                    + lane * 2.0
                                                    + i
                                    )
                            );

                    g.setColor(
                            withAlpha(
                                    color,
                                    55
                            )
                    );

                    g.fill(
                            new Ellipse2D.Double(
                                    droplet.x - size,
                                    droplet.y - size,
                                    size * 2,
                                    size * 2
                            )
                    );
                }
            }
        }

        private void drawReceptor(
                Graphics2D g,
                int lane,
                long now
        ) {
            Point p =
                    receptor(
                            lane
                    );

            Color color =
                    LANE_COLORS[
                            lane
                            ];

            boolean pressed = !visualSettings.get().restrained()
                    && (keyDown[lane] || now < laneFlashUntil[lane]);

            double pulse = visualSettings.get().allowMotion()
                    ? 0.5 + 0.5 * Math.sin(now / 125_000_000.0 + lane * 1.4)
                    : 0.0;

            int radius =
                    pressed
                            ? 28
                            : 23;

            drawSoftOrb(
                    g,
                    p.x,
                    p.y,

                    pressed
                            ? 80
                            : 59,

                    color,

                    pressed
                            ? 115
                            : 72
            );

            Shape outer =
                    organicShape(
                            p.x,
                            p.y,

                            radius
                                    + pulse * 3.0,

                            lane * 1.7,

                            now
                                    / 1_400_000_000.0
                    );

            g.setColor(
                    new Color(
                            5,
                            10,
                            23,
                            220
                    )
            );

            g.fill(
                    outer
            );

            Paint old =
                    g.getPaint();

            g.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Float(
                                    p.x - 7,
                                    p.y - 8
                            ),

                            radius
                                    * 1.65f,

                            new float[]{
                                    0f,
                                    0.34f,
                                    0.74f,
                                    1f
                            },

                            new Color[]{
                                    new Color(
                                            255,
                                            255,
                                            255,
                                            250
                                    ),

                                    withAlpha(
                                            color,
                                            245
                                    ),

                                    new Color(
                                            75,
                                            72,
                                            155,
                                            235
                                    ),

                                    new Color(
                                            12,
                                            17,
                                            40,
                                            250
                                    )
                            }
                    )
            );

            g.fill(
                    outer
            );

            g.setPaint(
                    old
            );

            g.setStroke(
                    new BasicStroke(
                            1.4f
                    )
            );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,

                            pressed
                                    ? 245
                                    : 165
                    )
            );

            g.draw(
                    outer
            );

            Shape shine =
                    new Ellipse2D.Double(
                            p.x
                                    - radius
                                    * 0.53,

                            p.y
                                    - radius
                                    * 0.63,

                            radius
                                    * 0.66,

                            radius
                                    * 0.38
                    );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,

                            pressed
                                    ? 215
                                    : 105
                    )
            );

            g.fill(
                    shine
            );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,

                            pressed
                                    ? 255
                                    : 190
                    )
            );

            g.fill(
                    new Ellipse2D.Double(
                            p.x - 3,
                            p.y - 3,
                            6,
                            6
                    )
            );

            String label =
                    LANE_NAMES[lane]
                            + "  ["
                            + KeyEvent.getKeyText(
                            keys[lane]
                    )
                            + "]";

            Font labelFont =
                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            10
                    );

            Font stateFont =
                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            9
                    );

            if (
                    lane == 0
            ) {
                drawRightAligned(
                        g,
                        label,
                        p.x - 43,
                        p.y - 5,
                        labelFont,
                        TEXT
                );

                drawRightAligned(
                        g,

                        pressed
                                ? "HELD"
                                : "READY",

                        p.x - 43,
                        p.y + 12,
                        stateFont,

                        pressed
                                ? color
                                : MUTED
                );

            } else if (
                    lane == 1
            ) {
                drawCentered(
                        g,
                        label,
                        p.x,
                        p.y - 49,
                        labelFont,
                        TEXT
                );

                drawCentered(
                        g,

                        pressed
                                ? "HELD"
                                : "READY",

                        p.x,
                        p.y - 33,
                        stateFont,

                        pressed
                                ? color
                                : MUTED
                );

            } else {
                g.setFont(
                        labelFont
                );

                g.setColor(
                        TEXT
                );

                g.drawString(
                        label,
                        p.x + 43,
                        p.y - 5
                );

                g.setFont(
                        stateFont
                );

                g.setColor(
                        pressed
                                ? color
                                : MUTED
                );

                g.drawString(
                        pressed
                                ? "HELD"
                                : "READY",

                        p.x + 43,
                        p.y + 12
                );
            }
        }

        private void drawIncomingNote(
                Graphics2D g,
                Note note,
                long now,
                int w,
                int h
        ) {
            CubicCurve2D curve =
                    laneCurve(
                            note.lane,
                            w,
                            h
                    );

            double progress =
                    1.0
                            - (
                            note.targetTime
                                    - now
                    )
                            / (double) NOTE_TRAVEL_NANOS;

            progress =
                    Math.max(
                            0.0,

                            Math.min(
                                    1.12,
                                    progress
                            )
                    );

            Point2D.Double head =
                    curvePoint(
                            curve,
                            progress
                    );

            Color color =
                    LANE_COLORS[
                            note.lane
                            ];

            Path2D tail =
                    new Path2D.Double();

            double tailStart =
                    Math.max(
                            0.0,
                            progress - 0.23
                    );

            Point2D.Double tailPoint =
                    curvePoint(
                            curve,
                            tailStart
                    );

            tail.moveTo(
                    tailPoint.x,
                    tailPoint.y
            );

            for (
                    int i = 1;
                    i <= 12;
                    i++
            ) {
                double p =
                        tailStart
                                + (
                                progress
                                        - tailStart
                        )
                                * i
                                / 12.0;

                Point2D.Double q =
                        curvePoint(
                                curve,
                                p
                        );

                tail.lineTo(
                        q.x,
                        q.y
                );
            }

            g.setStroke(
                    new BasicStroke(
                            note.hold
                                    ? 21f
                                    : 15f,

                            BasicStroke.CAP_ROUND,

                            BasicStroke.JOIN_ROUND
                    )
            );

            g.setColor(
                    withAlpha(
                            color,

                            note.hold
                                    ? 27
                                    : 20
                    )
            );

            g.draw(
                    tail
            );

            g.setStroke(
                    new BasicStroke(
                            note.hold
                                    ? 8f
                                    : 5f,

                            BasicStroke.CAP_ROUND,

                            BasicStroke.JOIN_ROUND
                    )
            );

            g.setColor(
                    withAlpha(
                            color,
                            125
                    )
            );

            g.draw(
                    tail
            );

            drawSoftOrb(
                    g,

                    (int) head.x,

                    (int) head.y,

                    note.hold
                            ? 59
                            : 48,

                    color,

                    125
            );

            Paint old =
                    g.getPaint();

            g.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Float(
                                    (float) head.x - 4,
                                    (float) head.y - 5
                            ),

                            note.hold
                                    ? 17f
                                    : 14f,

                            new float[]{
                                    0f,
                                    0.48f,
                                    1f
                            },

                            new Color[]{
                                    Color.WHITE,

                                    withAlpha(
                                            color,
                                            245
                                    ),

                                    withAlpha(
                                            color,
                                            0
                                    )
                            }
                    )
            );

            int r =
                    note.hold
                            ? 17
                            : 14;

            g.fill(
                    new Ellipse2D.Double(
                            head.x - r,
                            head.y - r,
                            r * 2.0,
                            r * 2.0
                    )
            );

            g.setPaint(
                    old
            );

            for (
                    int i = 0;
                    i < 5;
                    i++
            ) {
                double p =
                        Math.max(
                                0.0,

                                progress
                                        - i * 0.025
                        );

                Point2D.Double q =
                        curvePoint(
                                curve,
                                p
                        );

                double size =
                        1.5
                                + (
                                5 - i
                        )
                                * 0.85;

                g.setColor(
                        withAlpha(
                                color,

                                130
                                        - i * 18
                        )
                );

                g.fill(
                        new Ellipse2D.Double(
                                q.x
                                        - size / 2.0,

                                q.y
                                        - size / 2.0,

                                size,

                                size
                        )
                );
            }

            if (
                    note.hold
            ) {
                int x =
                        (int) head.x;

                int y =
                        (int) head.y;

                double holdProgress =
                        Math.max(
                                0.0,

                                Math.min(
                                        1.0,

                                        (
                                                now
                                                        - (
                                                        note.targetTime
                                                                - NOTE_TRAVEL_NANOS
                                                )
                                        )

                                                / (double) (
                                                NOTE_TRAVEL_NANOS
                                                        + note.duration
                                        )
                                )
                        );

                g.setStroke(
                        new BasicStroke(
                                2.3f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                color,
                                125
                        )
                );

                g.draw(
                        new Arc2D.Double(
                                x - 31,
                                y - 31,
                                62,
                                62,
                                90,

                                -360.0
                                        * holdProgress,

                                Arc2D.OPEN
                        )
                );
            }
        }

        private void drawActiveHold(
                Graphics2D g,
                Note note,
                long now,
                int w,
                int h
        ) {
            Point p =
                    receptor(
                            note.lane
                    );

            Color color =
                    LANE_COLORS[
                            note.lane
                            ];

            double progress =
                    Math.max(
                            0.0,

                            Math.min(
                                    1.0,

                                    (
                                            now
                                                    - note.targetTime
                                    )
                                            / (double) note.duration
                            )
                    );

            long untilRelease =
                    note.endTime()
                            - now;

            long normalMs =
                    normalWindowNanos()
                            / 1_000_000L;

            long totalReleaseMs =
                    normalMs
                            + HOLD_RELEASE_GRACE_MS;

            long lateMs =
                    Math.max(
                            0L,
                            -untilRelease
                    )
                            / 1_000_000L;

            g.setStroke(
                    new BasicStroke(
                            7f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            g.setColor(
                    withAlpha(
                            color,
                            25
                    )
            );

            g.draw(
                    new Arc2D.Double(
                            p.x - 48,
                            p.y - 48,
                            96,
                            96,
                            -90,

                            -360.0
                                    * progress,

                            Arc2D.OPEN
                    )
            );

            g.setStroke(
                    new BasicStroke(
                            3.5f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            g.setColor(
                    withAlpha(
                            Color.WHITE,
                            205
                    )
            );

            g.draw(
                    new Arc2D.Double(
                            p.x - 43,
                            p.y - 43,
                            86,
                            86,
                            -90,

                            -360.0
                                    * progress,

                            Arc2D.OPEN
                    )
            );

            String instruction;

            String detail =
                    "";

            Color instructionColor;

            if (
                    untilRelease
                            > totalReleaseMs
                            * 1_000_000L
            ) {
                instruction =
                        "HOLD  "
                                + Math.round(
                                progress * 100.0
                        )
                                + "%";

                instructionColor =
                        TEXT;

            } else if (
                    untilRelease
                            > normalWindowNanos()
            ) {
                instruction =
                        "EARLY BUFFER";

                detail =
                        "RELEASE IN "
                                + (
                                untilRelease
                                        / 1_000_000L
                        )
                                + " ms";

                instructionColor =
                        ACCENT_SOFT;

            } else if (
                    untilRelease > 0L
            ) {
                instruction =
                        "RELEASE NOW";

                detail =
                        "NORMAL WINDOW";

                instructionColor =
                        WARNING;

            } else if (
                    lateMs <= normalMs
            ) {
                instruction =
                        "RELEASE";

                detail =
                        (
                                normalMs - lateMs
                        )
                                + " ms NORMAL WINDOW LEFT";

                instructionColor =
                        ACCENT;

            } else if (
                    lateMs <= totalReleaseMs
            ) {
                instruction =
                        "PITY ZONE";

                detail =
                        (
                                totalReleaseMs
                                        - lateMs
                        )
                                + " ms EXTRA LEFT";

                instructionColor =
                        ACCENT;

            } else {
                instruction =
                        "RELEASE WINDOW ENDED";

                instructionColor =
                        MISS;
            }

            g.setFont(
                    new Font(
                            "Monospaced",
                            Font.BOLD,
                            11
                    )
            );

            FontMetrics fm =
                    g.getFontMetrics();

            int tx =
                    p.x
                            - fm.stringWidth(
                            instruction
                    )
                            / 2;

            int ty =
                    p.y + 76;

            drawSoftText(
                    g,
                    instruction,
                    tx,
                    ty,
                    g.getFont(),
                    instructionColor,
                    80
            );

            if (
                    !detail.isEmpty()
            ) {
                g.setFont(
                        new Font(
                                "Monospaced",
                                Font.PLAIN,
                                9
                        )
                );

                fm =
                        g.getFontMetrics();

                g.setColor(
                        MUTED
                );

                g.drawString(
                        detail,

                        p.x
                                - fm.stringWidth(
                                detail
                        )
                                / 2,

                        ty + 14
                );
            }
        }

        // ---------------- EFFECT RENDERING ----------------

        private void drawSoftOrb(
                Graphics2D g,
                int cx,
                int cy,
                int radius,
                Color color,
                int strength
        ) {
            strength = strength * visualSettings.get().effectiveIntensity() / 100;

            if (
                    radius <= 0
                            || strength <= 0
            ) {
                return;
            }

            Paint old =
                    g.getPaint();

            float r =
                    Math.max(
                            2f,
                            radius
                    );

            g.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Float(
                                    cx,
                                    cy
                            ),

                            r,

                            new float[]{
                                    0f,
                                    0.24f,
                                    0.54f,
                                    0.82f,
                                    1f
                            },

                            new Color[]{
                                    withAlpha(
                                            color,
                                            strength
                                    ),

                                    withAlpha(
                                            color,

                                            (int) (
                                                    strength * 0.52
                                            )
                                    ),

                                    withAlpha(
                                            color,

                                            (int) (
                                                    strength * 0.19
                                            )
                                    ),

                                    withAlpha(
                                            color,

                                            (int) (
                                                    strength * 0.055
                                            )
                                    ),

                                    withAlpha(
                                            color,
                                            0
                                    )
                            }
                    )
            );

            g.fillOval(
                    cx - radius,
                    cy - radius,
                    radius * 2,
                    radius * 2
            );

            g.setPaint(
                    old
            );
        }

        private void drawRings(
                Graphics2D g
        ) {
            for (
                    Ring ring
                    : rings
            ) {
                if (
                        !ring.visible()
                ) {
                    continue;
                }

                double progress =
                        ring.progress();

                double radius =
                        9.0
                                + ring.maxRadius
                                * easeOutCubic(
                                progress
                        );

                int alpha =
                        (int) (
                                230.0
                                        * Math.pow(
                                        1.0
                                                - progress,

                                        1.45
                                )
                        );

                float width =
                        (float) Math.max(
                                0.7,

                                5.0
                                        * (
                                        1.0
                                                - progress
                                )
                        );

                g.setStroke(
                        new BasicStroke(
                                width + 9f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                ring.color,
                                alpha / 5
                        )
                );

                g.draw(
                        new Ellipse2D.Double(
                                ring.x - radius,
                                ring.y - radius,
                                radius * 2.0,
                                radius * 2.0
                        )
                );

                g.setStroke(
                        new BasicStroke(
                                width,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                ring.color,
                                alpha
                        )
                );

                g.draw(
                        new Ellipse2D.Double(
                                ring.x - radius,
                                ring.y - radius,
                                radius * 2.0,
                                radius * 2.0
                        )
                );
            }
        }

        private void drawBurstRibbons(
                Graphics2D g,
                int w,
                int h
        ) {
            for (
                    RibbonBurst burst
                    : bursts
            ) {
                double p =
                        burst.progress();

                double fade =
                        Math.pow(
                                1.0 - p,
                                1.3
                        );

                double expand =
                        easeOutCubic(
                                p
                        );

                double reach =
                        48
                                + expand
                                * Math.max(
                                w,
                                h
                        )
                                * (
                                burst.victory
                                        ? 0.72
                                        : 0.46
                        );

                int ribbonCount =
                        burst.victory

                                ? (
                                burst.grandPrix
                                        ? 38
                                        : 28
                        )

                                : 15;

                for (
                        int i = 0;
                        i < ribbonCount;
                        i++
                ) {
                    double angle =
                            burst.angle
                                    + i
                                    * Math.PI
                                    * 2.0
                                    / ribbonCount;

                    double bend =
                            34
                                    + (
                                    i % 5
                            )
                                    * 12;

                    double length =
                            reach
                                    * (
                                    0.60
                                            + (
                                            i % 7
                                    )
                                            * 0.055
                            );

                    double sx =
                            burst.x
                                    + Math.cos(
                                    angle
                            )
                                    * (
                                    12
                                            + p * 20
                            );

                    double sy =
                            burst.y
                                    + Math.sin(
                                    angle
                            )
                                    * (
                                    12
                                            + p * 20
                            );

                    double ex =
                            burst.x
                                    + Math.cos(
                                    angle + 0.11
                            )
                                    * length;

                    double ey =
                            burst.y
                                    + Math.sin(
                                    angle + 0.11
                            )
                                    * length;

                    double c1x =
                            sx
                                    + Math.cos(
                                    angle - 0.9
                            )
                                    * bend;

                    double c1y =
                            sy
                                    + Math.sin(
                                    angle - 0.9
                            )
                                    * bend;

                    double c2x =
                            ex
                                    - Math.cos(
                                    angle + 0.7
                            )
                                    * bend
                                    * 1.8;

                    double c2y =
                            ey
                                    - Math.sin(
                                    angle + 0.7
                            )
                                    * bend
                                    * 1.8;

                    CubicCurve2D ribbon =
                            new CubicCurve2D.Double(
                                    sx,
                                    sy,
                                    c1x,
                                    c1y,
                                    c2x,
                                    c2y,
                                    ex,
                                    ey
                            );

                    Color color =
                            burst.victory

                                    ? SPECTRUM[
                                    (
                                            i + burst.combo
                                    )
                                            % SPECTRUM.length
                                    ]

                                    : burst.color;

                    int alpha =
                            (int) (
                                    (
                                            burst.victory
                                                    ? 105
                                                    : 67
                                    )
                                            * fade
                            );

                    g.setStroke(
                            new BasicStroke(
                                    burst.victory
                                            ? 3.2f
                                            : 2.0f,

                                    BasicStroke.CAP_ROUND,

                                    BasicStroke.JOIN_ROUND
                            )
                    );

                    g.setColor(
                            withAlpha(
                                    color,
                                    alpha / 4
                            )
                    );

                    g.draw(
                            ribbon
                    );

                    g.setStroke(
                            new BasicStroke(
                                    burst.victory
                                            ? 1.25f
                                            : 0.8f,

                                    BasicStroke.CAP_ROUND,

                                    BasicStroke.JOIN_ROUND
                            )
                    );

                    g.setColor(
                            withAlpha(
                                    color,
                                    alpha
                            )
                    );

                    g.draw(
                            ribbon
                    );
                }

                drawSoftOrb(
                        g,

                        (int) burst.x,

                        (int) burst.y,

                        (int) (
                                30
                                        + p
                                        * (
                                        burst.victory
                                                ? 250
                                                : 125
                                )
                        ),

                        burst.color,

                        (int) (
                                50
                                        * fade
                        )
                );

                if (
                        burst.victory
                ) {
                    drawVictoryBanner(
                            g,
                            w,
                            burst,
                            fade,
                            p
                    );
                }
            }
        }

        private void drawVictoryBanner(
                Graphics2D g,
                int w,
                RibbonBurst burst,
                double fade,
                double progress
        ) {
            double slide =
                    Math.min(
                            1.0,
                            progress * 4.8
                    );

            slide =
                    slide
                            * slide
                            * (
                            3.0
                                    - 2.0 * slide
                    );

            int bannerW =
                    Math.min(
                            w - 45,

                            burst.grandPrix
                                    ? 700
                                    : 560
                    );

            int bannerH =
                    burst.grandPrix
                            ? 102
                            : 84;

            int x =
                    (
                            w
                                    - bannerW
                    )
                            / 2;

            int y =
                    (int) (
                            -bannerH
                                    + slide
                                    * (
                                    25
                                            + bannerH
                            )
                    );

            int alpha =
                    (int) (
                            210
                                    * fade
                    );

            Shape plate =
                    new RoundRectangle2D.Double(
                            x,
                            y,
                            bannerW,
                            bannerH,
                            34,
                            34
                    );

            g.setColor(
                    new Color(
                            4,
                            7,
                            19,

                            Math.max(
                                    0,
                                    alpha
                            )
                    )
            );

            g.fill(
                    plate
            );

            g.setStroke(
                    new BasicStroke(
                            1.4f
                    )
            );

            g.setColor(
                    new Color(
                            244,
                            249,
                            255,

                            Math.max(
                                    0,
                                    alpha
                            )
                    )
            );

            g.draw(
                    plate
            );

            g.setStroke(
                    new BasicStroke(
                            3.0f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            g.setColor(
                    withAlpha(
                            SPECTRUM[1],

                            Math.max(
                                    0,
                                    alpha / 2
                            )
                    )
            );

            g.draw(
                    new Arc2D.Double(
                            x + 12,
                            y + 10,
                            bannerW - 24,
                            bannerH - 20,
                            205,
                            130,
                            Arc2D.OPEN
                    )
            );

            String headline =
                    burst.grandPrix
                            ? "GRAND PRIX WINNER"
                            : "VICTORY LAP";

            String subline =
                    burst.grandPrix

                            ? "PERFECT RUN  ·  P1  ·  THE CROWD IS YOURS"

                            : "P1  ·  "
                            + burst.combo
                            + " HIT STREAK  ·  SECTOR CLEAN";

            g.setFont(
                    new Font(
                            "SansSerif",
                            Font.BOLD,

                            burst.grandPrix
                                    ? 30
                                    : 27
                    )
            );

            FontMetrics fm =
                    g.getFontMetrics();

            int titleX =
                    w / 2
                            - fm.stringWidth(
                            headline
                    )
                            / 2;

            int titleY =
                    y
                            + (
                            burst.grandPrix
                                    ? 58
                                    : 53
                    );

            g.setColor(
                    new Color(
                            92,
                            151,
                            255,

                            Math.max(
                                    0,
                                    alpha / 2
                            )
                    )
            );

            g.drawString(
                    headline,
                    titleX + 2,
                    titleY + 2
            );

            g.setColor(
                    new Color(
                            255,
                            255,
                            255,

                            Math.max(
                                    0,
                                    alpha
                            )
                    )
            );

            g.drawString(
                    headline,
                    titleX,
                    titleY
            );

            g.setFont(
                    new Font(
                            "Monospaced",
                            Font.BOLD,
                            9
                    )
            );

            fm =
                    g.getFontMetrics();

            g.setColor(
                    new Color(
                            169,
                            218,
                            255,

                            Math.max(
                                    0,
                                    alpha
                            )
                    )
            );

            g.drawString(
                    subline,

                    w / 2
                            - fm.stringWidth(
                            subline
                    )
                            / 2,

                    y
                            + bannerH
                            - 12
            );

            drawFlag(
                    g,
                    x + 24,
                    y + 27,
                    alpha
            );

            drawFlag(
                    g,

                    x
                            + bannerW
                            - 52,

                    y + 27,

                    alpha
            );
        }

        private void drawFlag(
                Graphics2D g,
                int x,
                int y,
                int alpha
        ) {
            Shape flag =
                    new CubicCurve2D.Double(
                            x,
                            y,

                            x + 8,
                            y - 4,

                            x + 18,
                            y + 5,

                            x + 27,
                            y + 1
                    );

            g.setStroke(
                    new BasicStroke(
                            2.2f
                    )
            );

            g.setColor(
                    new Color(
                            240,
                            245,
                            255,

                            Math.max(
                                    0,
                                    alpha
                            )
                    )
            );

            g.draw(
                    flag
            );

            g.setStroke(
                    new BasicStroke(
                            1.2f
                    )
            );

            g.drawLine(
                    x,
                    y,
                    x,
                    y + 26
            );
        }

        private void drawParticles(
                Graphics2D g
        ) {
            for (
                    Particle p
                    : particles
            ) {
                double fade =
                        Math.max(
                                0.0,

                                Math.min(
                                        1.0,

                                        p.life
                                                / p.maxLife
                                )
                        );

                int alpha =
                        (int) (
                                255
                                        * fade
                        );

                if (
                        p.confetti
                ) {
                    Graphics2D shard =
                            (Graphics2D) g.create();

                    shard.translate(
                            p.x,
                            p.y
                    );

                    shard.rotate(
                            p.rotation
                    );

                    shard.setColor(
                            withAlpha(
                                    p.color,
                                    alpha
                            )
                    );

                    double width =
                            p.size
                                    * (
                                    0.55
                                            + 0.45
                                            * Math.abs(
                                            Math.sin(
                                                    p.rotation
                                                            * 1.7
                                            )
                                    )
                            );

                    shard.fillRoundRect(
                            (int) (
                                    -p.size
                                            * 0.5
                            ),

                            (int) (
                                    -p.size
                                            * 0.26
                            ),

                            Math.max(
                                    2,
                                    (int) p.size
                            ),

                            Math.max(
                                    2,
                                    (int) width
                            ),

                            3,
                            3
                    );

                    shard.dispose();

                    continue;
                }

                g.setStroke(
                        new BasicStroke(
                                (float) Math.max(
                                        0.7,
                                        p.size * 0.75
                                ),

                                BasicStroke.CAP_ROUND,

                                BasicStroke.JOIN_ROUND
                        )
                );

                g.setColor(
                        withAlpha(
                                p.color,
                                alpha / 2
                        )
                );

                g.draw(
                        new QuadCurve2D.Double(
                                p.previousX,
                                p.previousY,

                                (
                                        p.previousX
                                                + p.x
                                )
                                        / 2.0
                                        + p.vy
                                        * 0.012,

                                (
                                        p.previousY
                                                + p.y
                                )
                                        / 2.0
                                        - p.vx
                                        * 0.012,

                                p.x,
                                p.y
                        )
                );

                int d =
                        Math.max(
                                1,

                                (int) Math.ceil(
                                        p.size
                                )
                        );

                g.setColor(
                        withAlpha(
                                p.color,
                                alpha
                        )
                );

                g.fillOval(
                        (int) p.x
                                - d / 2,

                        (int) p.y
                                - d / 2,

                        d,
                        d
                );
            }
        }

        private void drawFloatingTexts(
                Graphics2D g
        ) {
            for (
                    FloatingText text
                    : floatingTexts
            ) {
                int alpha =
                        (int) (
                                255.0
                                        * Math.max(
                                        0.0,

                                        Math.min(
                                                1.0,

                                                text.life
                                                        / text.maxLife
                                        )
                                )
                        );

                g.setFont(
                        new Font(
                                "SansSerif",
                                Font.BOLD,
                                14
                        )
                );

                FontMetrics fm =
                        g.getFontMetrics();

                int x =
                        (int) text.x
                                - fm.stringWidth(
                                text.text
                        )
                                / 2;

                drawSoftText(
                        g,
                        text.text,
                        x,
                        (int) text.y,
                        g.getFont(),

                        withAlpha(
                                text.color,
                                alpha
                        ),

                        alpha / 2
                );
            }
        }

        private void drawComboText(
                Graphics2D g,
                int w,
                int h,
                long now
        ) {
            if (
                    combo < 5
            ) {
                return;
            }

            double t =
                    now
                            / 1_000_000_000.0;

            double pulse = visualSettings.get().allowMotion()
                    ? 0.5 + 0.5 * Math.sin(t * 2.4) : 0.0;

            int cx =
                    w / 2;

            int cy =
                    h - 78;

            drawSoftOrb(
                    g,
                    cx,
                    cy - 12,
                    39,
                    ACCENT,
                    32
            );

            drawCentered(
                    g,

                    combo
                            + "x",

                    cx,
                    cy,

                    new Font(
                            "SansSerif",
                            Font.BOLD,

                            18
                                    + Math.min(
                                    9,
                                    combo / 10
                            )
                    ),

                    withAlpha(
                            TEXT,

                            175
                                    + (int) (
                                    pulse * 70
                            )
                    )
            );

            drawCentered(
                    g,

                    combo >= 25
                            ? "UNREAL RUN"
                            : "FLOW STATE",

                    cx,
                    cy + 17,

                    new Font(
                            "Monospaced",
                            Font.BOLD,
                            8
                    ),

                    withAlpha(
                            ACCENT_SOFT,

                            135
                                    + (int) (
                                    pulse * 75
                            )
                    )
            );
        }

        private void drawProgress(
                Graphics2D g,
                int w,
                int h,
                long now
        ) {
            if (
                    state != GameState.PLAYING
                            && state != GameState.PAUSED
            ) {
                return;
            }

            long runLength =
                    Math.max(
                            1L,
                            roundEnd
                                    - roundStart
                    );

            double progress =
                    Math.max(
                            0.0,

                            Math.min(
                                    1.0,

                                    (
                                            now
                                                    - roundStart
                                    )
                                            / (double) runLength
                            )
                    );

            int cx =
                    w / 2;

            int cy =
                    h - 27;

            int width =
                    Math.min(
                            440,
                            w - 110
                    );

            double left =
                    cx
                            - width
                            / 2.0;

            double filled =
                    width
                            * progress;

            Shape track =
                    new RoundRectangle2D.Double(
                            left,
                            cy,
                            width,
                            4,
                            6,
                            6
                    );

            g.setColor(
                    new Color(
                            177,
                            193,
                            218,
                            35
                    )
            );

            g.fill(
                    track
            );

            if (
                    filled > 0.5
            ) {
                Shape fill =
                        new RoundRectangle2D.Double(
                                left,
                                cy,
                                filled,
                                4,
                                6,
                                6
                        );

                g.setColor(
                        new Color(
                                146,
                                204,
                                255,
                                95
                        )
                );

                g.fill(
                        fill
                );

                g.setColor(
                        new Color(
                                239,
                                246,
                                255,
                                195
                        )
                );

                g.fill(
                        new RoundRectangle2D.Double(
                                left,
                                cy,
                                filled,
                                1.2,
                                4,
                                4
                        )
                );
            }

            for (
                    int i = 1;
                    i < 10;
                    i++
            ) {
                double xx =
                        left
                                + width
                                * i
                                / 10.0;

                g.setColor(
                        new Color(
                                220,
                                228,
                                246,
                                26
                        )
                );

                g.fill(
                        new Ellipse2D.Double(
                                xx - 1.2,
                                cy - 1.2,
                                2.4,
                                6.4
                        )
                );
            }
        }

        private void drawHitFlash(
                Graphics2D g,
                int w,
                int h
        ) {
            if (
                    screenBloomFlash <= 0.015
            ) {
                return;
            }

            int alpha = (int) Math.min(18, screenBloomFlash * 20.0);

            g.setColor(
                    withAlpha(
                            screenFlashColor,
                            alpha
                    )
            );

            g.fillRect(
                    0,
                    0,
                    w,
                    h
            );

            drawSoftOrb(
                    g,
                    w / 2,
                    h / 2,

                    (int) (
                            125
                                    + screenBloomFlash
                                    * 90
                    ),

                    screenFlashColor,

                    (int) Math.min(
                            38,

                            screenBloomFlash
                                    * 19
                    )
            );
        }

        private void drawLegend(
                Graphics2D g,
                int w,
                int h
        ) {
            drawCentered(
                    g,

                    "TAP  ·  HIT THE GLOW          HOLD  ·  KEEP IT DOWN, THEN RELEASE",

                    w / 2,

                    h - 8,

                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            9
                    ),

                    MUTED
            );
        }

        private void drawOverlay(
                Graphics2D g,
                int w,
                int h,
                String title,
                String subtitle
        ) {
            g.setColor(
                    new Color(
                            4,
                            6,
                            15,
                            218
                    )
            );

            g.fillRect(
                    0,
                    0,
                    w,
                    h
            );

            drawSoftOrb(
                    g,
                    w / 2,
                    h / 2,
                    210,

                    new Color(
                            90,
                            130,
                            255
                    ),

                    25
            );

            drawCentered(
                    g,
                    title,
                    w / 2,
                    h / 2 - 8,

                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            36
                    ),

                    TEXT
            );

            drawCentered(
                    g,
                    subtitle,
                    w / 2,
                    h / 2 + 24,

                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            11
                    ),

                    MUTED
            );
        }

        private void drawResults(
                Graphics2D g,
                int w,
                int h
        ) {
            g.setColor(
                    new Color(
                            4,
                            6,
                            15,
                            225
                    )
            );

            g.fillRect(
                    0,
                    0,
                    w,
                    h
            );

            drawSoftOrb(
                    g,
                    w / 2 - 140,
                    h / 2,
                    210,

                    new Color(
                            30,
                            168,
                            255
                    ),

                    40
            );

            drawSoftOrb(
                    g,
                    w / 2 + 155,
                    h / 2 - 20,
                    190,

                    new Color(
                            226,
                            49,
                            175
                    ),

                    35
            );

            double accuracy =
                    judged == 0

                            ? 100.0

                            : successful
                            * 100.0
                            / judged;

            drawCentered(
                    g,

                    "FINISH LINE",

                    w / 2,

                    h / 2 - 111,

                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            34
                    ),

                    TEXT
            );

            drawCentered(
                    g,

                    String.format(
                            "%,d",
                            score
                    ),

                    w / 2,

                    h / 2 - 60,

                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            32
                    ),

                    ACCENT
            );

            drawCentered(
                    g,

                    String.format(
                            "%.1f%% ACCURACY    ·    %d MAX COMBO",
                            accuracy,
                            maxCombo
                    ),

                    w / 2,

                    h / 2 - 22,

                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            12
                    ),

                    TEXT
            );

            drawCentered(
                    g,

                    "PERFECT  "
                            + perfects
                            + "     GREAT  "
                            + greats
                            + "     GOOD  "
                            + goods
                            + "     OK  "
                            + oks
                            + "     MISS  "
                            + misses,

                    w / 2,

                    h / 2 + 15,

                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            10
                    ),

                    MUTED
            );

            drawCentered(
                    g,

                    "ENTER OR PLAY AGAIN",

                    w / 2,

                    h / 2 + 66,

                    new Font(
                            "SansSerif",
                            Font.BOLD,
                            10
                    ),

                    ACCENT_SOFT
            );
        }

        // ---------------- DRAW HELPERS ----------------

        private void drawCentered(
                Graphics2D g,
                String text,
                int centerX,
                int baselineY,
                Font font,
                Color color
        ) {
            g.setFont(
                    font
            );

            g.setColor(
                    color
            );

            FontMetrics fm =
                    g.getFontMetrics();

            g.drawString(
                    text,

                    centerX
                            - fm.stringWidth(
                            text
                    )
                            / 2,

                    baselineY
            );
        }

        private void drawRightAligned(
                Graphics2D g,
                String text,
                int rightX,
                int baselineY,
                Font font,
                Color color
        ) {
            g.setFont(
                    font
            );

            g.setColor(
                    color
            );

            g.drawString(
                    text,

                    rightX
                            - g
                            .getFontMetrics()
                            .stringWidth(
                                    text
                            ),

                    baselineY
            );
        }

        private void drawSoftText(
                Graphics2D g,
                String text,
                int x,
                int y,
                Font font,
                Color color,
                int alpha
        ) {
            g.setFont(
                    font
            );

            g.setColor(
                    withAlpha(
                            color,
                            alpha / 5
                    )
            );

            g.drawString(
                    text,
                    x - 1,
                    y
            );

            g.drawString(
                    text,
                    x + 1,
                    y
            );

            g.setColor(
                    color
            );

            g.drawString(
                    text,
                    x,
                    y
            );
        }
    }

    // ---------------------------- SHARED DRAW HELPERS ----------------------------

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

    private static double lerp(
            double a,
            double b,
            double t
    ) {
        return a
                + (
                b - a
        )
                * t;
    }

    private static double easeOutCubic(
            double t
    ) {
        return 1.0
                - Math.pow(
                1.0 - t,
                3.0
        );
    }

    private static Color withAlpha(
            Color color,
            int alpha
    ) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),

                Math.max(
                        0,

                        Math.min(
                                255,
                                alpha
                        )
                )
        );
    }

    // ---------------------------- MAIN ----------------------------

    public static void main(
            String[] args
    ) {
        SwingUtilities.invokeLater(
                Main::new
        );
    }
}
