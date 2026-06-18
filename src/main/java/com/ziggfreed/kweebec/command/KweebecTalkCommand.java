package com.ziggfreed.kweebec.command;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.dialogue.page.DialoguePage;
import com.ziggfreed.kweebec.dialogue.KweebecDialogue;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * {@code /kntalk} (alias {@code /kweebectalk}) - opens the demo dialogue with the
 * Whispering Sapling. The MVP trigger for the lifted dialogue engine: an
 * {@link AbstractPlayerCommand} hands the world-thread {@code store}/{@code ref}, so
 * the page open needs no scheduling hop and no page pre-registration. The diegetic
 * triggers (an NPC, a shrine block) route the same {@link DialoguePage} later.
 */
public final class KweebecTalkCommand extends AbstractPlayerCommand {

    public KweebecTalkCommand() {
        super("kntalk", Lang.CMD_TALK_DESC);
        this.addAliases("kweebectalk");
        // The non-deprecated equivalent of the old setPermissionGroup(GameMode.Adventure).
        this.setPermissionGroups("hytale:Adventurer");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Lang.msg(Lang.CMD_PLAYERS_ONLY));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new DialoguePage(playerRef, KweebecDialogue.INTRO_ID, null, KweebecDialogue.deps()));
    }
}
