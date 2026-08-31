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
    private EditText expiryInput;
    private EditText maxUsesInput;
    private EditText currentUseInput;
    private EditText durationHoursInput;
    private Button primaryButton;
    private Button toggleButton;
    private Button clearButton;
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

        TextView guide = new TextView(this);
        guide.setText(Strings.setupGuide());
        guide.setTextSize(16f);
        guide.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams guideParams = matchWrap();
        guideParams.topMargin = dp(14);
        root.addView(guide, guideParams);

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

        AutomationState initialState = Automation.requireState();
        LinearLayout useCounts = new LinearLayout(this);
        useCounts.setOrientation(LinearLayout.HORIZONTAL);
        useCounts.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        LinearLayout.LayoutParams countsParams = matchWrap();
        countsParams.topMargin = dp(12);
        root.addView(useCounts, countsParams);

        maxUsesInput = new EditText(this);
        maxUsesInput.setHint(Strings.maxUsesHint());
        maxUsesInput.setText(String.valueOf(initialState.maxUses()));
        maxUsesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        useCounts.addView(maxUsesInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        currentUseInput = new EditText(this);
        currentUseInput.setHint(Strings.currentUseHint());
        currentUseInput.setText(String.valueOf(initialState.appliedUses()));
        currentUseInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        currentParams.setMarginStart(dp(8));
        useCounts.addView(currentUseInput, currentParams);

        durationHoursInput = new EditText(this);
        durationHoursInput.setHint(Strings.durationHoursHint());
        durationHoursInput.setText(String.valueOf(initialState.durationHours()));
        durationHoursInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams durationParams = matchWrap();
        durationParams.topMargin = dp(12);
        root.addView(durationHoursInput, durationParams);

        expiryInput = new EditText(this);
        expiryInput.setHint(Strings.expiryInputHint());
        expiryInput.setSingleLine(true);
        expiryInput.setInputType(InputType.TYPE_CLASS_DATETIME);
        LinearLayout.LayoutParams expiryParams = matchWrap();
        expiryParams.topMargin = dp(12);
        root.addView(expiryInput, expiryParams);

        primaryButton = addButton(Strings.save(), view -> saveAndEnable());
        toggleButton = addButton("", view -> {
            AutomationState state = Automation.requireState();
            if (state.code() != null) Automation.setEnabled(!state.enabled());
            refresh();
        });
        clearButton = addButton(Strings.clear(), view -> new AlertDialog.Builder(this)
                .setTitle(Strings.clear())
                .setMessage(Strings.clear())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    AlarmScheduler.cancel(this);
                    Automation.requireState().clear();
                    input.setText("");
                    expiryInput.setText("");
                    maxUsesInput.setText("24");
                    currentUseInput.setText("0");
                    durationHoursInput.setText("168");
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
        text.append("\n").append(state.enabled() ? Strings.automationEnabled() : Strings.automationPaused());
        if (state.currentExpiry() > 0L) {
            text.append("\n")
                    .append(DateFormat.getDateTimeInstance().format(new Date(state.currentExpiry())));
        }
        text.append("\n").append(Strings.usesProgress(state.appliedUses(), state.maxUses()));
        text.append(" · ").append(Strings.durationPerUse(state.durationHours()));
        text.append("\n").append(Strings.successCount(state.successCount()));
        if (!state.lastStatus().isEmpty()) text.append("\n").append(state.lastStatus());
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!alarms.canScheduleExactAlarms()) text.append("\n").append(Strings.exactAlarmRequired());
        }
        status.setText(text.toString());
        toggleButton.setText(state.enabled() ? Strings.disable() : Strings.enable());
        boolean configured = code != null;
        primaryButton.setText(configured ? Strings.updateSettings() : Strings.save());
        toggleButton.setVisibility(configured ? View.VISIBLE : View.GONE);
        clearButton.setVisibility(configured ? View.VISIBLE : View.GONE);
    }

    private void saveAndEnable() {
        boolean valid = Automation.setUseProgress(
                maxUsesInput.getText().toString(),
                currentUseInput.getText().toString(),
                durationHoursInput.getText().toString()
        );
        if (!valid) {
            currentUseInput.setError(Strings.invalidUseProgress());
            return;
        }

        AutomationState state = Automation.requireState();
        String expiryText = expiryInput.getText().toString().trim();
        if (!expiryText.isEmpty() && !Automation.setManualExpiry(expiryText)) {
            expiryInput.setError(Strings.invalidExpiry());
            return;
        }
        if (state.currentExpiry() <= System.currentTimeMillis()) {
            expiryInput.setError(Strings.invalidExpiry());
            return;
        }

        String raw = input.getText().toString();
        if (!raw.trim().isEmpty()) {
            String extracted = Automation.preparePromoInput(raw);
            if (!extracted.equals(raw.trim())) input.setHint(mask(extracted));
        }
        if (state.code() == null) {
            input.setError(Strings.pasteHint());
            return;
        }

        input.setText("");
        expiryInput.setText("");
        Automation.setEnabled(true);
        refresh();
        requestExactAlarm();
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarms.canScheduleExactAlarms()) return;
        Intent intent = new Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private Button addButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        root.addView(button, params);
        return button;
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
