package com.waykeep.app.ui.widget;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * RemoteViews layouts may only use the framework's whitelisted view classes; anything
 * else fails to inflate in the launcher and the widget shows "Can't load widget"
 * (seen live: android.widget.Space in widget_goal.xml).
 * Source: https://developer.android.com/develop/ui/views/appwidgets/layout
 */
public class WidgetLayoutsWhitelistTest {

    private static final Set<String> ALLOWED = Set.of(
            "linearlayout", "framelayout", "relativelayout", "gridlayout",
            "textview", "imageview", "imagebutton", "button", "progressbar",
            "chronometer", "analogclock", "textclock", "viewflipper",
            "listview", "gridview", "stackview", "adapterviewflipper", "viewstub");

    private static final List<String> WIDGET_LAYOUTS = List.of(
            "widget_goal", "widget_stats");

    @Test
    public void widgetLayoutsOnlyUseRemoteViewsWhitelistedViews() throws Exception {
        for (String layout : WIDGET_LAYOUTS) {
            File file = new File("src/main/res/layout/" + layout + ".xml");
            assertTrue(layout + " layout file missing", file.isFile());
            Element root = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(file).getDocumentElement();
            checkTree(layout, root);
        }
    }

    private void checkTree(String layout, Element element) {
        String tag = element.getTagName().substring(element.getTagName().indexOf(':') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(tag)) {
            throw new AssertionError(layout + " uses <" + element.getTagName()
                    + ">, which RemoteViews cannot inflate (widget shows \"Can't load widget\")");
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                checkTree(layout, (Element) child);
            }
        }
    }
}
