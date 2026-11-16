package pe.kydium.eclipserpg.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Progreso RPG de un jugador. */
public final class PlayerData {
    public final UUID uuid;
    public int level = 1;
    public long xp = 0;
    public int points = 0;
    public long soles = 0;
    public final Map<String, Integer> stats = new HashMap<>();

    // Contratos diarios
    public String contractDate = "";
    public int contractTier = 1;
    public final List<String> contractIds = new ArrayList<>();
    public int[] contractProgress = new int[3];
    public boolean[] contractClaimed = new boolean[3];

    // Premios únicos ya entregados (armas de jefes, etc.)
    public final Set<String> rewards = new HashSet<>();

    public boolean dirty = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public int stat(String id) {
        return stats.getOrDefault(id, 0);
    }
}
