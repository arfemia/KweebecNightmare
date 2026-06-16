package com.ziggfreed.kweebec.command;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.RoundPreset;
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
        this.setPermissionGroup(GameMode.Adventure);
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
            default -> ctx.sendMessage(Lang.msg(Lang.CMD_USAGE));
        }
    }

    private void start(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof Player player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID initiator = player.getUuid();
        RoundService svc = RoundService.getInstance();
        if (svc.registry().isInRound(initiator)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_ALREADY_IN_ROUND));
            return;
        }

        RoundPreset preset = RoundPreset.DEFAULT;
        if (ctx.provided(presetArg)) {
            RoundPreset parsed = RoundPreset.byId(presetArg.get(ctx));
            if (parsed == null) {
                ctx.sendMessage(Lang.msg(Lang.CMD_UNKNOWN_PRESET));
                return;
            }
            preset = parsed;
        }

        List<UUID> party = partyInSameWorld(initiator);
        ctx.sendMessage(Lang.msg(Lang.CMD_STARTING));
        svc.startChase(initiator, party, preset).whenComplete((roundId, err) -> {
            if (err != null) {
                ctx.sendMessage(Lang.msg(Lang.CMD_START_FAILED));
            }
        });
    }

    private void exit(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof Player player)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        UUID uuid = player.getUuid();
        if (!RoundService.getInstance().registry().isInRound(uuid)) {
            ctx.sendMessage(Lang.msg(Lang.CMD_NOT_IN_ROUND));
            return;
        }
        ctx.sendMessage(Lang.msg(Lang.CMD_LEAVING));
        RoundService.getInstance().exit(uuid);
    }

    private void endAll(@Nonnull CommandContext ctx) {
        int n = RoundService.getInstance().endAll();
        if (n == 0) {
            ctx.sendMessage(Lang.msg(Lang.CMD_NO_ACTIVE_ROUNDS));
        } else {
            ctx.sendMessage(Lang.msg(Lang.CMD_ENDED));
        }
    }

    /** The caller plus every other online player in the caller's current world. */
    @Nonnull
    private static List<UUID> partyInSameWorld(@Nonnull UUID initiator) {
        PlayerRef initRef = Universe.get().getPlayer(initiator);
        if (initRef == null) {
            return List.of(initiator);
        }
        UUID worldUuid = initRef.getWorldUuid();
        return Universe.get().getPlayers().stream()
                .filter(pr -> worldUuid.equals(pr.getWorldUuid()))
                .map(PlayerRef::getUuid)
                .collect(Collectors.toList());
    }
}
