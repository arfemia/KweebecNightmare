package com.ziggfreed.kweebec.hunter;

import java.util.EnumSet;

import javax.annotation.Nonnull;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.asset.builder.holder.BooleanHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.NumberArrayHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleSequenceValidator;
import com.hypixel.hytale.server.npc.asset.builder.validators.IntSequenceValidator;
import com.hypixel.hytale.server.npc.asset.builder.validators.StringNullOrNotEmptyValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;

/**
 * Builds {@link ActionKweebecHunterWave}: {@code {"Type": "KweebecHunterWave", "Archetype": "Lunger",
 * "Count": [1, 1], "PerPlayer": true, "Radius": [8.4, 14], "Even": false, "AroundOnePlayer": true,
 * "Announce": true}}. Flat keys and native {@code [min, max]} ranges, the NPC builder vocabulary,
 * legal only inside an encounter script.
 */
public class BuilderKweebecHunterWave extends BuilderActionBase {

    protected final StringHolder archetype = new StringHolder();
    protected final NumberArrayHolder count = new NumberArrayHolder();
    protected final BooleanHolder perPlayer = new BooleanHolder();
    protected final NumberArrayHolder radius = new NumberArrayHolder();
    protected final BooleanHolder even = new BooleanHolder();
    protected final BooleanHolder aroundOnePlayer = new BooleanHolder();
    protected final BooleanHolder announce = new BooleanHolder();

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Put a wave of Blighted hunters down around the chase round's survivors";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Asks the chase round that owns this encounter for one wave of hunters: Count [min, max] of them "
                + "(times the party size when PerPlayer), landing Radius [min, max] blocks from the anchor, spaced "
                + "evenly around it (Even) or scattered, the anchor being one survivor picked at random "
                + "(AroundOnePlayer) or the party's centre. Archetype fixes what rises (an id under "
                + "Server/KweebecNightmare/Hunters); left blank, the round's own corruption-gated weighted roster "
                + "picks. Announce tells every survivor the light drew them when a hunter actually appears. The "
                + "round's live-hunter ceiling always wins, so a wave with no room puts nobody down.";
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionKweebecHunterWave(this, builderSupport);
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        getString(data, "Archetype", archetype, null, StringNullOrNotEmptyValidator.get(), BuilderDescriptorState.Stable,
                "A hunter archetype id to raise, or blank for the round's own weighted pick", null);
        getIntRange(data, "Count", count, new int[] {1, 1},
                IntSequenceValidator.betweenWeaklyMonotonic(0, Integer.MAX_VALUE), BuilderDescriptorState.Stable,
                "Hunters this wave asks for, [min, max] inclusive", null);
        getBoolean(data, "PerPlayer", perPlayer, true, BuilderDescriptorState.Stable,
                "Multiply Count by the party size", null);
        getDoubleRange(data, "Radius", radius, new double[] {8.0, 14.0},
                DoubleSequenceValidator.betweenWeaklyMonotonic(0.0, Double.MAX_VALUE), BuilderDescriptorState.Stable,
                "Blocks from the anchor a hunter lands, [min, max] inclusive", null);
        getBoolean(data, "Even", even, false, BuilderDescriptorState.Stable,
                "Space the points evenly around the anchor instead of scattering them", null);
        getBoolean(data, "AroundOnePlayer", aroundOnePlayer, false, BuilderDescriptorState.Stable,
                "Anchor on one survivor picked at random instead of the party's centre", null);
        getBoolean(data, "Announce", announce, false, BuilderDescriptorState.Stable,
                "Tell every survivor the light drew them when a hunter appears", null);
        requireInstructionType(EnumSet.of(InstructionType.Encounter));
        return this;
    }

    /** The wave this builder was authored as, resolved once against the script's own context. */
    @Nonnull
    public HunterWave wave(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        int[] c = count.getIntArray(ctx);
        double[] r = radius.get(ctx);
        return new HunterWave(archetype.get(ctx), c[0], c[1], perPlayer.get(ctx), r[0], r[1],
                even.get(ctx), aroundOnePlayer.get(ctx), announce.get(ctx));
    }
}
