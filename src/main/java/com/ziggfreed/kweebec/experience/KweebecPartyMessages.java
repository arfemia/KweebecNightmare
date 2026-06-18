package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.party.PartyMessages;
import com.ziggfreed.kweebec.i18n.Lang;

/** Lang-backed toasts + banner for party invites / membership changes. */
public final class KweebecPartyMessages implements PartyMessages {

    @Override @Nullable public Message invited(@Nonnull String inviterName, int size, int maxSize) {
        return Lang.msg(Lang.PARTY_MSG_INVITED).param("0", inviterName).param("1", size).param("2", maxSize);
    }

    @Override @Nullable public Message inviteBannerPrimary(@Nonnull String inviterName) {
        return Lang.msg(Lang.PARTY_MSG_BANNER).param("0", inviterName);
    }

    @Override @Nullable public Message inviteBannerSecondary() {
        return Lang.msg(Lang.PARTY_MSG_BANNER_SUB);
    }

    @Override @Nullable public Message inviteSent(@Nonnull String inviteeName) {
        return Lang.msg(Lang.PARTY_MSG_SENT).param("0", inviteeName);
    }

    @Override @Nullable public Message inviteDeclined(@Nonnull String inviteeName) {
        return Lang.msg(Lang.PARTY_MSG_DECLINED).param("0", inviteeName);
    }

    @Override @Nullable public Message inviteExpired() {
        return Lang.msg(Lang.PARTY_MSG_EXPIRED);
    }

    @Override @Nullable public Message memberJoined(@Nonnull String name, int size, int maxSize) {
        return Lang.msg(Lang.PARTY_MSG_JOINED).param("0", name).param("1", size).param("2", maxSize);
    }

    @Override @Nullable public Message memberLeft(@Nonnull String name) {
        return Lang.msg(Lang.PARTY_MSG_LEFT).param("0", name);
    }

    @Override @Nullable public Message kicked() {
        return Lang.msg(Lang.PARTY_MSG_KICKED);
    }

    @Override @Nullable public Message kickedMember(@Nonnull String name) {
        return Lang.msg(Lang.PARTY_MSG_KICKED_MEMBER).param("0", name);
    }

    @Override @Nullable public Message disbanded() {
        return Lang.msg(Lang.PARTY_MSG_DISBANDED);
    }

    @Override @Nullable public Message ownerTransferred(@Nonnull String newOwnerName) {
        return Lang.msg(Lang.PARTY_MSG_OWNER).param("0", newOwnerName);
    }
}
