package com.ziggfreed.kweebec.command;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.instance.leaderboard.Leaderboard;
import com.ziggfreed.common.instance.leaderboard.LeaderboardEntry;
import com.ziggfreed.common.instance.leaderboard.LeaderboardPage;
import com.ziggfreed.common.instance.queue.QueuePage;
import com.ziggfreed.common.inventory.InventoryUtil;
import com.ziggfreed.common.lobby.JoinResult;
import com.ziggfreed.common.party.page.PartyInvitePage;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.experience.KweebecExperience;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.lobby.KweebecLobby;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.npc.KweebecGuideSpawn;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * {@code /kweebec [start|exit|endall] [preset]} - the round entry point.
 *
 * <ul>
 *   <li>{@code start [preset]} - start a Chase round; the party is the caller plus
 *       every other player in the caller's current world (so the lobby co-ops in
 *       together). Preset = amateur | nightmare | hardcore (default nightmare).</li>
 *   <li>{@code exit} - leave your current round.</li>
 *   <li>{@code endall} - force-end every live round (admin/testing).</li>
 * </ul>
 *
 * <p>The diegetic triggers (void-rift pad, shrine block, guide NPC) route through
 * the same {@link RoundService#startChase} entry; the command is the first one.
 */
public final class KweebecCommand extends CommandBase {

    private final OptionalArg<String> subArg;
    private final OptionalArg<String> presetArg;

    public KweebecCommand() {
        // The engine resolves the command + arg descriptions as localization keys.
        super("kweebec", Lang.CMD_DESC);
        this.addAliases("kn");
        // The non-deprecated equivalent of the old setPermissionGroup(GameMode.Adventure).
        this.setPermissionGroups("hytale:Adventurer");
        this.subArg = withOptionalArg("sub", Lang.ARG_SUB_DESC, ArgTypes.STRING);
        this.presetArg = withOptionalArg("preset", Lang.ARG_PRESET_DESC, ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String sub = ctx.provided(subArg) ? subArg.get(ctx).toLowerCase() : "start";
        switch (sub) {
            case "start" -> start(ctx);
            case "exit", "leave" -> exit(ctx);
            case "endall", "end" -> endAll(ctx);
            case "give" -> give(ctx);
            case "score" -> score(ctx);
            case "leaderboard", "lb" -> leaderboard(ctx);
            case "party" -> party(ctx);
            case "spawnguide", "guide" -> spawnGuide(ctx);
            default -> ctx.sendMessage(Lang.msg(Lang.CMD_USAGE));
        }
    }

    private void start(@Nonnull CommandContext ctx) {
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
        if (ctx.provided(presetArg)) {
            String requested = presetArg.get(ctx);
            if (PresetConfig.getInstance().byId(requested) == null) {
                ctx.sendMessage(Lang.msg(Lang.CMD_UNKNOWN_PRESET));
                return;
            }
            presetId = requested;
        }

        // Queue for the preset: the lobby gathers a party over a short fill window, then
        // launches via startChase. The queue itself toasts join + countdown feedback.
        switch (KweebecLobby.join(initiator, presetId)) {
            case JOINED -> {
                ctx.sendMessage(Lang.msg(Lang.CMD_QUEUED));
                // Keep the player on a still-closable queue screen after queueing.
                openPage(player, new QueuePage(player, KweebecExperience.queueDeps()));
            }
            case ALREADY_QUEUED -> ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_QUEUED));
            case ALREADY_ENGAGED -> ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_IN_ROUND));
            case QUEUE_UNAVAILABLE -> ctx.sendMessage(Lang.msg(Lang.CMD_START_FAILED));
        }
    }

    /** {@code party} - open the party + invite screen. */
    private void party(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_PARTY_OPENED));
        openPage(player, new PartyInvitePage(player, KweebecExperience.partyDeps()));
    }

    /** Open a custom page for {@code player} on its world thread (resolving ref/store there). */
    private void openPage(@Nonnull PlayerRef player, @Nonnull InteractiveCustomUIPage<?> page) {
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

    private void exit(@Nonnull CommandContext ctx) {
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

    /** {@code spawnguide} - (re)place the Grove Warden guide at the caller's position (debug). */
    private void spawnGuide(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Lang.msg(Lang.CMD_GUIDE_FAILED));
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_GUIDE_SPAWNED));
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc == null) {
                    return;
                }
                KweebecGuideSpawn.reposition(world, store, tc.getPosition(), 0.0f);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] spawnguide failed: " + t.getMessage());
            }
        });
    }

    private void endAll(@Nonnull CommandContext ctx) {
        int n = RoundService.getInstance().endAll();
        if (n == 0) {
            ctx.sendMessage(Lang.msg(Lang.CMD_NO_ACTIVE_ROUNDS));
        } else {
            ctx.sendMessage(Lang.msg(Lang.CMD_ENDED));
        }
    }

    /** {@code give [n]} - grant the caller n Moonbloom charges (testing aid; default 5). */
    private void give(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        int n = 5;
        if (ctx.provided(presetArg)) {
            try {
                n = Math.max(1, Integer.parseInt(presetArg.get(ctx).trim()));
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
    private void score(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID uuid = player.getUuid();
        boolean any = false;
        for (int ps = 1; ps <= MAX_PARTY_SIZE; ps++) {
            LeaderboardEntry e = KweebecExperience.board().forBucket(String.valueOf(ps)).get(uuid);
            if (e != null) {
                if (!any) {
                    ctx.sendMessage(Lang.msg(Lang.CMD_SCORE_HEADER));
                    any = true;
                }
                ctx.sendMessage(Message.raw(formatEntry(ps + "p", e)));
            }
        }
        if (!any) {
            ctx.sendMessage(Lang.msg(Lang.CMD_SCORE_NONE));
        }
    }

    /** {@code leaderboard} - open the in-game leaderboard page (the shared instance-experience board). */
    private void leaderboard(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        openPage(player, new LeaderboardPage(player, KweebecExperience.leaderboardDeps()));
    }

    /** Largest party size the score chat surface scans. */
    private static final int MAX_PARTY_SIZE = 8;

    @Nonnull
    private static String formatEntry(@Nonnull String label, @Nonnull LeaderboardEntry e) {
        String time = e.bestTimeSeconds > 0 ? ", " + e.bestTimeSeconds + "s win" : "";
        return label + ": " + e.bestScore + " pts" + time + " (" + e.plays + " plays)";
    }
}
