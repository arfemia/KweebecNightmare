package com.ziggfreed.kweebec.command;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.instance.leaderboard.LeaderboardEntry;
import com.ziggfreed.common.instance.leaderboard.LeaderboardPage;
import com.ziggfreed.common.instance.play.PlayModePage;
import com.ziggfreed.common.inventory.InventoryUtil;
import com.ziggfreed.common.party.page.PartyInvitePage;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.experience.KweebecExperience;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.lobby.KweebecLobby;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.round.KweebecMode;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * {@code /kweebec} - the round entry point. A bare {@code /kweebec} queues for a round on the default
 * preset, as {@code /kweebec start} does. Every {@link KweebecSubcommand} is a real engine subcommand, so a
 * player types {@code /kweebec leaderboard} or {@code /kweebec start hardcore}, never a {@code --name=} flag.
 *
 * <ul>
 *   <li>{@code start [preset]} - queue for a Chase round on the preset (default nightmare).</li>
 *   <li>{@code exit} - leave the queue, else the current round.</li>
 *   <li>{@code score}, {@code leaderboard}, {@code party} - the caller's bests, the board, the party screen.</li>
 *   <li>{@code clash [preset]}, {@code domination [preset]}, {@code endall}, {@code give [count]} and
 *       {@code clashhost} - admin-only: {@link KweebecSubcommand} holds every name the command answers to
 *       and who may run it.</li>
 * </ul>
 *
 * <p>An optional positional argument is an engine usage variant: the subcommand itself takes no argument,
 * and its variant, which takes one required argument, answers when one is typed. A word after
 * {@code /kweebec} that names no subcommand reaches the root's own one-argument variant, which answers with
 * the usage line.
 *
 * <p>The diegetic triggers (the guide's dialogue, the Play screen) queue through the same
 * {@link KweebecLobby}; the command is one entry among them.
 */
public final class KweebecCommand extends CommandBase {

    public KweebecCommand() {
        // The engine resolves the command + arg descriptions as localization keys.
        super("kweebec", Lang.CMD_DESC);
        this.addAliases("kn");
        // The non-deprecated equivalent of the old setPermissionGroup(GameMode.Adventure). A player
        // subcommand declares no group of its own, so it inherits this one and stays open to every player;
        // an admin subcommand declares the admin group and requires the admin node (Sub).
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER);
        for (KweebecSubcommand sub : KweebecSubcommand.values()) {
            this.addSubCommand(new Sub(sub));
        }
        this.addUsageVariant(new UnknownSub());
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        run(KweebecSubcommand.START, ctx, null);
    }

    /** One {@code /kweebec} subcommand: its first name, the rest as aliases, and its positional variant. */
    private static final class Sub extends CommandBase {

        private final KweebecSubcommand sub;

        Sub(@Nonnull KweebecSubcommand sub) {
            super(sub.names().get(0), sub.descKey());
            this.sub = sub;
            if (sub.admin()) {
                // A group of its own keeps the root's Adventurer group off this subcommand, so the engine
                // neither runs nor lists it for a player without the admin node (an operator holds every
                // node). Its variant inherits the node. mayRun stays as the second guard.
                this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADMIN);
                this.requirePermission(KweebecSubcommand.ADMIN_PERMISSION);
            }
            List<String> names = sub.names();
            if (names.size() > 1) {
                this.addAliases(names.subList(1, names.size()).toArray(new String[0]));
            }
            String argName = sub.argName();
            String argDescKey = sub.argDescKey();
            if (argName != null && argDescKey != null) {
                this.addUsageVariant(new WithArg(sub, argName, argDescKey));
            }
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            run(sub, ctx, null);
        }
    }

    /** A subcommand typed with its one positional argument ({@code /kweebec start hardcore}). */
    private static final class WithArg extends CommandBase {

        private final KweebecSubcommand sub;
        private final RequiredArg<String> arg;

        WithArg(@Nonnull KweebecSubcommand sub, @Nonnull String argName, @Nonnull String argDescKey) {
            super(sub.descKey());
            this.sub = sub;
            this.arg = withRequiredArg(argName, argDescKey, ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            run(sub, ctx, arg.get(ctx));
        }
    }

    /** {@code /kweebec <word>} where the word names no subcommand: the usage line. */
    private static final class UnknownSub extends CommandBase {

        UnknownSub() {
            super(Lang.CMD_DESC);
            withRequiredArg("sub", Lang.ARG_SUB_DESC, ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Lang.msg(Lang.CMD_USAGE));
        }
    }

    /** Run {@code sub} with its positional argument, or null when none was typed. */
    private static void run(@Nonnull KweebecSubcommand sub, @Nonnull CommandContext ctx, @Nullable String arg) {
        // The command is open to every player, so an admin subcommand refuses here, before anything runs.
        if (!sub.mayRun(ctx.sender())) {
            ctx.sendMessage(Lang.msg(Lang.CMD_NO_PERMISSION));
            return;
        }
        switch (sub) {
            case START -> start(ctx, arg);
            case CLASH -> startMode(ctx, KweebecMode.CLASH, arg != null ? arg : "clash_1v1");
            case DOMINATION -> startMode(ctx, KweebecMode.DOMINATION, arg != null ? arg : "domination_koth");
            case EXIT -> exit(ctx);
            case END_ALL -> endAll(ctx);
            case GIVE -> give(ctx, arg);
            case SCORE -> score(ctx);
            case LEADERBOARD -> leaderboard(ctx);
            case PARTY -> party(ctx);
            case CLASH_HOST -> spawnClashHost(ctx);
        }
    }

    /** {@code start [preset]} - queue for a Chase round on {@code requested}, or the default preset. */
    private static void start(@Nonnull CommandContext ctx, @Nullable String requested) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID initiator = player.getUuid();
        if (RoundService.getInstance().registry().isInRound(initiator)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_IN_ROUND));
            return;
        }

        String presetId = PresetConfig.DEFAULT;
        if (requested != null) {
            if (PresetConfig.getInstance().byId(requested) == null) {
                ctx.sendMessage(Lang.msg(Lang.CMD_UNKNOWN_PRESET));
                return;
            }
            presetId = requested;
        }

        // Queue for the preset: the lobby gathers a party over a short fill window, then
        // launches the round. The queue itself toasts join + countdown feedback.
        switch (KweebecLobby.join(initiator, presetId)) {
            case JOINED -> {
                ctx.sendMessage(Lang.msg(Lang.CMD_QUEUED));
                // Keep the player on a still-closable Play screen (live roster) after queueing.
                openPage(player, new PlayModePage(player, KweebecExperience.playModeDeps(), presetId));
            }
            case ALREADY_QUEUED -> ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_QUEUED));
            case ALREADY_ENGAGED -> ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_IN_ROUND));
            case QUEUE_UNAVAILABLE -> ctx.sendMessage(Lang.msg(Lang.CMD_START_FAILED));
        }
    }

    /**
     * {@code clash [preset]} / {@code domination [preset]} - DIRECT PvP start (testing / admin): gather every
     * eligible player in the caller's current world into one match and start it immediately (no queue). The
     * teams are split from that roster. The diegetic queue/NPC entry routes through the lobby instead.
     */
    private static void startMode(@Nonnull CommandContext ctx, @Nonnull KweebecMode mode, @Nonnull String presetId) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID initiator = player.getUuid();
        if (RoundService.getInstance().registry().isInRound(initiator)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_IN_ROUND));
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Lang.msg(Lang.CMD_START_FAILED));
            return;
        }
        // Gather every eligible (not-already-in-a-round) player in the caller's world, initiator first.
        List<UUID> party = new ArrayList<>();
        party.add(initiator);
        for (PlayerRef pr : world.getPlayerRefs()) {
            UUID u = pr.getUuid();
            if (u != null && !u.equals(initiator) && !RoundService.getInstance().registry().isInRound(u)) {
                party.add(u);
            }
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_STARTING));
        RoundService.getInstance().startRound(initiator, party, presetId, mode).whenComplete((id, err) -> {
            if (err != null) {
                player.sendMessage(Lang.msg(Lang.CMD_START_FAILED));
            }
        });
    }

    /** {@code party} - open the party + invite screen. */
    private static void party(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_PARTY_OPENED));
        openPage(player, new PartyInvitePage(player, KweebecExperience.partyDeps()));
    }

    /** Open a custom page for {@code player} on its world thread (resolving ref/store there). */
    private static void openPage(@Nonnull PlayerRef player, @Nonnull InteractiveCustomUIPage<?> page) {
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                Store<EntityStore> store = ref.getStore();
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) {
                    p.getPageManager().openCustomPage(ref, store, page);
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] openPage failed: " + t.getMessage());
            }
        });
    }

    private static void exit(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID uuid = player.getUuid();
        // Leave a queue first (not yet in a round), else leave the live round.
        if (KweebecLobby.isQueued(uuid)) {
            KweebecLobby.leave(uuid);
            ctx.sendMessage(Lang.msg(Lang.CMD_LEFT_QUEUE));
            return;
        }
        if (RoundService.getInstance().registry().isInRound(uuid)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_LEAVING));
            RoundService.getInstance().exit(uuid);
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_NOT_QUEUED_OR_IN_ROUND));
    }

    /** {@code clashhost} - ensure the PvP Clash Host NPC exists in the caller's world (debug placement). */
    private static void spawnClashHost(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Lang.msg(Lang.CMD_CLASH_HOST_FAILED));
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_CLASH_HOST_SPAWNED));
        world.execute(() -> KweebecNightmarePlugin.debugSpawnClashHost(world));
    }

    private static void endAll(@Nonnull CommandContext ctx) {
        int n = RoundService.getInstance().endAll();
        if (n == 0) {
            ctx.sendMessage(Lang.msg(Lang.CMD_NO_ACTIVE_ROUNDS));
        } else {
            ctx.sendMessage(Lang.msg(Lang.CMD_ENDED));
        }
    }

    /** {@code give [count]} - grant the caller count Moonbloom charges (testing aid; default 5). */
    private static void give(@Nonnull CommandContext ctx, @Nullable String requested) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        int n = 5;
        if (requested != null) {
            try {
                n = Math.max(1, Integer.parseInt(requested.trim()));
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        final int count = n;
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Lang.msg(Lang.CMD_START_FAILED));
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_GIVE_DONE).param("0", count));
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                InventoryUtil.give(store, ref, Moonbloom.CHARGE_ITEM, count);
            }
        });
    }

    /** {@code score} - report the caller's own best leaderboard entry in each party-size bucket. */
    private static void score(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID uuid = player.getUuid();
        boolean any = false;
        // Buckets are now "<difficulty>_<partySize>"; scan every recorded bucket the caller appears in.
        for (String bucket : new TreeSet<>(KweebecExperience.board().bucketKeys())) {
            LeaderboardEntry e = KweebecExperience.board().forBucket(bucket).get(uuid);
            if (e != null) {
                if (!any) {
                    ctx.sendMessage(Lang.msg(Lang.CMD_SCORE_HEADER));
                    any = true;
                }
                ctx.sendMessage(Message.raw(formatEntry(bucket, e)));
            }
        }
        if (!any) {
            ctx.sendMessage(Lang.msg(Lang.CMD_SCORE_NONE));
        }
    }

    /** {@code leaderboard} - open the in-game leaderboard page (the shared instance-experience board). */
    private static void leaderboard(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        openPage(player, new LeaderboardPage(player, KweebecExperience.leaderboardDeps()));
    }

    @Nonnull
    private static String formatEntry(@Nonnull String label, @Nonnull LeaderboardEntry e) {
        String time = e.bestTimeSeconds > 0 ? ", " + e.bestTimeSeconds + "s win" : "";
        return label + ": " + e.bestScore + " pts" + time + " (" + e.plays + " plays)";
    }
}
