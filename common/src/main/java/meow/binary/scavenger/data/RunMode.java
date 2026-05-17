package meow.binary.scavenger.data;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

public enum RunMode {
    SOLO("solo"),
    DUO("duo"),
    TRIO("trio");

    private final String id;

    RunMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Component getName() {
        return Component.translatable("scavenger.mode." + id);
    }

    public int getTeamSize() {
        return switch (this) {
            case SOLO -> 1;
            case DUO -> 2;
            case TRIO -> 3;
        };
    }

    public boolean isTeamMode() {
        return this != SOLO;
    }

    public static RunMode byId(String id) {
        return tryById(id).orElse(SOLO);
    }

    public static Optional<RunMode> tryById(String id) {
        if (id == null) {
            return Optional.empty();
        }

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (RunMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return Optional.of(mode);
            }
        }

        return Optional.empty();
    }
}
