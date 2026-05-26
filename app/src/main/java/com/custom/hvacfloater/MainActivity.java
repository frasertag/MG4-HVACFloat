package com.custom.hvacfloater;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE_FOR_UPDATE = 41;
    private static final String CURRENT_VERSION = "0.5";
    private static final String PUBLISHED_VERSION = "Published version: " + CURRENT_VERSION;
    private static final String RELEASES_LATEST_URL = "https://api.github.com/repos/frasertag/MG4-HVACFloat/releases/latest";

    private SharedPreferences prefs;
    private TextView themeStatus;
    private TextView controlsStatus;
    private TextView autostartStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private UpdateInfo pendingUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(HvacTheme.PREFS, MODE_PRIVATE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().getDecorView().setSystemUiVisibility(5894);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(36, 24, 36, 24);
        root.setBackgroundColor(Color.rgb(16, 16, 20));
        int bgResId = getResources().getIdentifier("settings_bg", "drawable", getPackageName());
        if (bgResId != 0) {
            root.setBackgroundResource(bgResId);
        }

        TextView title = new TextView(this);
        title.setText("HVAC FLOAT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(54);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setShadowLayer(12f, 0f, 0f, 0xff30d158);
        try {
            title.setLetterSpacing(0.16f);
        } catch (Throwable ignored) {
        }
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(this);
        status.setText("Floating HVAC controls for MG4.");
        status.setTextColor(0xffcfcfd6);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(900, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 16, 0, 22);
        root.addView(status, statusParams);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.CENTER);
        root.addView(columns, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout leftColumn = makeColumn();
        LinearLayout middleColumn = makeColumn();
        LinearLayout rightColumn = makeColumn();
        columns.addView(leftColumn);
        columns.addView(middleColumn);
        columns.addView(rightColumn);

        Button permission = makeButton("Overlay Permission");
        permission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOverlaySettings();
            }
        });
        leftColumn.addView(permission);

        Button start = makeButton("Start HVAC Overlay");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(new Intent(MainActivity.this, OverlayService.class));
                Toast.makeText(MainActivity.this, "Starting HVAC overlay", Toast.LENGTH_SHORT).show();
            }
        });
        leftColumn.addView(start);

        Button stop = makeButton("Stop HVAC Overlay");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(MainActivity.this, OverlayService.class));
                Toast.makeText(MainActivity.this, "Stopped HVAC overlay", Toast.LENGTH_SHORT).show();
            }
        });
        leftColumn.addView(stop);

        Button autostart = makeButton("Autostart Mode");
        autostart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAutostartDialog();
            }
        });
        leftColumn.addView(autostart);
        autostartStatus = makeStatusText();
        leftColumn.addView(autostartStatus);
        updateAutostartStatus();

        Button updates = makeButton("Check Updates");
        updates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkForUpdates();
            }
        });
        leftColumn.addView(updates);

        Button controls = makeButton("Select Controls");
        controls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showControlsDialog();
            }
        });
        middleColumn.addView(controls);
        controlsStatus = makeStatusText();
        middleColumn.addView(controlsStatus);
        updateControlsStatus();

        Button theme = makeButton("Select Theme");
        theme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showThemeDialog();
            }
        });
        rightColumn.addView(theme);
        themeStatus = makeStatusText();
        rightColumn.addView(themeStatus);
        updateThemeStatus();

        FrameLayout shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView version = new TextView(this);
        version.setText(PUBLISHED_VERSION);
        version.setTextColor(Color.WHITE);
        version.setTextSize(22);
        version.setTypeface(Typeface.DEFAULT_BOLD);
        version.setShadowLayer(8f, 0f, 0f, 0xaa000000);
        version.setGravity(Gravity.RIGHT);
        FrameLayout.LayoutParams versionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT);
        versionParams.setMargins(0, 0, 36, 28);
        shell.addView(version, versionParams);

        setContentView(shell);
    }

    private LinearLayout makeColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(12, 0, 12, 0);
        column.setLayoutParams(params);
        return column;
    }

    private TextView makeStatusText() {
        TextView text = new TextView(this);
        text.setTextColor(0xffcfcfd6);
        text.setTextSize(18);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 6, 0, 0);
        text.setLayoutParams(params);
        return text;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(makeButtonBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(300, 72);
        params.setMargins(0, 12, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable makeButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xd9212120);
        drawable.setCornerRadius(4);
        drawable.setStroke(1, 0x99ffffff);
        return drawable;
    }

    private void showThemeDialog() {
        final String[] labels = new String[] {"TEXT", "ICON Set 1"};
        final String[] values = new String[] {HvacTheme.TEXT, HvacTheme.ICON_SET_1};
        String current = prefs.getString(HvacTheme.KEY_THEME, HvacTheme.TEXT);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
            @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                prefs.edit().putString(HvacTheme.KEY_THEME, values[which]).apply();
                updateThemeStatus();
                Toast.makeText(MainActivity.this, "Theme saved", Toast.LENGTH_SHORT).show();
                        dialogInterface.dismiss();
            }
                })
                .show();
    }

    private void showControlsDialog() {
        final String[] labels = new String[] {
                "Passenger Heat",
                "Temp",
                "Fan",
                "AUTO",
                "Air Loop",
                "Air Flow",
                "Defrost",
                "Steering Heat",
                "Driver Heat",
                "Settings"
        };
        final String[] keys = new String[] {
                HvacTheme.KEY_PASSENGER_HEAT,
                HvacTheme.KEY_TEMP,
                HvacTheme.KEY_FAN,
                HvacTheme.KEY_AUTO,
                HvacTheme.KEY_LOOP,
                HvacTheme.KEY_FLOW,
                HvacTheme.KEY_DEFROST,
                HvacTheme.KEY_STEERING_HEAT,
                HvacTheme.KEY_DRIVER_HEAT,
                HvacTheme.KEY_SETTINGS
        };
        final boolean[] checked = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            checked[i] = prefs.getBoolean(keys[i], true);
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Controls")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        SharedPreferences.Editor editor = prefs.edit();
                        for (int i = 0; i < keys.length; i++) {
                            editor.putBoolean(keys[i], checked[i]);
                        }
                        editor.apply();
                        updateControlsStatus();
                        Toast.makeText(MainActivity.this, "Controls saved", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAutostartDialog() {
        final String[] labels = new String[] {"Autostart Full", "Autostart Hidden", "Autostart Off"};
        final String[] values = new String[] {
                HvacTheme.AUTOSTART_FULL,
                HvacTheme.AUTOSTART_HIDDEN,
                HvacTheme.AUTOSTART_OFF
        };
        String current = prefs.getString(HvacTheme.KEY_AUTOSTART, HvacTheme.AUTOSTART_OFF);
        int selected = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Autostart")
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        prefs.edit().putString(HvacTheme.KEY_AUTOSTART, values[which]).apply();
                        updateAutostartStatus();
                        Toast.makeText(MainActivity.this, "Autostart saved", Toast.LENGTH_SHORT).show();
                        dialogInterface.dismiss();
                    }
                })
                .show();
    }

    private void updateThemeStatus() {
        if (themeStatus == null) return;
        String theme = prefs.getString(HvacTheme.KEY_THEME, HvacTheme.TEXT);
        String label;
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            label = "Theme: ICON Set 1";
        } else {
            label = "Theme: TEXT";
        }
        themeStatus.setText(label);
    }

    private void updateControlsStatus() {
        if (controlsStatus == null) return;
        int count = 0;
        String[] keys = new String[] {
                HvacTheme.KEY_PASSENGER_HEAT,
                HvacTheme.KEY_TEMP,
                HvacTheme.KEY_FAN,
                HvacTheme.KEY_AUTO,
                HvacTheme.KEY_LOOP,
                HvacTheme.KEY_FLOW,
                HvacTheme.KEY_DEFROST,
                HvacTheme.KEY_STEERING_HEAT,
                HvacTheme.KEY_DRIVER_HEAT,
                HvacTheme.KEY_SETTINGS
        };
        for (String key : keys) {
            if (prefs.getBoolean(key, true)) count++;
        }
        controlsStatus.setText("Controls: " + count + " selected");
    }

    private void updateAutostartStatus() {
        if (autostartStatus == null) return;
        String mode = prefs.getString(HvacTheme.KEY_AUTOSTART, HvacTheme.AUTOSTART_OFF);
        String label;
        if (HvacTheme.AUTOSTART_FULL.equals(mode)) {
            label = "Autostart: Full";
        } else if (HvacTheme.AUTOSTART_HIDDEN.equals(mode)) {
            label = "Autostart: Hidden";
        } else {
            label = "Autostart: Off";
        }
        autostartStatus.setText(label);
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable second) {
                Toast.makeText(this, "Settings unavailable", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkForUpdates() {
        Toast.makeText(this, "Checking for updates", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UpdateInfo info = fetchLatestRelease();
                    if (info == null || info.version == null || info.apkUrl == null) {
                        showToast("No downloadable update found");
                        return;
                    }
                    if (compareVersions(info.version, CURRENT_VERSION) <= 0) {
                        showToast("Already on latest version");
                        return;
                    }
                    showUpdateDialog(info);
                } catch (Throwable throwable) {
                    showToast("Update check failed");
                }
            }
        }).start();
    }

    private UpdateInfo fetchLatestRelease() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(RELEASES_LATEST_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MG4-HVACFloat");
            String json = readAll(connection.getInputStream());
            JSONObject root = new JSONObject(json);
            String tag = root.optString("tag_name", "");
            String version = normalizeVersion(tag);
            JSONArray assets = root.optJSONArray("assets");
            String apkUrl = null;
            String apkName = null;
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        apkName = name;
                        apkUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
            }
            if (version.length() == 0 || apkUrl == null) return null;
            return new UpdateInfo(version, apkName, apkUrl);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private void showUpdateDialog(final UpdateInfo info) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update available")
                        .setMessage("Version " + info.version + " is available. Download APK to Downloads?")
                        .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                downloadUpdate(info);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void downloadUpdate(UpdateInfo info) {
        pendingUpdate = info;
        if (needsStoragePermission()) {
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE_FOR_UPDATE);
            return;
        }
        startUpdateDownload(info);
    }

    private boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT >= 23
                && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    private void startUpdateDownload(UpdateInfo info) {
        try {
            String fileName = info.apkName != null ? info.apkName : "MG4-HVACFloat-V" + info.version + ".apk";
            Toast.makeText(this, "Downloading update to Downloads", Toast.LENGTH_LONG).show();
            downloadApkManually(info.apkUrl, fileName);
        } catch (Throwable throwable) {
            showDownloadFallback(info);
        }
    }

    private void downloadApkManually(final String apkUrl, final String fileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                InputStream input = null;
                FileOutputStream output = null;
                try {
                    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloads.exists() && !downloads.mkdirs()) {
                        throw new IllegalStateException("Downloads folder unavailable");
                    }
                    File target = new File(downloads, fileName);
                    connection = openDownloadConnection(apkUrl, 0);
                    input = connection.getInputStream();
                    output = new FileOutputStream(target);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                    showToast("Downloaded to Downloads");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            openDownloads();
                        }
                    });
                } catch (Throwable throwable) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (pendingUpdate != null) {
                                showDownloadFallback(pendingUpdate);
                            } else {
                                Toast.makeText(MainActivity.this, "Download failed", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } finally {
                    try {
                        if (output != null) output.close();
                    } catch (Throwable ignored) {
                    }
                    try {
                        if (input != null) input.close();
                    } catch (Throwable ignored) {
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private HttpURLConnection openDownloadConnection(String url, int redirects) throws Exception {
        if (redirects > 5) {
            throw new IllegalStateException("Too many redirects");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "MG4-HVACFloat");
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307
                || code == 308) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.length() == 0) {
                throw new IllegalStateException("Redirect missing location");
            }
            return openDownloadConnection(location, redirects + 1);
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        return connection;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE_FOR_UPDATE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingUpdate != null) {
                startUpdateDownload(pendingUpdate);
            } else if (pendingUpdate != null) {
                showDownloadFallback(pendingUpdate);
            }
        }
    }

    private void showDownloadFallback(final UpdateInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("Download failed")
                .setMessage("The system download manager refused the APK download. Open the GitHub download link instead?")
                .setPositiveButton("Open Link", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        openUpdateLink(info);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openUpdateLink(UpdateInfo info) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl));
            startActivity(intent);
        } catch (Throwable throwable) {
            Toast.makeText(this, "No app can open update link", Toast.LENGTH_LONG).show();
        }
    }

    private void openDownloads() {
        try {
            Intent intent = new Intent("android.intent.action.VIEW_DOWNLOADS");
            startActivity(intent);
        } catch (Throwable first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
            }
        }
    }

    private void showToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String normalizeVersion(String value) {
        if (value == null) return "";
        value = value.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            return value.substring(1);
        }
        return value;
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            int l = i < leftParts.length ? parsePart(leftParts[i]) : 0;
            int r = i < rightParts.length ? parsePart(rightParts[i]) : 0;
            if (l != r) return l < r ? -1 : 1;
        }
        return 0;
    }

    private int parsePart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static final class UpdateInfo {
        final String version;
        final String apkName;
        final String apkUrl;

        UpdateInfo(String version, String apkName, String apkUrl) {
            this.version = version;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
        }
    }
}
