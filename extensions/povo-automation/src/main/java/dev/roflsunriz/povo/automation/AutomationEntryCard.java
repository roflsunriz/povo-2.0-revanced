package dev.roflsunriz.povo.automation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

final class AutomationEntryCard {
    private static final String TAG = "povo-promo-automation-entry";

    private AutomationEntryCard() {}

    static void attach(Activity activity) {
        Log.d("povo-automation", "Resumed activity: " + activity.getClass().getName());
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        if (findByResourceEntryName(content, "bottomNavigationView") == null) return;

        View existing = content.findViewWithTag(TAG);
        if (existing instanceof LinearLayout) {
            update((LinearLayout) existing);
            return;
        }

        LinearLayout card = new LinearLayout(activity);
        card.setTag(TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 18), dp(activity, 12), dp(activity, 18), dp(activity, 12));
        card.setElevation(dp(activity, 8));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(Strings.settingsTitle());

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 242, 0));
        background.setCornerRadius(dp(activity, 18));
        background.setStroke(dp(activity, 1), Color.rgb(35, 35, 35));
        card.setBackground(background);

        TextView title = new TextView(activity);
        title.setText(Strings.settingsTitle());
        title.setTextColor(Color.BLACK);
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(activity);
        summary.setTag("summary");
        summary.setTextColor(Color.rgb(35, 35, 35));
        summary.setTextSize(14f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = dp(activity, 4);
        card.addView(summary, summaryParams);

        card.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, AutomationSettingsActivity.class)
        ));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        params.setMargins(dp(activity, 14), 0, dp(activity, 14), dp(activity, 92));
        content.addView(card, params);
        Log.i("povo-automation", "Home automation entry attached");
        update(card);
    }

    private static View findByResourceEntryName(View view, String expectedName) {
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                if (expectedName.equals(view.getResources().getResourceEntryName(id))) return view;
            } catch (android.content.res.Resources.NotFoundException ignored) {
                // Continue through children whose IDs come from another resource package.
            }
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View result = findByResourceEntryName(group.getChildAt(index), expectedName);
            if (result != null) return result;
        }
        return null;
    }

    private static void update(LinearLayout card) {
        TextView summary = card.findViewWithTag("summary");
        if (summary == null) return;
        AutomationState state = Automation.requireState();
        String code = state.code();
        if (code == null) {
            summary.setText(Strings.tapToRegister());
            return;
        }

        String status = state.hasRemainingUses()
                ? (state.enabled() ? Strings.automationEnabled() : Strings.automationPaused())
                : Strings.allUsesCompleted();
        status += " · " + Strings.usesProgress(state.appliedUses(), state.maxUses());
        status += " · " + Strings.durationPerUse(state.durationHours());
        long expiry = state.currentExpiry();
        if (expiry > 0L) {
            status += " · " + Strings.nextAt(DateFormat.getDateTimeInstance().format(new Date(expiry)));
        } else {
            status += " · " + Strings.expiryNotDetected();
        }
        summary.setText(status);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
