package com.custom.hvacfloater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView themeStatus;
    private TextView controlsStatus;
    private TextView autostartStatus;

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

        setContentView(root);
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
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(300, 72);
        params.setMargins(0, 12, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void showThemeDialog() {
        final String[] labels = new String[] {"TEXT", "ICON Set 1", "ICON Set 2"};
        final String[] values = new String[] {HvacTheme.TEXT, HvacTheme.ICON_SET_1, HvacTheme.ICON_SET_2};
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
        } else if (HvacTheme.ICON_SET_2.equals(theme)) {
            label = "Theme: ICON Set 2";
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
}
