package app.twoeno.extension.spotify;

import java.lang.reflect.Field;
import java.util.Map;

import app.twoeno.extension.shared.Logger;
import app.twoeno.extension.shared.Reflection;

/**
 * Client-side Spotify Premium unlock.
 * <p>
 * The account attributes of the remote config product state decide what the client believes the
 * account may do (on-demand playback, shuffle, skips, ads, the Premium settings UI). The values
 * are overwritten in place, so every consumer of the map sees the Premium values.
 * <p>
 * Everything here is best effort: a missing attribute or field on a new app version is logged
 * and skipped, the client keeps running with the server value.
 * <p>
 * Ported from ReVanced's UnlockPremiumPatch through the Femify fork family (see NOTICE),
 * GPL-3.0.
 */
@SuppressWarnings("unused")
public final class UnlockPremiumPatch {

    private static class OverrideAttribute {
        /** Account attribute key. */
        final String key;

        /** Override value. */
        final Object overrideValue;

        /**
         * If this attribute is expected to be present in all situations.
         * If false, then no error is raised if the attribute is missing.
         */
        final boolean isExpected;

        OverrideAttribute(String key, Object overrideValue) {
            this(key, overrideValue, true);
        }

        OverrideAttribute(String key, Object overrideValue, boolean isExpected) {
            this.key = key;
            this.overrideValue = overrideValue;
            this.isExpected = isExpected;
        }
    }

    private static final OverrideAttribute[] PREMIUM_OVERRIDES = {
            // Disables player and app ads.
            new OverrideAttribute("ads", Boolean.FALSE),
            // Works along on-demand, allows playing any song without restriction.
            new OverrideAttribute("player-license", "premium"),
            // Alternative key used by some app versions; not present on all accounts, so its
            // absence must not be reported as an error.
            new OverrideAttribute("player-license-v2", "premium", false),
            // Disables shuffle being initially enabled when first playing a playlist.
            new OverrideAttribute("shuffle", Boolean.FALSE),
            // Allows playing any song on-demand, without a shuffled order.
            new OverrideAttribute("on-demand", Boolean.TRUE),
            // Make sure playing songs is not disabled remotely and playlists show up.
            new OverrideAttribute("streaming", Boolean.TRUE),
            // Allows adding songs to the queue and removes the smart shuffle mode restriction.
            new OverrideAttribute("pick-and-shuffle", Boolean.FALSE, false),
            // Disables the shuffle-mode streaming-rule, which forces songs to be played shuffled
            // and breaks the player when other patches are applied.
            new OverrideAttribute("streaming-rules", "", false),
            // Enables the Premium UI in settings and removes the Premium button in the nav-bar.
            new OverrideAttribute("nft-disabled", "1", false),
            // Enables Spotify Connect and disables other Premium related UI, like buying premium.
            // It also removes the download button.
            new OverrideAttribute("type", "premium", false),
            // Enables the (discontinued) Spotify Car Thing hardware device.
            new OverrideAttribute("can_use_superbird", Boolean.TRUE, false),
            // Removes the Premium button in the nav-bar for tablet users.
            new OverrideAttribute("tablet-free", Boolean.FALSE, false),
    };

    private UnlockPremiumPatch() {
    }

    /**
     * Injection point: the account attributes map of the remote config product state.
     */
    public static void overrideAttributes(Map<String, ?> attributes) {
        if (attributes == null) return;

        for (OverrideAttribute override : PREMIUM_OVERRIDES) {
            try {
                Object attribute = attributes.get(override.key);
                if (attribute == null) {
                    if (override.isExpected) {
                        Logger.error("Attribute " + override.key + " expected but not found", null);
                    }
                    continue;
                }

                Field valueField = Reflection.findField(attribute.getClass(), "value_");
                Object originalValue = valueField.get(attribute);
                if (override.overrideValue.equals(originalValue)) continue;

                Logger.info("Overriding account attribute " + override.key +
                        " from " + originalValue + " to " + override.overrideValue);
                valueField.set(attribute, override.overrideValue);
            } catch (Throwable ex) {
                Logger.error("overrideAttributes failure for " + override.key, ex);
            }
        }
    }

    /**
     * Injection point: a deserialized Google Assistant player context. Target URIs arrive as
     * {@code spotify:station:…}, which the client cannot play on demand.
     */
    public static void stripStationUris(Object context) {
        if (context == null) return;

        for (String name : new String[]{"uri", "url"}) {
            try {
                Field field = Reflection.findField(context.getClass(), name);
                Object value = field.get(context);
                if (value instanceof String && ((String) value).contains("spotify:station:")) {
                    field.set(context, ((String) value).replace("spotify:station:", "spotify:"));
                }
            } catch (Throwable ignored) {
                // The field is not part of this app version's context model.
            }
        }
    }
}
