package com.waykeep.app.ui.trackmap;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import com.waykeep.app.R;

/**
 * Selectable color of the recorded route line on the map.
 * <p>
 * The preference stores the enum name (see {@code route_color_values}); an unknown or missing
 * value falls back to the brand cyan so older installs keep their current look.
 */
public enum RouteColor {
    CYAN(R.color.route_color_cyan),
    GREEN(R.color.route_color_green),
    ORANGE(R.color.route_color_orange),
    MAGENTA(R.color.route_color_magenta),
    BLUE(R.color.route_color_blue),
    WHITE(R.color.route_color_white);

    private final int colorRes;

    RouteColor(@ColorRes int colorRes) {
        this.colorRes = colorRes;
    }

    /**
     * Maps a stored preference value to a color. Null, empty and unknown values yield {@link #CYAN}.
     */
    public static RouteColor fromPreference(@Nullable String value) {
        for (RouteColor color : values()) {
            if (color.name().equalsIgnoreCase(value)) {
                return color;
            }
        }
        return CYAN;
    }

    @ColorRes
    public int colorRes() {
        return colorRes;
    }
}
