package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;

/**
 * Resolves the effective merge mode for a Kweebec content type across all loaded
 * packs. Mirrors hyMMO's {@code PackControlRegistry}.
 *
 * <p>The asset store collapses multiple packs to one value per id, but each pack
 * may declare its own mode. The resolution rule is: if <b>any</b> loaded pack
 * declares {@code replace} for a type, that type is in replace mode; otherwise
 * {@code add}. Both content stores declare
 * {@code loadsAfter(KweebecPackControlAsset.class)}, so the control store is always
 * populated before a content merge handler runs.
 */
public final class KweebecPackControlRegistry {

    private KweebecPackControlRegistry() {
    }

    /**
     * True when at least one loaded pack declares {@code replace} for the given
     * content-type key ({@link KweebecPackControlAsset#PRESETS} /
     * {@link KweebecPackControlAsset#HUNTERS}).
     */
    public static boolean isReplace(@Nonnull String typeKey) {
        AssetStore<String, KweebecPackControlAsset, DefaultAssetMap<String, KweebecPackControlAsset>> store =
                AssetRegistry.getAssetStore(KweebecPackControlAsset.class);
        if (store == null) {
            return false;
        }
        for (KweebecPackControlAsset control : store.getAssetMap().getAssetMap().values()) {
            if ("replace".equalsIgnoreCase(control.getMode(typeKey))) {
                return true;
            }
        }
        return false;
    }
}
