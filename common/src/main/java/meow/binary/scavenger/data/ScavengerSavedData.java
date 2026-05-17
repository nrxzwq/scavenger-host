package meow.binary.scavenger.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import meow.binary.scavenger.Scavenger;
import meow.binary.scavenger.data.modifier.ScavengerModifier;
import meow.binary.scavenger.registry.Modifiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ScavengerSavedData extends SavedData {
    private static final String DATA_NAME = "scavenger_data";
    public static final Codec<ScavengerSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("itemId").forGetter(ScavengerSavedData::getItemId),
                    Identifier.CODEC.fieldOf("modifier").forGetter(ScavengerSavedData::getModifierId),
                    Codec.BOOL.optionalFieldOf("hasWon", false).forGetter(ScavengerSavedData::hasWon),
                    Codec.LONG.optionalFieldOf("startTimestamp", 0L).forGetter(ScavengerSavedData::getStartTimestamp),
                    Codec.LONG.optionalFieldOf("winTimestamp", 0L).forGetter(ScavengerSavedData::getWinTimestamp),
                    Codec.STRING.optionalFieldOf("mode", RunMode.SOLO.getId()).forGetter(data -> data.getMode().getId()),
                    Codec.STRING.optionalFieldOf("winnerName", "").forGetter(ScavengerSavedData::getWinnerName),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("teams", Map.of()).forGetter(ScavengerSavedData::getTeams),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("nicknames", Map.of()).forGetter(ScavengerSavedData::getNicknames),
                    Codec.LONG.optionalFieldOf("disconnectTimestamp", 0L).forGetter(ScavengerSavedData::getDisconnectTimestamp),
                    Codec.BOOL.optionalFieldOf("playersDisconnectedAfterWin", false).forGetter(ScavengerSavedData::hasDisconnectedPlayersAfterWin)
            ).apply(instance, ScavengerSavedData::new));

    public static final SavedDataType<ScavengerSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            ScavengerSavedData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public Identifier getItemId() {
        return itemId;
    }
    public Item getItem() {
        return BuiltInRegistries.ITEM.getValue(itemId);
    }

    public void setItem(Item item) {
        this.itemId = item.arch$registryName();

        this.setDirty();
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
        this.setDirty();
    }

    public long getWinTimestamp() {
        return winTimestamp;
    }

    public void setWinTimestamp(long winTimestamp) {
        this.winTimestamp = winTimestamp;
        this.setDirty();
    }

    public Identifier getModifierId() {
        return modifierId;
    }

    public ScavengerModifier getModifier() {
        return Modifiers.get(modifierId);
    }

    public void setModifierId(Identifier modifierId) {
        this.modifierId = modifierId;

        this.setDirty();
    }

    public boolean hasWon() {
        return hasWon;
    }

    public void win(long winTimestamp, String winnerName) {
        this.hasWon = true;
        this.winnerName = winnerName == null ? "" : winnerName;
        this.setWinTimestamp(winTimestamp);
        this.setDirty();
    }

    public RunMode getMode() {
        return mode;
    }

    public void setMode(RunMode mode) {
        this.mode = mode == null ? RunMode.SOLO : mode;
        this.setDirty();
    }

    public String getWinnerName() {
        return winnerName == null ? "" : winnerName;
    }

    public Map<String, String> getNicknames() {
        return copyNicknames(this.nicknames);
    }

    public String getDisplayName(String playerName) {
        String normalizedPlayer = normalizeName(playerName);
        for (Map.Entry<String, String> entry : nicknames.entrySet()) {
            if (normalizeName(entry.getKey()).equals(normalizedPlayer)) {
                return entry.getValue();
            }
        }

        return sanitizeName(playerName);
    }

    public void setNickname(String playerName, String nickname) {
        String player = sanitizeName(playerName);
        String displayName = sanitizeNickname(nickname);
        if (player.isBlank() || displayName.isBlank()) {
            return;
        }

        removeNickname(player);
        nicknames.put(player, displayName);
        setDirty();
    }

    public boolean removeNickname(String playerName) {
        String normalizedPlayer = normalizeName(playerName);
        List<String> keysToRemove = nicknames.keySet().stream()
                .filter(key -> normalizeName(key).equals(normalizedPlayer))
                .toList();
        keysToRemove.forEach(nicknames::remove);
        if (!keysToRemove.isEmpty()) {
            setDirty();
            return true;
        }

        return false;
    }

    public long getDisconnectTimestamp() {
        return disconnectTimestamp;
    }

    public boolean hasDisconnectedPlayersAfterWin() {
        return playersDisconnectedAfterWin;
    }

    public void scheduleDisconnectAfterWin(long timestamp) {
        this.disconnectTimestamp = timestamp;
        this.playersDisconnectedAfterWin = false;
        setDirty();
    }

    public boolean shouldDisconnectPlayers(long gameTime) {
        return hasWon && disconnectTimestamp > 0L && !playersDisconnectedAfterWin && gameTime >= disconnectTimestamp;
    }

    public void markPlayersDisconnectedAfterWin() {
        this.playersDisconnectedAfterWin = true;
        setDirty();
    }

    public Map<String, List<String>> getTeams() {
        return copyTeams(this.teams);
    }

    public List<String> getTeamMembers(String leaderName) {
        String leader = findLeader(leaderName).orElse(sanitizeName(leaderName));
        return new ArrayList<>(teams.getOrDefault(leader, List.of()));
    }

    public Optional<String> getTeamLeaderFor(String playerName) {
        String normalizedPlayer = normalizeName(playerName);
        if (normalizedPlayer.isBlank()) {
            return Optional.empty();
        }

        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            if (normalizeName(entry.getKey()).equals(normalizedPlayer)) {
                return Optional.of(entry.getKey());
            }

            for (String member : entry.getValue()) {
                if (normalizeName(member).equals(normalizedPlayer)) {
                    return Optional.of(entry.getKey());
                }
            }
        }

        return Optional.empty();
    }

    public void addTeamMember(String leaderName, String playerName) {
        String leader = sanitizeName(leaderName);
        String player = sanitizeName(playerName);
        if (leader.isBlank() || player.isBlank()) {
            return;
        }

        Optional<String> existingLeader = findLeader(leader);
        if (existingLeader.isPresent()) {
            leader = existingLeader.get();
        }

        removePlayerFromTeams(player);
        List<String> members = new ArrayList<>(teams.getOrDefault(leader, List.of()));
        String normalizedLeader = normalizeName(leader);
        if (members.stream().noneMatch(member -> normalizeName(member).equals(normalizedLeader))) {
            members.add(leader);
        }
        if (members.stream().noneMatch(member -> normalizeName(member).equals(normalizeName(player)))) {
            members.add(player);
        }
        teams.put(leader, members);
        removeEmptyTeams();
        setDirty();
    }

    public boolean removeTeamMember(String leaderName, String playerName) {
        Optional<String> foundLeader = findLeader(leaderName);
        if (foundLeader.isEmpty()) {
            return false;
        }

        String leader = foundLeader.get();
        String normalizedPlayer = normalizeName(playerName);
        if (normalizeName(leader).equals(normalizedPlayer)) {
            teams.remove(leader);
            setDirty();
            return true;
        }

        List<String> members = new ArrayList<>(teams.getOrDefault(leader, List.of()));
        boolean removed = members.removeIf(member -> normalizeName(member).equals(normalizedPlayer));
        if (!removed) {
            return false;
        }

        teams.put(leader, members);
        removeEmptyTeams();
        setDirty();
        return true;
    }

    public boolean removePlayerFromTeams(String playerName) {
        String normalizedPlayer = normalizeName(playerName);
        boolean removed = false;

        List<String> leadersToRemove = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            if (normalizeName(entry.getKey()).equals(normalizedPlayer)) {
                leadersToRemove.add(entry.getKey());
                removed = true;
                continue;
            }

            List<String> members = new ArrayList<>(entry.getValue());
            boolean memberRemoved = members.removeIf(member -> normalizeName(member).equals(normalizedPlayer));
            if (memberRemoved) {
                teams.put(entry.getKey(), members);
                removed = true;
            }
        }

        leadersToRemove.forEach(teams::remove);
        if (removed) {
            removeEmptyTeams();
            setDirty();
        }

        return removed;
    }

    public boolean removeTeam(String leaderName) {
        Optional<String> leader = findLeader(leaderName);
        if (leader.isEmpty()) {
            return false;
        }

        teams.remove(leader.get());
        setDirty();
        return true;
    }

    public void clearTeams() {
        teams.clear();
        setDirty();
    }

    public Optional<String> findOversizedTeam(RunMode mode) {
        if (mode == null || !mode.isTeamMode()) {
            return Optional.empty();
        }

        int maxSize = mode.getTeamSize();
        for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
            if (entry.getValue().size() > maxSize) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }

    private Optional<String> findLeader(String leaderName) {
        String normalizedLeader = normalizeName(leaderName);
        return teams.keySet().stream()
                .filter(leader -> normalizeName(leader).equals(normalizedLeader))
                .findFirst();
    }

    private static Map<String, List<String>> copyTeams(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((leader, members) -> copy.put(leader, new ArrayList<>(members)));
        return copy;
    }

    private static Map<String, String> copyNicknames(Map<String, String> source) {
        return new LinkedHashMap<>(source);
    }

    private void removeEmptyTeams() {
        teams.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static String sanitizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private static String sanitizeNickname(String nickname) {
        String sanitized = sanitizeName(nickname);
        if (sanitized.length() > 32) {
            return sanitized.substring(0, 32);
        }

        return sanitized;
    }

    private static String normalizeName(String name) {
        return sanitizeName(name).toLowerCase(Locale.ROOT);
    }

    public void prepare(Item item, Identifier modifierId, RunMode mode) {
        this.itemId = item.arch$registryName();
        this.modifierId = modifierId;
        this.mode = mode == null ? RunMode.SOLO : mode;
        this.startTimestamp = 0L;
        this.winTimestamp = 0L;
        this.winnerName = "";
        this.hasWon = false;
        this.disconnectTimestamp = 0L;
        this.playersDisconnectedAfterWin = false;

        this.setDirty();
    }

    public void begin(long startTimestamp) {
        this.startTimestamp = startTimestamp;
        this.winTimestamp = 0L;
        this.winnerName = "";
        this.hasWon = false;
        this.disconnectTimestamp = 0L;
        this.playersDisconnectedAfterWin = false;

        this.setDirty();
    }

    public void clear() {
        this.itemId = Items.AIR.arch$registryName();
        this.modifierId = Modifiers.NONE.getId();
        this.mode = RunMode.SOLO;
        this.startTimestamp = 0L;
        this.winTimestamp = 0L;
        this.winnerName = "";
        this.hasWon = false;
        this.disconnectTimestamp = 0L;
        this.playersDisconnectedAfterWin = false;

        this.setDirty();
    }

    public boolean isPrepared() {
        return !this.isEmpty() && !this.hasWon && this.startTimestamp <= 0L;
    }

    public boolean isRunning() {
        return !this.isEmpty() && !this.hasWon && this.startTimestamp > 0L;
    }

    private Identifier itemId;
    private Identifier modifierId;
    private boolean hasWon;
    private long startTimestamp;
    private long winTimestamp;
    private RunMode mode;
    private String winnerName;
    private Map<String, List<String>> teams;
    private Map<String, String> nicknames;
    private long disconnectTimestamp;
    private boolean playersDisconnectedAfterWin;

    private ScavengerSavedData(Identifier itemId, Identifier modifierId, boolean hasWon, long startTimestamp, long winTimestamp, String mode, String winnerName, Map<String, List<String>> teams, Map<String, String> nicknames, long disconnectTimestamp, boolean playersDisconnectedAfterWin) {
        this.itemId = itemId;
        this.modifierId = modifierId;
        this.hasWon = hasWon;
        this.startTimestamp = startTimestamp;
        this.winTimestamp = winTimestamp;
        this.mode = RunMode.byId(mode);
        this.winnerName = winnerName == null ? "" : winnerName;
        this.teams = copyTeams(teams);
        this.nicknames = copyNicknames(nicknames);
        this.disconnectTimestamp = disconnectTimestamp;
        this.playersDisconnectedAfterWin = playersDisconnectedAfterWin;

        this.setDirty();
    }

    public ScavengerSavedData() {
        this.itemId = Scavenger.TEMP_DATA.item.arch$registryName();
        this.modifierId = Scavenger.TEMP_DATA.modifier;
        this.hasWon = false;
        this.startTimestamp = 0L;
        this.winTimestamp = 0L;
        this.mode = RunMode.SOLO;
        this.winnerName = "";
        this.teams = new LinkedHashMap<>();
        this.nicknames = new LinkedHashMap<>();
        this.disconnectTimestamp = 0L;
        this.playersDisconnectedAfterWin = false;

        Scavenger.TEMP_DATA.item = Items.AIR;
        Scavenger.TEMP_DATA.modifier = Modifiers.NONE.getId();

        this.setDirty();
    }

    public static ScavengerSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isEmpty() {
        return this.modifierId.equals(Modifiers.NONE.getId()) && itemId.equals(Items.AIR.arch$registryName());
    }
}
