package com.doze.timebound;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Particle;

import java.util.Locale;

public enum ClockType {
    FREEZE("freeze", "Freeze Clock", TextColor.fromHexString("#73D9FF"), Particle.SNOWFLAKE),
    BRAKE("brake", "Brake Clock", TextColor.fromHexString("#BDBDBD"), Particle.ASH),
    SKIP("skip", "Skip Clock", TextColor.fromHexString("#FFD95E"), Particle.ELECTRIC_SPARK),
    REVERSE("reverse", "Reverse Clock", TextColor.fromHexString("#D58DFF"), Particle.REVERSE_PORTAL);

    private final String key;
    private final String displayName;
    private final TextColor color;
    private final Particle ambientParticle;

    ClockType(String key, String displayName, TextColor color, Particle ambientParticle) {
        this.key = key;
        this.displayName = displayName;
        this.color = color;
        this.ambientParticle = ambientParticle;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public TextColor color() {
        return color;
    }

    public Particle ambientParticle() {
        return ambientParticle;
    }

    public static ClockType fromKey(String key) {
        if (key == null) return null;
        String normalized = key.toLowerCase(Locale.ROOT);
        for (ClockType value : values()) {
            if (value.key.equals(normalized)) return value;
        }
        return null;
    }
}
