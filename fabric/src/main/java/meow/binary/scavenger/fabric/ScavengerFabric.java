package meow.binary.scavenger.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meow.binary.scavenger.Scavenger;
import meow.binary.scavenger.data.RunMode;
import meow.binary.scavenger.data.ScavengerSavedData;
import meow.binary.scavenger.registry.Modifiers;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class ScavengerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Scavenger.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("scavenger")
                .executes(ScavengerFabric::showHelp)
                .then(Commands.literal("help")
                        .executes(ScavengerFabric::showHelp))
                .then(registerNickCommand())
                .then(Commands.literal("menu")
                        .requires(ScavengerFabric::canUse)
                        .executes(ScavengerFabric::openMenu))
                .then(Commands.literal("clear")
                        .requires(ScavengerFabric::canUse)
                        .executes(context -> {
                            Scavenger.clearRun(context.getSource().getServer());
                            return 1;
                        }))
                .then(Commands.literal("stop")
                        .requires(ScavengerFabric::canUse)
                        .executes(context -> {
                            Scavenger.clearRun(context.getSource().getServer());
                            return 1;
                        }))
                .then(Commands.literal("random")
                        .requires(ScavengerFabric::canUse)
                        .executes(context -> startRandom(context, RunMode.SOLO))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ScavengerFabric::startRandomWithMode)))
                .then(Commands.literal("start")
                        .requires(ScavengerFabric::canUse)
                        .then(Commands.argument("item", IdentifierArgument.id())
                                .then(Commands.argument("modifier", IdentifierArgument.id())
                                        .executes(context -> startSelected(context, RunMode.SOLO))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(ScavengerFabric::startSelectedWithMode)))))
                .then(Commands.literal("startgame")
                        .requires(ScavengerFabric::canUse)
                        .executes(ScavengerFabric::startPreparedGame))
                .then(registerTeamCommand("team"))
                .then(registerTeamCommand("addteam"))
                .then(Commands.literal("teams")
                        .requires(ScavengerFabric::canUse)
                        .executes(ScavengerFabric::listTeams)
                        .then(Commands.literal("clear")
                                .executes(ScavengerFabric::clearTeams)))
                .then(Commands.literal("teamlist")
                        .requires(ScavengerFabric::canUse)
                        .executes(ScavengerFabric::listTeams))
                .then(Commands.literal("unteam")
                        .requires(ScavengerFabric::canUse)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ScavengerFabric::removePlayerFromTeams))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerTeamCommand(String literal) {
        return Commands.literal(literal)
                .requires(ScavengerFabric::canUse)
                .then(Commands.argument("leader", StringArgumentType.word())
                        .executes(ScavengerFabric::listTeam)
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ScavengerFabric::addTeamMember)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ScavengerFabric::removeTeamMember)))
                        .then(Commands.literal("delete")
                                .executes(ScavengerFabric::deleteTeam))
                        .then(Commands.literal("clear")
                                .executes(ScavengerFabric::deleteTeam))
                        .then(Commands.literal("list")
                                .executes(ScavengerFabric::listTeam)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerNickCommand() {
        return Commands.literal("nick")
                .executes(ScavengerFabric::showOwnNickname)
                .then(Commands.literal("clear")
                        .executes(ScavengerFabric::clearOwnNickname))
                .then(Commands.literal("set")
                        .requires(ScavengerFabric::canUse)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                                        .executes(ScavengerFabric::setPlayerNickname))))
                .then(Commands.literal("clearplayer")
                        .requires(ScavengerFabric::canUse)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ScavengerFabric::clearPlayerNickname)))
                .then(Commands.literal("list")
                        .requires(ScavengerFabric::canUse)
                        .executes(ScavengerFabric::listNicknames))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(ScavengerFabric::setOwnNickname));
    }

    private static int openMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Scavenger.openControlScreen(player);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSystemMessage(Component.translatable("scavenger.command.help.1"));
        context.getSource().sendSystemMessage(Component.translatable("scavenger.command.help.2"));
        context.getSource().sendSystemMessage(Component.translatable("scavenger.command.help.3"));
        context.getSource().sendSystemMessage(Component.translatable("scavenger.command.help.4"));
        return 1;
    }

    private static int startPreparedGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return Scavenger.startPreparedGame(context.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int startRandom(CommandContext<CommandSourceStack> context, RunMode mode) throws CommandSyntaxException {
        Item item = Scavenger.getRandomRollableItem();
        if (item == Items.AIR) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.no_rollable_items"));
            return 0;
        }

        Identifier modifier = Scavenger.getRandomRollableModifier(item);
        return Scavenger.startRun(context.getSource().getPlayerOrException(), item, modifier, mode) ? 1 : 0;
    }

    private static int startRandomWithMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Optional<RunMode> mode = parseMode(context);
        if (mode.isEmpty()) {
            return 0;
        }

        return startRandom(context, mode.get());
    }

    private static int startSelected(CommandContext<CommandSourceStack> context, RunMode mode) throws CommandSyntaxException {
        Identifier itemId = IdentifierArgument.getId(context, "item");
        String itemValue = itemId.toString();

        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty() || item.get() == Items.AIR) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.invalid_item", itemValue));
            return 0;
        }

        Identifier modifierId = IdentifierArgument.getId(context, "modifier");
        String modifierValue = modifierId.toString();
        if (!Modifiers.getIds().contains(modifierId)) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.invalid_modifier", modifierValue));
            return 0;
        }

        return Scavenger.startRun(context.getSource().getPlayerOrException(), item.get(), modifierId, mode) ? 1 : 0;
    }

    private static int startSelectedWithMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Optional<RunMode> mode = parseMode(context);
        if (mode.isEmpty()) {
            return 0;
        }

        return startSelected(context, mode.get());
    }

    private static Optional<RunMode> parseMode(CommandContext<CommandSourceStack> context) {
        String modeValue = StringArgumentType.getString(context, "mode");
        Optional<RunMode> mode = RunMode.tryById(modeValue);
        if (mode.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.invalid_mode", modeValue));
        }

        return mode;
    }

    private static int addTeamMember(CommandContext<CommandSourceStack> context) {
        ScavengerSavedData data = getData(context);
        String leader = StringArgumentType.getString(context, "leader");
        String player = StringArgumentType.getString(context, "player");
        data.addTeamMember(leader, player);
        List<String> members = data.getTeamMembers(leader);
        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.team_added", player, leader, formatMembers(members)),
                false
        );
        return 1;
    }

    private static int setOwnNickname(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nickname = StringArgumentType.getString(context, "nickname");
        ScavengerSavedData data = getData(context);
        data.setNickname(player.getName().getString(), nickname);
        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.nick_set_self", data.getDisplayName(player.getName().getString())),
                false
        );
        return 1;
    }

    private static int setPlayerNickname(CommandContext<CommandSourceStack> context) {
        String player = StringArgumentType.getString(context, "player");
        String nickname = StringArgumentType.getString(context, "nickname");
        ScavengerSavedData data = getData(context);
        data.setNickname(player, nickname);
        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.nick_set_other", player, data.getDisplayName(player)),
                false
        );
        return 1;
    }

    private static int clearOwnNickname(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ScavengerSavedData data = getData(context);
        if (!data.removeNickname(player.getName().getString())) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.nick_missing_self"));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.nick_cleared_self"),
                false
        );
        return 1;
    }

    private static int clearPlayerNickname(CommandContext<CommandSourceStack> context) {
        String player = StringArgumentType.getString(context, "player");
        ScavengerSavedData data = getData(context);
        if (!data.removeNickname(player)) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.nick_missing_other", player));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.nick_cleared_other", player),
                false
        );
        return 1;
    }

    private static int showOwnNickname(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String realName = player.getName().getString();
        context.getSource().sendSystemMessage(Component.translatable("scavenger.command.nick_current", getData(context).getDisplayName(realName)));
        return 1;
    }

    private static int listNicknames(CommandContext<CommandSourceStack> context) {
        Map<String, String> nicknames = getData(context).getNicknames();
        if (nicknames.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.nick_no_entries"));
            return 0;
        }

        StringJoiner joiner = new StringJoiner("; ");
        nicknames.forEach((player, nickname) -> joiner.add(player + " = " + nickname));
        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.nick_list", joiner.toString()),
                false
        );
        return 1;
    }

    private static int removeTeamMember(CommandContext<CommandSourceStack> context) {
        ScavengerSavedData data = getData(context);
        String leader = StringArgumentType.getString(context, "leader");
        String player = StringArgumentType.getString(context, "player");
        if (!data.removeTeamMember(leader, player)) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.team_member_missing", player, leader));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.team_removed", player, leader),
                false
        );
        return 1;
    }

    private static int deleteTeam(CommandContext<CommandSourceStack> context) {
        ScavengerSavedData data = getData(context);
        String leader = StringArgumentType.getString(context, "leader");
        if (!data.removeTeam(leader)) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.team_missing", leader));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.team_deleted", leader),
                false
        );
        return 1;
    }

    private static int removePlayerFromTeams(CommandContext<CommandSourceStack> context) {
        ScavengerSavedData data = getData(context);
        String player = StringArgumentType.getString(context, "player");
        if (!data.removePlayerFromTeams(player)) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.player_not_in_team", player));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.player_unteamed", player),
                false
        );
        return 1;
    }

    private static int clearTeams(CommandContext<CommandSourceStack> context) {
        getData(context).clearTeams();
        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.teams_cleared"),
                false
        );
        return 1;
    }

    private static int listTeam(CommandContext<CommandSourceStack> context) {
        ScavengerSavedData data = getData(context);
        String leader = StringArgumentType.getString(context, "leader");
        List<String> members = data.getTeamMembers(leader);
        if (members.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.team_missing", leader));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.team_list", leader, formatMembers(members)),
                false
        );
        return 1;
    }

    private static int listTeams(CommandContext<CommandSourceStack> context) {
        Map<String, List<String>> teams = getData(context).getTeams();
        if (teams.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("scavenger.command.no_teams"));
            return 0;
        }

        StringJoiner joiner = new StringJoiner("; ");
        teams.forEach((leader, members) -> joiner.add(leader + ": " + formatMembers(members)));
        context.getSource().sendSuccess(
                () -> Component.translatable("scavenger.command.teams_list", joiner.toString()),
                false
        );
        return 1;
    }

    private static ScavengerSavedData getData(CommandContext<CommandSourceStack> context) {
        return ScavengerSavedData.get(context.getSource().getServer().overworld());
    }

    private static String formatMembers(List<String> members) {
        if (members.isEmpty()) {
            return "-";
        }

        return String.join(", ", members);
    }

    private static boolean canUse(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || Scavenger.canManage(player);
    }
}
