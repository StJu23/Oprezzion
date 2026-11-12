package org.example;

/**
 * One note from an Oprezzion chart.
 * Times are stored in nanoseconds so Main can compare them directly
 * against the audio clock.
 */
public record ChartNote(
        long targetTimeNanos,
        int lane,
        Type type,
        long durationNanos
) {
    public enum Type {
        TAP,
        HOLD
    }

    public ChartNote {
        if (targetTimeNanos < 0L) {
            throw new IllegalArgumentException("targetTimeNanos must be >= 0");
        }

        if (lane < 0 || lane > 2) {
            throw new IllegalArgumentException("lane must be 0, 1, or 2");
        }

        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }

        if (durationNanos < 0L) {
            throw new IllegalArgumentException("durationNanos must be >= 0");
        }

        if (type == Type.TAP && durationNanos != 0L) {
            throw new IllegalArgumentException("TAP notes must have duration 0");
        }

        if (type == Type.HOLD && durationNanos <= 0L) {
            throw new IllegalArgumentException("HOLD notes must have a duration greater than 0");
        }
    }

    public boolean isHold() {
        return type == Type.HOLD;
    }

    public long endTimeNanos() {
        return targetTimeNanos + durationNanos;
    }
}