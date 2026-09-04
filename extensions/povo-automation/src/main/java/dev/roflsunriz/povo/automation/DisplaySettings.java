package dev.roflsunriz.povo.automation;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

final class DisplaySettings {
    private DisplaySettings() {}

    private static String text(String ja, String en) {
        return "ja".equals(Locale.getDefault().getLanguage()) ? ja : en;
    }

    static void attach(Activity activity, LinearLayout root) {
        AutomationState state = Automation.requireState();
        TextView guide = new TextView(activity);
        guide.setText(text("CYD表示用API\nPCのHTTPS送信先と書き込み専用トークンを設定します。期限・回数・更新状態だけを送信します。バックグラウンド送信は15分以上の間隔で、Androidの省電力設定により遅れる場合があります。",
                "CYD display API\nSet the PC HTTPS endpoint and write-only token. Only expiry, counts and renewal state are sent. Background sync runs at intervals of at least 15 minutes and may be delayed by Android power management."));
        root.addView(guide);
        EditText endpoint = new EditText(activity);
        endpoint.setHint("https://PC/api/v1/status");
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setSingleLine(true);
        endpoint.setText(state.displayEndpoint());
        root.addView(endpoint);
        EditText token = new EditText(activity);
        token.setHint(text("書き込みトークン（保存済みなら空欄で保持）", "Write token (leave blank to keep saved token)"));
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSingleLine(true);
        root.addView(token);
        TextView result = new TextView(activity);
        root.addView(result);
        Runnable refresh = () -> {
            SharedPreferences preferences = activity.getSharedPreferences("povo_display_sync_result", Context.MODE_PRIVATE);
            long last = preferences.getLong("last_success", 0);
            String error = preferences.getString("error", "");
            String failure = error.isEmpty() ? "" : String.format(text(
                    "\n送信失敗（PCの起動・URL・証明書・トークンを確認）: %s",
                    "\nSync failed (check PC, URL, certificate and token): %s"), error);
            result.setText(String.format(text("送信: %s\n最終成功: %s%s", "Sync: %s\nLast success: %s%s"),
                    state.displayEnabled() ? text("有効", "enabled") : text("無効", "disabled"),
                    last == 0 ? text("未送信", "Never") : DateFormat.getDateTimeInstance().format(new Date(last)), failure));
        };
        Button save = new Button(activity);
        save.setText(text("保存して送信", "Save and sync"));
        save.setOnClickListener(view -> {
            if (!state.configureDisplay(endpoint.getText().toString(), token.getText().toString())) {
                endpoint.setError(text("正しいHTTPS URLと32〜256文字の英数字・_・-のトークンを指定してください。安全に保存できない場合も失敗します。",
                        "Use a valid HTTPS URL and a 32–256 character token (letters, digits, _ or -). Secure storage must be available."));
                return;
            }
            token.setText("");
            DisplaySync.schedule(activity);
            DisplaySync.request();
            refresh.run();
        });
        root.addView(save);
        Button disable = new Button(activity);
        disable.setText(text("送信を停止して接続設定を削除", "Stop sync and remove connection settings"));
        disable.setOnClickListener(view -> {
            state.disableDisplay();
            DisplaySync.schedule(activity);
            endpoint.setText("");
            token.setText("");
            refresh.run();
        });
        root.addView(disable);
        Button status = new Button(activity);
        status.setText(text("送信結果を確認", "Refresh sync result"));
        status.setOnClickListener(view -> refresh.run());
        root.addView(status);
        refresh.run();
    }
}
