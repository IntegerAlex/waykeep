package com.waykeep.app.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.waykeep.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GoalWidgetLayoutTest {

    @Test
    public void widgetFitsTwoByTwoWithWorstCaseContent() {
        Context context = ApplicationProvider.getApplicationContext();
        View root = LayoutInflater.from(context).inflate(R.layout.widget_goal, null);

        ((TextView) root.findViewById(R.id.widget_goal_distance)).setText("33.7 km");
        ((TextView) root.findViewById(R.id.widget_goal_percent)).setText("100% of 20.0 km");
        ((TextView) root.findViewById(R.id.widget_goal_streak)).setText("3 active weeks in a row");

        int size = dp(context, 110);
        int exact = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
        root.measure(exact, exact);
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());

        assertTrue("widget exceeds 2x2 height: " + root.getMeasuredHeight() + "px",
                root.getMeasuredHeight() <= size);
        assertNoEllipsis(root, R.id.widget_goal_label);
        assertNoEllipsis(root, R.id.widget_goal_distance);
        assertNoEllipsis(root, R.id.widget_goal_percent);
        assertNoEllipsis(root, R.id.widget_goal_streak);
        assertInside(root, R.id.widget_goal_label);
        assertInside(root, R.id.widget_goal_distance);
        assertInside(root, R.id.widget_goal_percent);
        assertInside(root, R.id.widget_goal_streak);
    }

    private static void assertInside(View root, int id) {
        View view = root.findViewById(id);
        assertTrue("view starts above widget: " + id, view.getTop() >= 0);
        assertTrue("view extends past widget: " + id + " bottom=" + view.getBottom(),
                view.getBottom() <= root.getMeasuredHeight());
    }

    private static void assertNoEllipsis(View root, int id) {
        TextView view = root.findViewById(id);
        assertTrue(view.getText().length() > 0);
        assertEquals("clipped: " + view.getText(), 0, view.getLayout().getEllipsisCount(0));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
