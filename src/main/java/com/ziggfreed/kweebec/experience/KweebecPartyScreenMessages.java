package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.party.page.PartyScreenMessages;
import com.ziggfreed.kweebec.i18n.Lang;

/** Lang-backed chrome for the party + invite screen. */
public final class KweebecPartyScreenMessages implements PartyScreenMessages {

    @Override @Nonnull public Message title() {
        return Lang.msg(Lang.PARTY_TITLE);
    }

    @Override @Nonnull public Message tabParty() {
        return Lang.msg(Lang.PARTY_TAB_PARTY);
    }

    @Override @Nonnull public Message tabInvite() {
        return Lang.msg(Lang.PARTY_TAB_INVITE);
    }

    @Override @Nonnull public Message emptyInviteList() {
        return Lang.msg(Lang.PARTY_EMPTY_INVITE);
    }

    @Override @Nonnull public Message emptyParty() {
        return Lang.msg(Lang.PARTY_EMPTY_PARTY);
    }

    @Override @Nonnull public Message inviteButton() {
        return Lang.msg(Lang.PARTY_BTN_INVITE);
    }

    @Override @Nonnull public Message kickButton() {
        return Lang.msg(Lang.PARTY_BTN_KICK);
    }

    @Override @Nonnull public Message leaveButton() {
        return Lang.msg(Lang.PARTY_BTN_LEAVE);
    }

    @Override @Nonnull public Message disbandButton() {
        return Lang.msg(Lang.PARTY_BTN_DISBAND);
    }

    @Override @Nonnull public Message queueButton() {
        return Lang.msg(Lang.PARTY_BTN_QUEUE);
    }

    @Override @Nonnull public Message acceptButton() {
        return Lang.msg(Lang.PARTY_BTN_ACCEPT);
    }

    @Override @Nonnull public Message declineButton() {
        return Lang.msg(Lang.PARTY_BTN_DECLINE);
    }

    @Override @Nonnull public Message ownerBadge() {
        return Lang.msg(Lang.PARTY_OWNER_BADGE);
    }

    @Override @Nonnull public Message memberCount(int size, int maxSize) {
        return Lang.msg(Lang.PARTY_COUNT).param("0", size).param("1", maxSize);
    }

    @Override @Nonnull public Message privacyPublic() {
        return Lang.msg(Lang.PARTY_PRIVACY_PUBLIC);
    }

    @Override @Nonnull public Message privacyPrivate() {
        return Lang.msg(Lang.PARTY_PRIVACY_PRIVATE);
    }

    @Override @Nonnull public Message toastInviteSent(@Nonnull String playerName) {
        // Reuse the existing service-side "Invite sent to {0}." string (DRY; same meaning).
        return Lang.msg(Lang.PARTY_MSG_SENT).param("0", playerName);
    }

    @Override @Nonnull public Message toastInviteFailed() {
        return Lang.msg(Lang.PARTY_TOAST_INVITE_FAILED);
    }
}
