package com.ziggfreed.kweebec.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The click event the {@link KweebecLeaderboardPage} round-trips: an {@code Action}
 * (a party-size {@code "tab"} switch, or {@code "close"}) plus the target {@code Party}
 * size string. Mirrors the shape of ziggfreed-common's {@code DialogueEventData}.
 */
public class LeaderboardEventData {

    public String action;
    public String party;

    public static final BuilderCodec<LeaderboardEventData> CODEC =
            BuilderCodec.builder(LeaderboardEventData.class, LeaderboardEventData::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING),
                            (data, value, info) -> data.action = value,
                            (data, info) -> data.action)
                    .add()
                    .append(new KeyedCodec<>("Party", Codec.STRING),
                            (data, value, info) -> data.party = value,
                            (data, info) -> data.party)
                    .add()
                    .build();
}
