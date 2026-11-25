package com.example.lovanta;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

final class BottomNavigation {

    private static final int[] TAB_IDS = {
            R.id.navChat, R.id.navVip, R.id.navData, R.id.navTask, R.id.navMe
    };
    private static final Class<?>[] DESTINATIONS = {
            MainActivity.class, VipActivity.class, DataActivity.class, TaskActivity.class, MeActivity.class
    };

    private BottomNavigation() {}

    static void bind(Activity activity, int selectedTabId) {
        for (int i = 0; i < TAB_IDS.length; i++) {
            View tab = activity.findViewById(TAB_IDS[i]);
            boolean selected = TAB_IDS[i] == selectedTabId;
            int color = activity.getResources().getColor(selected
                    ? R.color.lovanta_pink : R.color.lovanta_gray);
            ImageView icon = (ImageView) ((android.view.ViewGroup) tab).getChildAt(0);
            TextView label = (TextView) ((android.view.ViewGroup) tab).getChildAt(1);
            icon.setColorFilter(color);
            label.setTextColor(color);
            label.setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            final Class<?> destination = DESTINATIONS[i];
            tab.setOnClickListener(v -> {
                if (destination == activity.getClass()) {
                    return;
                }
                Intent intent = new Intent(activity, destination);
                if (destination == MainActivity.class) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(0, 0);
                } else {
                    activity.startActivity(intent);
                    activity.overridePendingTransition(0, 0);
                    activity.finish();
                    activity.overridePendingTransition(0, 0);
                }
            });
        }
    }
}
