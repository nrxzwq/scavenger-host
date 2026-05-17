package meow.binary.scavenger;

import net.minecraft.advancements.AdvancementHolder;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.registries.RegistrarManager;
import it.hurts.shatterbyte.shatterlib.module.config.ConfigManager;
import it.hurts.shatterbyte.shatterlib.module.network.ShatterLibNetwork;
import meow.binary.scavenger.client.Config;
import meow.binary.scavenger.data.RunMode;
import meow.binary.scavenger.data.ScavengerSavedData;
import meow.binary.scavenger.data.modifier.ScavengerModifier;
import meow.binary.scavenger.network.OpenScavengerControlPacket;
import meow.binary.scavenger.network.SyncScavengerDataPacket;
import meow.binary.scavenger.registry.Modifiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.util.Cast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class Scavenger {
    private static final Identifier KILL_DRAGON_ADVANCEMENT = Identifier.withDefaultNamespace("end/kill_dragon");
    private static final Random RANDOM = new Random();
    public static final Config CONFIG = new Config();
    public static final RegistrarManager REGISTRIES = RegistrarManager.get(Scavenger.MOD_ID);
    public static final TemporaryData TEMP_DATA = new TemporaryData();
    public static final String MOD_ID = "scavenger";

    public static final Player.BedSleepingProblem INSOMNIA_PROBLEM = new Player.BedSleepingProblem(Component.translatable("scavenger.insomnia"));

    public static final TagKey<Item> VEGETARIAN_FOOD = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "vegetarian_food"));
    public static final TagKey<Item> UNROLLABLE_BY_DEFAULT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "unrollable_by_default"));

    public static void init() {
        ConfigManager.registerConfig("scavenger", CONFIG);

        TickEvent.PLAYER_POST.register(player -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ServerLevel serverLevel = serverPlayer.level().getServer().overworld();
            ScavengerSavedData data = ScavengerSavedData.get(serverLevel);
            if (data.isEmpty()) {
                return;
            }

            if (data.shouldDisconnectPlayers(serverLevel.getGameTime())) {
                disconnectPlayersAfterWin(serverPlayer.level().getServer(), data);
            }

            if (data.isRunning()) {
                Scavenger.checkWinCondition(serverPlayer, data);

                ScavengerModifier modifier = data.getModifier();
                if (modifier.hasPlayerTick()) {
                    modifier.playerTick(serverPlayer);
                }
            }
        });

        PlayerEvent.PLAYER_JOIN.register(serverPlayer -> {
            ServerLevel serverLevel = serverPlayer.level().getServer().overworld();
            ScavengerSavedData data = ScavengerSavedData.get(serverLevel);
            NetworkManager.sendToPlayer(serverPlayer, createSyncPacket(data, false));

            if (canManage(serverPlayer) && !data.isRunning()) {
                openControlScreen(serverPlayer);
            }
        });

        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
            ScavengerSavedData data = ScavengerSavedData.get(level.getServer().overworld());
            if (!data.isRunning()) {
                return;
            }

            ScavengerModifier modifier = data.getModifier();

            if (modifier.hasWorldStart()) {
                modifier.onWorldStart(level);
            }
        });

        ShatterLibNetwork.registerS2CPayloadType(SyncScavengerDataPacket.TYPE, SyncScavengerDataPacket.STREAM_CODEC);
        ShatterLibNetwork.registerS2CPayloadType(OpenScavengerControlPacket.TYPE, OpenScavengerControlPacket.STREAM_CODEC);
    }

    private static void checkWinCondition(ServerPlayer player, ScavengerSavedData data) {
        int itemCount = getItemCount(data.getModifierId());

        boolean hasItem = getRunItemCount(player, data) >= itemCount;
        boolean hasWon = hasItem && (!data.getModifierId().equals(Modifiers.FINALIST.getId()) || hasKilledDragon(player));
        if (hasWon) {
            MinecraftServer server = player.level().getServer();
            ServerLevel serverLevel = server.overworld();
            String winnerName = getWinnerName(player, data.getMode());
            data.win(serverLevel.getGameTime(), winnerName);
            if (data.getMode() == RunMode.SOLO) {
                data.scheduleDisconnectAfterWin(serverLevel.getGameTime() + 100L);
            }
            syncToAll(server, data, true);
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(
                            "scavenger.chat.won",
                            winnerName,
                            data.getItem().getDefaultInstance().getHoverName(),
                            formatTicks(Math.max(0L, data.getWinTimestamp() - data.getStartTimestamp()), serverLevel)
                    ),
                    false
            );
        }
    }

    private static boolean hasKilledDragon(ServerPlayer player) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(KILL_DRAGON_ADVANCEMENT);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    public static boolean canManage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null && server.isSingleplayerOwner(player.nameAndId())) {
            return true;
        }

        return player.permissions() instanceof LevelBasedPermissionSet permissionSet
                && permissionSet.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
    }

    public static void openControlScreen(ServerPlayer player) {
        NetworkManager.sendToPlayer(player, new OpenScavengerControlPacket());
    }

    public static boolean startRun(ServerPlayer starter, Item item, Identifier modifierId, RunMode mode) {
        if (item == null || item == Items.AIR) {
            return false;
        }

        MinecraftServer server = starter.level().getServer();
        ServerLevel serverLevel = server.overworld();
        ScavengerSavedData data = ScavengerSavedData.get(serverLevel);
        Identifier normalizedModifier = Modifiers.getIds().contains(modifierId) ? modifierId : Modifiers.NONE.getId();
        RunMode normalizedMode = mode == null ? RunMode.SOLO : mode;
        if (isWorldGenerationModifier(normalizedModifier)) {
            starter.sendSystemMessage(Component.translatable(
                    "scavenger.command.world_modifier_requires_recreate",
                    Modifiers.getName(normalizedModifier)
            ));
            return false;
        }

        data.prepare(item, normalizedModifier, normalizedMode);
        syncToAll(server, data, false);
        starter.sendSystemMessage(
                Component.translatable(
                        "scavenger.chat.prepared",
                        item.getDefaultInstance().getHoverName(),
                        Modifiers.getName(normalizedModifier),
                        normalizedMode.getName()
                )
        );
        starter.sendSystemMessage(Component.translatable("scavenger.chat.startgame_hint"));
        return true;
    }

    public static boolean startPreparedGame(ServerPlayer starter) {
        MinecraftServer server = starter.level().getServer();
        ServerLevel serverLevel = server.overworld();
        ScavengerSavedData data = ScavengerSavedData.get(serverLevel);
        if (data.isRunning()) {
            starter.sendSystemMessage(Component.translatable("scavenger.command.already_running"));
            return false;
        }

        if (!data.isPrepared()) {
            starter.sendSystemMessage(Component.translatable("scavenger.command.no_prepared_run"));
            return false;
        }

        RunMode normalizedMode = data.getMode();
        Optional<String> oversizedTeam = data.findOversizedTeam(normalizedMode);
        if (oversizedTeam.isPresent()) {
            starter.sendSystemMessage(Component.translatable(
                    "scavenger.command.team_too_big",
                    oversizedTeam.get(),
                    normalizedMode.getName(),
                    normalizedMode.getTeamSize()
            ));
            return false;
        }

        data.begin(serverLevel.getGameTime());
        ScavengerModifier modifier = data.getModifier();
        if (modifier.hasWorldStart()) {
            for (ServerLevel level : server.getAllLevels()) {
                modifier.onWorldStart(level);
            }
        }

        syncToAll(server, data, false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "scavenger.chat.started",
                        data.getDisplayName(starter.getName().getString()),
                        data.getItem().getDefaultInstance().getHoverName(),
                        Modifiers.getName(data.getModifierId()),
                        normalizedMode.getName()
                ),
                false
        );

        if (normalizedMode.isTeamMode()) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("scavenger.chat.team_hint", normalizedMode.getTeamSize()), false);
        }

        return true;
    }

    public static void clearRun(MinecraftServer server) {
        ScavengerSavedData data = ScavengerSavedData.get(server.overworld());
        data.clear();
        syncToAll(server, data, false);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("scavenger.chat.cleared"), false);
    }

    public static Item getRandomRollableItem() {
        List<Item> items = getRollableItems();
        if (items.isEmpty()) {
            return Items.AIR;
        }

        return items.get(RANDOM.nextInt(items.size()));
    }

    public static Identifier getRandomRollableModifier(Item item) {
        List<Identifier> modifiers = getRollableModifierIds(item);
        if (modifiers.isEmpty()) {
            return Modifiers.NONE.getId();
        }

        return modifiers.get(RANDOM.nextInt(modifiers.size()));
    }

    public static List<Item> getRollableItems() {
        Set<String> configuredItems = CONFIG.gameplay.rollableItems == null ? Set.of() : CONFIG.gameplay.rollableItems.stream()
                .filter(itemId -> itemId != null && !itemId.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());

        List<Item> allItems = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(item -> !isDefaultExcludedItem(item))
                .filter(item -> {
                    if (configuredItems.isEmpty()) {
                        return true;
                    }

                    boolean isConfigured = configuredItems.contains(item.arch$registryName().toString());
                    return CONFIG.gameplay.rollableItemsIsBlacklist != isConfigured;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        if (!allItems.isEmpty()) {
            return allItems;
        }

        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(item -> !isDefaultExcludedItem(item))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<Identifier> getRollableModifierIds(Item item) {
        return getRollableModifierIds(item, false);
    }

    public static List<Identifier> getRollableModifierIds(Item item, boolean includeWorldGenerationModifiers) {
        List<Identifier> filteredModifiers = new ArrayList<>(Modifiers.getIds().stream()
                .filter(id -> !CONFIG.gameplay.modifierBlacklist.contains(id.toString()))
                .filter(id -> includeWorldGenerationModifiers || !isWorldGenerationModifier(id))
                .toList());

        if (item == Items.DRAGON_EGG) {
            filteredModifiers.remove(Modifiers.TWICE.getId());
            filteredModifiers.remove(Modifiers.THRICE.getId());
        }

        if (filteredModifiers.isEmpty()) {
            return Collections.singletonList(Modifiers.NONE.getId());
        }

        return filteredModifiers;
    }

    public static boolean isWorldGenerationModifier(Identifier modifier) {
        return modifier != null && (
                modifier.equals(Modifiers.DEJAVU.getId())
                        || modifier.equals(Modifiers.LARGE_BIOMES.getId())
                        || modifier.equals(Modifiers.AMPLIFIED.getId())
        );
    }

    private static boolean isDefaultExcludedItem(Item item) {
        return item.getDefaultInstance().is(Scavenger.UNROLLABLE_BY_DEFAULT);
    }

    private static SyncScavengerDataPacket createSyncPacket(ScavengerSavedData data, boolean isWin) {
        return new SyncScavengerDataPacket(
                data.getItem(),
                data.getModifierId(),
                data.getStartTimestamp(),
                data.getWinTimestamp(),
                data.getMode(),
                data.getWinnerName(),
                isWin
        );
    }

    private static void syncToAll(MinecraftServer server, ScavengerSavedData data, boolean isWin) {
        SyncScavengerDataPacket packet = createSyncPacket(data, isWin);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, packet);
        }
    }

    private static void disconnectPlayersAfterWin(MinecraftServer server, ScavengerSavedData data) {
        data.markPlayersDisconnectedAfterWin();
        Component reason = Component.translatable("scavenger.disconnect.win", data.getWinnerName());
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            player.connection.disconnect(reason);
        }
    }

    private static int getRunItemCount(ServerPlayer player, ScavengerSavedData data) {
        if (!data.getMode().isTeamMode()) {
            return player.getInventory().countItem(data.getItem());
        }

        Optional<String> teamLeader = data.getTeamLeaderFor(player.getName().getString());
        if (teamLeader.isEmpty()) {
            return player.getInventory().countItem(data.getItem());
        }

        int count = 0;
        for (String memberName : data.getTeamMembers(teamLeader.get())) {
            ServerPlayer onlinePlayer = player.level().getServer().getPlayerList().getPlayerByName(memberName);
            if (onlinePlayer != null) {
                count += onlinePlayer.getInventory().countItem(data.getItem());
            }
        }

        return count;
    }

    private static String getWinnerName(ServerPlayer player, RunMode mode) {
        if (mode != null && mode.isTeamMode()) {
            ScavengerSavedData data = ScavengerSavedData.get(player.level().getServer().overworld());
            Optional<String> teamLeader = data.getTeamLeaderFor(player.getName().getString());
            if (teamLeader.isPresent()) {
                return "Team " + data.getDisplayName(teamLeader.get());
            }
        }

        ScavengerSavedData data = ScavengerSavedData.get(player.level().getServer().overworld());
        return data.getDisplayName(player.getName().getString());
    }

    private static String formatTicks(long ticks, ServerLevel level) {
        float tickrate = level.tickRateManager().tickrate();
        double totalSeconds = ticks / tickrate;
        int hours = (int)(totalSeconds / 3600);
        int minutes = (int)((totalSeconds % 3600) / 60);
        int seconds = (int)(totalSeconds % 60);
        int millis = (int)((totalSeconds - Math.floor(totalSeconds)) * 100);

        return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, millis);
    }

    public static int getItemCount(Identifier modifier) {
        int itemCount = 1;
        if (modifier.equals(Modifiers.TWICE.getId())) itemCount = 2;
        else if (modifier.equals(Modifiers.THRICE.getId())) itemCount = 3;

        return itemCount;
    }

    public static boolean isSlotBlocked(int index, Level level) {
        if (Modifiers.isActive(Modifiers.HOLEY_POCKETS, level) && index > 8 && index < 36) {
            return true;
        }

//        if (Modifiers.isActive(Modifiers.BRITTLE_BONES, level) && index >= 36 && index < 40) {
//            return true;
//        }

//        if (Modifiers.isActive(Modifiers.ONE_ARM, level) && index == 40) {
//            return true;
//        }

        return false;
    }

    public static void saveConfig() {
        Object object = CONFIG.prepareData();
        CONFIG.getLoader().saveToFiles("scavenger", Cast.cast(object), ConfigManager.BASE_PROVIDER);
    }
}
