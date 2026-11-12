package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Saved visual accessibility preferences for OPREZZION.
 * Reduced is the first-launch default. No preset guarantees seizure safety.
 */
public final class VisualSettings {
    public enum Preset { REDUCED, MINIMAL, FULL, CUSTOM }

    public record Config(
            Preset preset,
            int intensity,
            int particles,
            int bloom,
            boolean flashes,
            boolean cameraShake,
            boolean backgroundMotion,
            boolean beatPulsing,
            boolean photosensitivityPrecautions
    ) {
        public Config {
            intensity = cap(intensity);
            particles = cap(particles);
            bloom = cap(bloom);
            if (preset == null) preset = Preset.REDUCED;
        }

        public static Config reduced() {
            return new Config(Preset.REDUCED, 35, 30, 20,
                    false, false, true, false, false);
        }

        public static Config minimal() {
            return new Config(Preset.MINIMAL, 0, 0, 0,
                    false, false, false, false, true);
        }

        public static Config full() {
            return new Config(Preset.FULL, 100, 100, 100,
                    true, true, true, true, false);
        }

        public boolean restrained() {
            return photosensitivityPrecautions;
        }

        public int effectiveIntensity() {
            return restrained() ? 0 : intensity;
        }

        public int effectiveParticles() {
            return restrained() ? 0 : (particles * intensity / 100);
        }

        public int effectiveBloom() {
            return restrained() ? 0 : (bloom * intensity / 100);
        }

        public boolean allowFlashes() {
            return !restrained() && flashes && intensity > 0;
        }

        public boolean allowShake() {
            return !restrained() && cameraShake && intensity > 0;
        }

        public boolean allowMotion() {
            return !restrained() && backgroundMotion && intensity > 0;
        }

        public boolean allowBeatPulses() {
            return !restrained() && beatPulsing && intensity > 0;
        }

        private static int cap(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }

    private final Preferences preferences =
            Preferences.userNodeForPackage(VisualSettings.class).node("visual-v1");

    private volatile Config current;

    public VisualSettings() {
        current = load();
    }

    public Config get() {
        return current;
    }

    public void update(Config config) {
        if (config == null) return;
        current = config;
        try {
            preferences.put("preset", config.preset().name());
            preferences.putInt("intensity", config.intensity());
            preferences.putInt("particles", config.particles());
            preferences.putInt("bloom", config.bloom());
            preferences.putBoolean("flashes", config.flashes());
            preferences.putBoolean("shake", config.cameraShake());
            preferences.putBoolean("motion", config.backgroundMotion());
            preferences.putBoolean("beat", config.beatPulsing());
            preferences.putBoolean("precautions", config.photosensitivityPrecautions());
            preferences.flush();
        } catch (Exception exception) {
            System.err.println("OPREZZION: Couldn't save visual preferences: "
                    + exception.getMessage());
        }
    }

    private Config load() {
        try {
            Preset preset;
            try {
                preset = Preset.valueOf(preferences.get("preset", "REDUCED"));
            } catch (IllegalArgumentException exception) {
                preset = Preset.REDUCED;
            }
            Config fallback = switch (preset) {
                case MINIMAL -> Config.minimal();
                case FULL -> Config.full();
                case REDUCED, CUSTOM -> Config.reduced();
            };
            return new Config(preset,
                    preferences.getInt("intensity", fallback.intensity()),
                    preferences.getInt("particles", fallback.particles()),
                    preferences.getInt("bloom", fallback.bloom()),
                    preferences.getBoolean("flashes", fallback.flashes()),
                    preferences.getBoolean("shake", fallback.cameraShake()),
                    preferences.getBoolean("motion", fallback.backgroundMotion()),
                    preferences.getBoolean("beat", fallback.beatPulsing()),
                    preferences.getBoolean("precautions", fallback.photosensitivityPrecautions()));
        } catch (Exception exception) {
            return Config.reduced();
        }
    }

    /**
     * Opens a modal settings dialog. Applies settings on Save only.
     * Does not turn photosensitivity precautions off without user action.
     */
    public void showDialog(Window owner, Runnable afterApply) {
        Config saved = get();

        JComboBox<Preset> preset = new JComboBox<>(Preset.values());
        preset.setSelectedItem(saved.preset());

        JSlider intensity = slider(saved.intensity());
        JSlider particles = slider(saved.particles());
        JSlider bloom = slider(saved.bloom());

        JCheckBox flashes = new JCheckBox("Allow brief screen flashes", saved.flashes());
        JCheckBox shake = new JCheckBox("Camera shake", saved.cameraShake());
        JCheckBox motion = new JCheckBox("Animated background", saved.backgroundMotion());
        JCheckBox beat = new JCheckBox("Beat-reactive brightness", saved.beatPulsing());
        JCheckBox precautions = new JCheckBox(
                "Photosensitivity precautions (static background, no flashes, no shake, no decorative animation)",
                saved.photosensitivityPrecautions());

        JCheckBox[] boxes = {flashes, shake, motion, beat, precautions};
        for (JCheckBox check : boxes) check.setOpaque(false);

        boolean[] applyingPreset = {false};
        Runnable markCustom = () -> {
            if (!applyingPreset[0]) preset.setSelectedItem(Preset.CUSTOM);
        };

        preset.addActionListener(e -> {
            Preset choice = (Preset) preset.getSelectedItem();
            if (choice == null || choice == Preset.CUSTOM) return;
            Config selected = switch (choice) {
                case FULL -> Config.full();
                case MINIMAL -> Config.minimal();
                case REDUCED -> Config.reduced();
                case CUSTOM -> saved;
            };
            applyingPreset[0] = true;
            intensity.setValue(selected.intensity());
            particles.setValue(selected.particles());
            bloom.setValue(selected.bloom());
            flashes.setSelected(selected.flashes());
            shake.setSelected(selected.cameraShake());
            motion.setSelected(selected.backgroundMotion());
            beat.setSelected(selected.beatPulsing());
            // A previously enabled safety preference never turns off just
            // because the player changes the visual preset.
            precautions.setSelected(
                    precautions.isSelected() || selected.photosensitivityPrecautions());
            applyingPreset[0] = false;
        });

        intensity.addChangeListener(e -> markCustom.run());
        particles.addChangeListener(e -> markCustom.run());
        bloom.addChangeListener(e -> markCustom.run());
        for (JCheckBox check : boxes) check.addActionListener(e -> markCustom.run());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel heading = new JLabel("Visual accessibility");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        content.add(heading);
        content.add(Box.createVerticalStrut(8));
        content.add(new JLabel("These controls never change timing, chart data, or scoring."));
        content.add(Box.createVerticalStrut(18));
        content.add(labeled("Preset", preset));
        content.add(Box.createVerticalStrut(8));
        content.add(labeled("Overall effects", intensity));
        content.add(labeled("Particle density", particles));
        content.add(labeled("Bloom/glow", bloom));
        content.add(Box.createVerticalStrut(8));
        for (JCheckBox check : boxes) {
            content.add(check);
            content.add(Box.createVerticalStrut(5));
        }
        content.add(Box.createVerticalStrut(10));
        JTextArea warning = new JTextArea(
                "Precautions reduce visual triggers but cannot guarantee seizure safety. "
                        + "Full effects can include brightness changes. For maximum reduction, "
                        + "enable precautions or choose Minimal.");
        warning.setEditable(false);
        warning.setFocusable(false);
        warning.setOpaque(false);
        warning.setLineWrap(true);
        warning.setWrapStyleWord(true);
        warning.setRows(3);
        warning.setFont(warning.getFont().deriveFont(11f));
        content.add(warning);

        int choice = JOptionPane.showConfirmDialog(owner, content,
                "OPREZZION · Visual Settings", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;

        update(new Config(
                (Preset) preset.getSelectedItem(),
                intensity.getValue(), particles.getValue(), bloom.getValue(),
                flashes.isSelected(), shake.isSelected(), motion.isSelected(),
                beat.isSelected(), precautions.isSelected()));
        if (afterApply != null) afterApply.run();
    }

    private static JSlider slider(int value) {
        JSlider result = new JSlider(0, 100, value);
        result.setMajorTickSpacing(25);
        result.setPaintTicks(true);
        result.setPaintLabels(true);
        return result;
    }

    private static JPanel labeled(String text, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(text);
        label.setPreferredSize(new Dimension(125, 28));
        panel.add(label, BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }
}