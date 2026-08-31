package dev.roflsunriz.povo.automation;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public final class AutomationSettingsActivity extends Activity {
    private TextView status;
    private EditText input;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Automation.initialize(getApplication());
        setTitle(Strings.settingsTitle());

        ScrollView scrollView = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText(Strings.settingsTitle());
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        status = new TextView(this);
        status.setTextSize(16f);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(16);
        root.addView(status, statusParams);

        input = new EditText(this);
        input.setHint(Strings.pasteHint());
        input.setMinLines(6);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(20);
        root.addView(input, inputParams);

        addButton(Strings.save(), view -> {
            String raw = input.getText().toString();
            String extracted = Automation.preparePromoInput(raw);
            input.setText("");
            if (!extracted.equals(raw.trim())) input.setHint(mask(extracted));
            refresh();
        });
        addButton(Strings.enable(), view -> {
            AutomationState state = Automation.requireState();
            if (state.code() != null) state.setEnabled(true);
            refresh();
        });
        addButton(Strings.disable(), view -> {
            Automation.requireState().setEnabled(false);
            AlarmScheduler.cancel(this);
            refresh();
        });
        addButton(Strings.requestExact(), view -> requestExactAlarm());
        addButton(Strings.clear(), view -> new AlertDialog.Builder(this)
                .setTitle(Strings.clear())
                .setMessage(Strings.clear())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    AlarmScheduler.cancel(this);
                    Automation.requireState().clear();
                    refresh();
                })
                .show());

        setContentView(scrollView);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refresh();
    }

    private void refresh() {
        AutomationState state = Automation.requireState();
        String code = state.code();
        StringBuilder text = new StringBuilder();
        text.append(code == null ? Strings.noCode() : mask(code));
        text.append("\n").append(state.enabled() ? Strings.enable() : Strings.disable());
        if (state.currentExpiry() > 0L) {
            text.append("\n")
                    .append(DateFormat.getDateTimeInstance().format(new Date(state.currentExpiry())));
        }
        text.append("\n").append(state.successCount());
        if (!state.lastStatus().isEmpty()) text.append("\n").append(state.lastStatus());
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!alarms.canScheduleExactAlarms()) text.append("\n").append(Strings.exactAlarmRequired());
        }
        status.setText(text.toString());
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return;
        Intent intent = new Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void addButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        root.addView(button, params);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String mask(String code) {
        if (code == null || code.length() < 6) return "••••••";
        return code.substring(0, 3) + "••••" + code.substring(code.length() - 3);
    }
}
