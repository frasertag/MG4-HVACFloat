package com.custom.hvacfloater;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.app.Dialog;
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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private static final String CURRENT_VERSION = "0.5.4";
    private static final String PUBLISHED_VERSION = "Published version: " + CURRENT_VERSION;
    private static final String RELEASES_LATEST_URL = "https://api.github.com/repos/frasertag/MG4-HVACFloat/releases/latest";

    private SharedPreferences prefs;
    private TextView themeStatus;
    private TextView overlayModeStatus;
    private TextView barStyleStatus;
    private TextView handleStyleStatus;
    private TextView controlsStatus;
    private TextView autostartStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private UpdateInfo pendingUpdate;
    private boolean updatePromptShowing;

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
                checkForUpdates(true);
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

        Button overlayMode = makeButton("Overlay Mode");
        overlayMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showOverlayModeDialog();
            }
        });
        rightColumn.addView(overlayMode);
        overlayModeStatus = makeStatusText();
        rightColumn.addView(overlayModeStatus);
        updateOverlayModeStatus();

        Button barStyle = makeButton("Bar Colour");
        barStyle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBarStyleDialog();
            }
        });
        rightColumn.addView(barStyle);
        barStyleStatus = makeStatusText();
        rightColumn.addView(barStyleStatus);
        updateBarStyleStatus();

        Button handleStyle = makeButton("HVAC Icon Style");
        handleStyle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showHandleStyleDialog();
            }
        });
        rightColumn.addView(handleStyle);
        handleStyleStatus = makeStatusText();
        rightColumn.addView(handleStyleStatus);
        updateHandleStyleStatus();

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
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkForUpdates(false);
            }
        }, 900);
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
        final int[] selected = new int[] {0};
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected[0] = i;
                break;
            }
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = makeStyledDialogPanel("SELECT THEME", "Overlay button style");

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(20, 12, 20, 12);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final Button option = makeUpdateDialogButton(labels[i]);
            option.setTextSize(20);
            option.setBackground(makeUpdatePanelBackground(
                    index == selected[0] ? 0xe055f0d8 : 0xd9212120,
                    8,
                    0x99ffffff));
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.edit().putString(HvacTheme.KEY_THEME, values[index]).apply();
                    updateThemeStatus();
                    refreshOverlayMode();
                    Toast.makeText(MainActivity.this, "Theme saved", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    72);
            optionParams.setMargins(0, 8, 0, 8);
            choices.addView(option, optionParams);
        }
        panel.addView(choices);

        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(cancel);
        panel.addView(buttons);

        showStyledDialog(dialog, panel, 560);
    }

    private void showOverlayModeDialog() {
        final String[] labels = new String[] {"Bar", "Factory HVAC"};
        final String[] values = new String[] {
                HvacTheme.OVERLAY_MODE_BAR,
                HvacTheme.OVERLAY_MODE_FACTORY_HVAC
        };
        String current = prefs.getString(HvacTheme.KEY_OVERLAY_MODE, HvacTheme.OVERLAY_MODE_BAR);
        final int[] selected = new int[] {0};
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected[0] = i;
                break;
            }
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = makeStyledDialogPanel("OVERLAY MODE", "Choose the floating HVAC behaviour");

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(20, 12, 20, 12);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final Button option = makeUpdateDialogButton(labels[i]);
            option.setTextSize(20);
            option.setBackground(makeUpdatePanelBackground(
                    index == selected[0] ? 0xe055f0d8 : 0xd9212120,
                    8,
                    0x99ffffff));
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.edit().putString(HvacTheme.KEY_OVERLAY_MODE, values[index]).apply();
                    updateOverlayModeStatus();
                    refreshOverlayMode();
                    Toast.makeText(MainActivity.this, "Overlay mode saved", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    72);
            optionParams.setMargins(0, 8, 0, 8);
            choices.addView(option, optionParams);
        }
        panel.addView(choices);

        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(cancel);
        panel.addView(buttons);

        showStyledDialog(dialog, panel, 620);
    }

    private void showBarStyleDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int[] rgb = parseColorParts(getBarColor());
        final int[] opacity = new int[] {getBarOpacity()};
        final boolean[] updating = new boolean[] {false};

        LinearLayout panel = makeStyledDialogPanel("BAR COLOUR", "Expanded bar and hidden handle");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 18, 20, 18);

        final View preview = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                72);
        previewParams.setMargins(0, 0, 0, 18);
        layout.addView(preview, previewParams);

        final EditText hexInput = new EditText(this);
        hexInput.setSingleLine(true);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setText(formatHex(rgb[0], rgb[1], rgb[2]));
        hexInput.setSelectAllOnFocus(true);
        hexInput.setTextColor(Color.WHITE);
        hexInput.setTextSize(20);
        hexInput.setTypeface(Typeface.DEFAULT_BOLD);
        hexInput.setGravity(Gravity.CENTER);
        hexInput.setBackground(makeUpdatePanelBackground(0xd9212120, 8, 0x99ffffff));
        hexInput.setPadding(10, 0, 10, 0);
        layout.addView(hexInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                64));

        final TextView redLabel = makeDarkDialogLabel("");
        final SeekBar red = makeColorSeekBar(rgb[0]);
        layout.addView(redLabel);
        layout.addView(red);

        final TextView greenLabel = makeDarkDialogLabel("");
        final SeekBar green = makeColorSeekBar(rgb[1]);
        layout.addView(greenLabel);
        layout.addView(green);

        final TextView blueLabel = makeDarkDialogLabel("");
        final SeekBar blue = makeColorSeekBar(rgb[2]);
        layout.addView(blueLabel);
        layout.addView(blue);

        final TextView opacityLabel = makeDarkDialogLabel("");
        final SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(100);
        opacitySeek.setProgress(opacity[0]);
        layout.addView(opacityLabel);
        layout.addView(opacitySeek);

        final Runnable updatePreview = new Runnable() {
            @Override
            public void run() {
                redLabel.setText("Red: " + rgb[0]);
                greenLabel.setText("Green: " + rgb[1]);
                blueLabel.setText("Blue: " + rgb[2]);
                opacityLabel.setText("Opacity: " + opacity[0] + "%");
                preview.setBackground(makePreviewBackground(
                        composeColor(rgb[0], rgb[1], rgb[2], opacity[0]),
                        10,
                        0x99ffffff));
            }
        };
        updatePreview.run();

        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showColorPickerDialog(rgb, hexInput, red, green, blue, updatePreview, updating);
            }
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (updating[0]) {
                    return;
                }
                int[] parsed = parseColorParts(editable.toString());
                if (parsed == null) {
                    return;
                }
                updating[0] = true;
                rgb[0] = parsed[0];
                rgb[1] = parsed[1];
                rgb[2] = parsed[2];
                red.setProgress(rgb[0]);
                green.setProgress(rgb[1]);
                blue.setProgress(rgb[2]);
                updatePreview.run();
                updating[0] = false;
            }
        };
        hexInput.addTextChangedListener(watcher);

        SeekBar.OnSeekBarChangeListener colorListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updating[0]) {
                    return;
                }
                rgb[0] = red.getProgress();
                rgb[1] = green.getProgress();
                rgb[2] = blue.getProgress();
                updating[0] = true;
                hexInput.setText(formatHex(rgb[0], rgb[1], rgb[2]));
                hexInput.setSelection(hexInput.getText().length());
                updatePreview.run();
                updating[0] = false;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        red.setOnSeekBarChangeListener(colorListener);
        green.setOnSeekBarChangeListener(colorListener);
        blue.setOnSeekBarChangeListener(colorListener);
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacity[0] = progress;
                updatePreview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                480));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        Button reset = makeUpdateDialogButton("Reset");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit()
                        .putString(HvacTheme.KEY_BAR_COLOR, HvacTheme.DEFAULT_BAR_COLOR)
                        .putInt(HvacTheme.KEY_BAR_OPACITY, HvacTheme.DEFAULT_BAR_OPACITY)
                        .apply();
                updateBarStyleStatus();
                refreshOverlayBarStyle();
                Toast.makeText(MainActivity.this, "Bar colour reset", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        Button save = makeUpdateDialogButton("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int[] parsed = parseColorParts(hexInput.getText().toString());
                if (parsed != null) {
                    rgb[0] = parsed[0];
                    rgb[1] = parsed[1];
                    rgb[2] = parsed[2];
                }
                prefs.edit()
                        .putString(HvacTheme.KEY_BAR_COLOR, formatHex(rgb[0], rgb[1], rgb[2]))
                        .putInt(HvacTheme.KEY_BAR_OPACITY, opacity[0])
                        .apply();
                updateBarStyleStatus();
                refreshOverlayBarStyle();
                Toast.makeText(MainActivity.this, "Bar colour saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        buttons.addView(cancel);
        buttons.addView(reset);
        buttons.addView(save);
        panel.addView(buttons);

        showStyledDialog(dialog, panel, 760);
    }

    private void showHandleStyleDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int[] width = new int[] {getHandleWidth()};
        final int[] height = new int[] {getHandleHeight()};
        final int[] radius = new int[] {getHandleRadius()};

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(34, 28, 34, 26);
        panel.setBackground(makeUpdatePanelBackground(0xf2222224, 18, 0xff55f0d8));

        TextView title = new TextView(this);
        title.setText("HVAC ICON STYLE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(8f, 0f, 0f, 0xaa000000);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Hidden and factory HVAC handle");
        subtitle.setTextColor(0xff55f0d8);
        subtitle.setTextSize(17);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, 8, 0, 18);
        panel.addView(subtitle, subtitleParams);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 18, 20, 18);

        FrameLayout previewShell = new FrameLayout(this);
        previewShell.setPadding(18, 18, 18, 18);
        previewShell.setBackground(makeUpdatePanelBackground(0xa9141418, 12, 0x55ffffff));
        final View preview = new View(this);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(width[0], height[0], Gravity.CENTER);
        previewShell.addView(preview, previewParams);
        LinearLayout.LayoutParams previewShellParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                230);
        previewShellParams.setMargins(0, 0, 0, 18);
        layout.addView(previewShell, previewShellParams);

        final CheckBox visible = new CheckBox(this);
        visible.setText("Show background box");
        visible.setTextColor(Color.WHITE);
        visible.setTextSize(18);
        visible.setTypeface(Typeface.DEFAULT_BOLD);
        visible.setChecked(isHandleBackgroundVisible());
        layout.addView(visible);

        final CheckBox textVisible = new CheckBox(this);
        textVisible.setText("Show text");
        textVisible.setTextColor(Color.WHITE);
        textVisible.setTextSize(18);
        textVisible.setTypeface(Typeface.DEFAULT_BOLD);
        textVisible.setChecked(isHandleTextVisible());
        layout.addView(textVisible);

        final TextView widthLabel = makeDarkDialogLabel("");
        final SeekBar widthSeek = new SeekBar(this);
        widthSeek.setMax(584);
        widthSeek.setProgress(width[0] - 56);
        layout.addView(widthLabel);
        layout.addView(widthSeek);

        final TextView heightLabel = makeDarkDialogLabel("");
        final SeekBar heightSeek = new SeekBar(this);
        heightSeek.setMax(304);
        heightSeek.setProgress(height[0] - 56);
        layout.addView(heightLabel);
        layout.addView(heightSeek);

        final TextView radiusLabel = makeDarkDialogLabel("");
        final SeekBar radiusSeek = new SeekBar(this);
        radiusSeek.setMax(120);
        radiusSeek.setProgress(radius[0]);
        layout.addView(radiusLabel);
        layout.addView(radiusSeek);

        final Runnable updatePreview = new Runnable() {
            @Override
            public void run() {
                widthLabel.setText("Width: " + width[0] + "dp");
                heightLabel.setText("Height: " + height[0] + "dp");
                radiusLabel.setText("Corners: " + radius[0] + "dp");

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) preview.getLayoutParams();
                params.width = width[0];
                params.height = height[0];
                params.gravity = Gravity.CENTER;
                preview.setLayoutParams(params);

                LinearLayout.LayoutParams shellParams = (LinearLayout.LayoutParams) previewShell.getLayoutParams();
                shellParams.height = Math.max(230, height[0] + 36);
                previewShell.setLayoutParams(shellParams);

                if (visible.isChecked()) {
                    preview.setBackground(makePreviewBackground(
                            composeColorFromHex(getBarColor(), getBarOpacity()),
                            radius[0],
                            0x99ffffff));
                } else {
                    preview.setBackground(makePreviewBackground(0x00000000, radius[0], 0x22ffffff));
                }
            }
        };
        updatePreview.run();

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                width[0] = widthSeek.getProgress() + 56;
                height[0] = heightSeek.getProgress() + 56;
                radius[0] = radiusSeek.getProgress();
                updatePreview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        widthSeek.setOnSeekBarChangeListener(seekListener);
        heightSeek.setOnSeekBarChangeListener(seekListener);
        radiusSeek.setOnSeekBarChangeListener(seekListener);
        visible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updatePreview.run();
            }
        });
        textVisible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updatePreview.run();
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                430));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        Button reset = makeUpdateDialogButton("Reset");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit()
                        .putInt(HvacTheme.KEY_HANDLE_WIDTH, HvacTheme.DEFAULT_HANDLE_SIZE)
                        .putInt(HvacTheme.KEY_HANDLE_HEIGHT, HvacTheme.DEFAULT_HANDLE_SIZE)
                        .putInt(HvacTheme.KEY_HANDLE_RADIUS, HvacTheme.DEFAULT_HANDLE_RADIUS)
                        .putBoolean(HvacTheme.KEY_HANDLE_BACKGROUND_VISIBLE, true)
                        .putBoolean(HvacTheme.KEY_HANDLE_TEXT_VISIBLE, true)
                        .apply();
                updateHandleStyleStatus();
                refreshOverlayMode();
                Toast.makeText(MainActivity.this, "HVAC icon style reset", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        Button save = makeUpdateDialogButton("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit()
                        .putInt(HvacTheme.KEY_HANDLE_WIDTH, width[0])
                        .putInt(HvacTheme.KEY_HANDLE_HEIGHT, height[0])
                        .putInt(HvacTheme.KEY_HANDLE_RADIUS, radius[0])
                        .putBoolean(HvacTheme.KEY_HANDLE_BACKGROUND_VISIBLE, visible.isChecked())
                        .putBoolean(HvacTheme.KEY_HANDLE_TEXT_VISIBLE, textVisible.isChecked())
                        .apply();
                updateHandleStyleStatus();
                refreshOverlayMode();
                Toast.makeText(MainActivity.this, "HVAC icon style saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        buttons.addView(cancel);
        buttons.addView(reset);
        buttons.addView(save);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, 20, 0, 0);
        panel.addView(buttons, buttonParams);

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(920, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showControlsDialog() {
        final String[] labels = new String[] {
                "Passenger Heat",
                "Temp",
                "Fan",
                "Auto Fan",
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

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = makeStyledDialogPanel("SELECT CONTROLS", "Choose what appears on the floating bar");

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(20, 12, 20, 12);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            CheckBox box = new CheckBox(this);
            box.setText(labels[i]);
            box.setTextColor(Color.WHITE);
            box.setTextSize(19);
            box.setTypeface(Typeface.DEFAULT_BOLD);
            box.setChecked(checked[i]);
            box.setPadding(8, 8, 8, 8);
            box.setBackground(makeUpdatePanelBackground(0x66212120, 8, 0x33ffffff));
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    checked[index] = ((CheckBox) view).isChecked();
                }
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    58);
            rowParams.setMargins(0, 5, 0, 5);
            list.addView(box, rowParams);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                430));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        Button save = makeUpdateDialogButton("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor editor = prefs.edit();
                for (int i = 0; i < keys.length; i++) {
                    editor.putBoolean(keys[i], checked[i]);
                }
                editor.apply();
                updateControlsStatus();
                refreshOverlayMode();
                Toast.makeText(MainActivity.this, "Controls saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        buttons.addView(cancel);
        buttons.addView(save);
        panel.addView(buttons);

        showStyledDialog(dialog, panel, 680);
    }

    private void showAutostartDialog() {
        final String[] labels = new String[] {"Autostart Full", "Autostart Hidden", "Autostart Off"};
        final String[] values = new String[] {
                HvacTheme.AUTOSTART_FULL,
                HvacTheme.AUTOSTART_HIDDEN,
                HvacTheme.AUTOSTART_OFF
        };
        String current = prefs.getString(HvacTheme.KEY_AUTOSTART, HvacTheme.AUTOSTART_OFF);
        final int[] selected = new int[] {2};
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected[0] = i;
                break;
            }
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = makeStyledDialogPanel("AUTOSTART", "Choose startup behaviour");

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(20, 12, 20, 12);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final Button option = makeUpdateDialogButton(labels[i]);
            option.setTextSize(20);
            option.setBackground(makeUpdatePanelBackground(
                    index == selected[0] ? 0xe055f0d8 : 0xd9212120,
                    8,
                    0x99ffffff));
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.edit().putString(HvacTheme.KEY_AUTOSTART, values[index]).apply();
                    updateAutostartStatus();
                    Toast.makeText(MainActivity.this, "Autostart saved", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    72);
            optionParams.setMargins(0, 8, 0, 8);
            choices.addView(option, optionParams);
        }
        panel.addView(choices);

        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(cancel);
        panel.addView(buttons);

        showStyledDialog(dialog, panel, 620);
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

    private void updateOverlayModeStatus() {
        if (overlayModeStatus == null) return;
        String mode = prefs.getString(HvacTheme.KEY_OVERLAY_MODE, HvacTheme.OVERLAY_MODE_BAR);
        if (HvacTheme.OVERLAY_MODE_FACTORY_HVAC.equals(mode)) {
            overlayModeStatus.setText("Mode: Factory HVAC");
        } else {
            overlayModeStatus.setText("Mode: Bar");
        }
    }

    private void updateBarStyleStatus() {
        if (barStyleStatus == null) return;
        barStyleStatus.setText("Bar: " + getBarColor() + " / " + getBarOpacity() + "%");
    }

    private void updateHandleStyleStatus() {
        if (handleStyleStatus == null) return;
        String visible = isHandleBackgroundVisible() ? "Visible" : "Invisible";
        if (!isHandleTextVisible()) {
            visible += " / No text";
        }
        handleStyleStatus.setText("HVAC icon: " + getHandleWidth() + "x" + getHandleHeight()
                + " / " + visible);
    }

    private String getBarColor() {
        String color = prefs.getString(HvacTheme.KEY_BAR_COLOR, HvacTheme.DEFAULT_BAR_COLOR);
        int[] parsed = parseColorParts(color);
        if (parsed == null) {
            return HvacTheme.DEFAULT_BAR_COLOR;
        }
        return formatHex(parsed[0], parsed[1], parsed[2]);
    }

    private int getBarOpacity() {
        return clamp(prefs.getInt(HvacTheme.KEY_BAR_OPACITY, HvacTheme.DEFAULT_BAR_OPACITY), 0, 100);
    }

    private int getHandleWidth() {
        return clamp(prefs.getInt(HvacTheme.KEY_HANDLE_WIDTH, HvacTheme.DEFAULT_HANDLE_SIZE), 56, 640);
    }

    private int getHandleHeight() {
        return clamp(prefs.getInt(HvacTheme.KEY_HANDLE_HEIGHT, HvacTheme.DEFAULT_HANDLE_SIZE), 56, 360);
    }

    private int getHandleRadius() {
        return clamp(prefs.getInt(HvacTheme.KEY_HANDLE_RADIUS, HvacTheme.DEFAULT_HANDLE_RADIUS), 0, 120);
    }

    private boolean isHandleBackgroundVisible() {
        return prefs.getBoolean(HvacTheme.KEY_HANDLE_BACKGROUND_VISIBLE, true);
    }

    private boolean isHandleTextVisible() {
        return prefs.getBoolean(HvacTheme.KEY_HANDLE_TEXT_VISIBLE, true);
    }

    private TextView makeDialogLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(0xff222222);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 14, 0, 0);
        label.setLayoutParams(params);
        return label;
    }

    private TextView makeDarkDialogLabel(String text) {
        TextView label = makeDialogLabel(text);
        label.setTextColor(Color.WHITE);
        return label;
    }

    private LinearLayout makeStyledDialogPanel(String titleText, String subtitleText) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(34, 28, 34, 26);
        panel.setBackground(makeUpdatePanelBackground(0xf2222224, 18, 0xff55f0d8));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(8f, 0f, 0f, 0xaa000000);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(0xff55f0d8);
        subtitle.setTextSize(17);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, 8, 0, 18);
        panel.addView(subtitle, subtitleParams);
        return panel;
    }

    private void showStyledDialog(Dialog dialog, View content, int width) {
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showColorPickerDialog(
            final int[] targetRgb,
            final EditText hexInput,
            final SeekBar targetRed,
            final SeekBar targetGreen,
            final SeekBar targetBlue,
            final Runnable targetPreview,
            final boolean[] targetUpdating) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = makeStyledDialogPanel("COLOUR PICKER", "Tap a swatch or tune RGB");

        final int[] working = new int[] {targetRgb[0], targetRgb[1], targetRgb[2]};
        final boolean[] updating = new boolean[] {false};

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 12, 20, 12);

        final View preview = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                82);
        previewParams.setMargins(0, 0, 0, 18);
        layout.addView(preview, previewParams);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setGravity(Gravity.CENTER);
        final int[] swatchColors = new int[] {
                0x181a20, 0x212120, 0x2f3440, 0x101418,
                0x334155, 0x14532d, 0x7f1d1d, 0x4c1d95
        };
        for (int i = 0; i < swatchColors.length; i++) {
            final int color = swatchColors[i];
            TextView swatch = new TextView(this);
            swatch.setText("");
            swatch.setBackground(makePreviewBackground(0xff000000 | color, 8, 0x99ffffff));
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(54, 54);
            swatchParams.setMargins(6, 0, 6, 14);
            swatches.addView(swatch, swatchParams);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    working[0] = (color >> 16) & 0xff;
                    working[1] = (color >> 8) & 0xff;
                    working[2] = color & 0xff;
                    updating[0] = true;
                    ((SeekBar) layout.findViewWithTag("picker_red")).setProgress(working[0]);
                    ((SeekBar) layout.findViewWithTag("picker_green")).setProgress(working[1]);
                    ((SeekBar) layout.findViewWithTag("picker_blue")).setProgress(working[2]);
                    updateColorPickerPreview(preview, working);
                    updating[0] = false;
                }
            });
        }
        layout.addView(swatches);

        final TextView redLabel = makeDarkDialogLabel("");
        final SeekBar red = makeColorSeekBar(working[0]);
        red.setTag("picker_red");
        layout.addView(redLabel);
        layout.addView(red);

        final TextView greenLabel = makeDarkDialogLabel("");
        final SeekBar green = makeColorSeekBar(working[1]);
        green.setTag("picker_green");
        layout.addView(greenLabel);
        layout.addView(green);

        final TextView blueLabel = makeDarkDialogLabel("");
        final SeekBar blue = makeColorSeekBar(working[2]);
        blue.setTag("picker_blue");
        layout.addView(blueLabel);
        layout.addView(blue);

        final Runnable updateLabels = new Runnable() {
            @Override
            public void run() {
                redLabel.setText("Red: " + working[0]);
                greenLabel.setText("Green: " + working[1]);
                blueLabel.setText("Blue: " + working[2]);
                updateColorPickerPreview(preview, working);
            }
        };
        updateLabels.run();

        SeekBar.OnSeekBarChangeListener pickerListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updating[0]) return;
                working[0] = red.getProgress();
                working[1] = green.getProgress();
                working[2] = blue.getProgress();
                updateLabels.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        red.setOnSeekBarChangeListener(pickerListener);
        green.setOnSeekBarChangeListener(pickerListener);
        blue.setOnSeekBarChangeListener(pickerListener);

        panel.addView(layout);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        Button apply = makeUpdateDialogButton("Apply");
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                targetRgb[0] = working[0];
                targetRgb[1] = working[1];
                targetRgb[2] = working[2];
                targetUpdating[0] = true;
                hexInput.setText(formatHex(targetRgb[0], targetRgb[1], targetRgb[2]));
                hexInput.setSelection(hexInput.getText().length());
                targetRed.setProgress(targetRgb[0]);
                targetGreen.setProgress(targetRgb[1]);
                targetBlue.setProgress(targetRgb[2]);
                targetPreview.run();
                targetUpdating[0] = false;
                dialog.dismiss();
            }
        });
        buttons.addView(cancel);
        buttons.addView(apply);
        panel.addView(buttons);
        showStyledDialog(dialog, panel, 680);
    }

    private void updateColorPickerPreview(View preview, int[] rgb) {
        preview.setBackground(makePreviewBackground(
                composeColor(rgb[0], rgb[1], rgb[2], 100),
                10,
                0x99ffffff));
    }

    private SeekBar makeColorSeekBar(int value) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(255);
        seekBar.setProgress(clamp(value, 0, 255));
        return seekBar;
    }

    private GradientDrawable makePreviewBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, strokeColor);
        return drawable;
    }

    private int composeColor(int red, int green, int blue, int opacity) {
        int alpha = Math.round(clamp(opacity, 0, 100) * 255f / 100f);
        return (alpha << 24)
                | (clamp(red, 0, 255) << 16)
                | (clamp(green, 0, 255) << 8)
                | clamp(blue, 0, 255);
    }

    private int composeColorFromHex(String hex, int opacity) {
        int[] parts = parseColorParts(hex);
        if (parts == null) {
            parts = parseColorParts(HvacTheme.DEFAULT_BAR_COLOR);
        }
        return composeColor(parts[0], parts[1], parts[2], opacity);
    }

    private int[] parseColorParts(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.length() != 6) {
            return null;
        }
        try {
            int color = Integer.parseInt(clean, 16);
            return new int[] {
                    (color >> 16) & 0xff,
                    (color >> 8) & 0xff,
                    color & 0xff
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String formatHex(int red, int green, int blue) {
        return String.format("#%02X%02X%02X",
                clamp(red, 0, 255),
                clamp(green, 0, 255),
                clamp(blue, 0, 255));
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void refreshOverlayBarStyle() {
        if (!OverlayService.isOverlayActive()) {
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        intent.putExtra(OverlayService.EXTRA_REFRESH_STYLE, true);
        startService(intent);
    }

    private void refreshOverlayMode() {
        if (!OverlayService.isOverlayActive()) {
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        intent.putExtra(OverlayService.EXTRA_REFRESH_OVERLAY, true);
        startService(intent);
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
        checkForUpdates(true);
    }

    private void checkForUpdates(final boolean manual) {
        if (manual) {
            Toast.makeText(this, "Checking for updates", Toast.LENGTH_SHORT).show();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UpdateInfo info = fetchLatestRelease();
                    if (info == null || info.version == null || info.apkUrl == null) {
                        if (manual) {
                            showToast("No downloadable update found");
                        }
                        return;
                    }
                    if (compareVersions(info.version, CURRENT_VERSION) <= 0) {
                        if (manual) {
                            showToast("Already on latest version");
                        }
                        return;
                    }
                    showUpdateDialog(info);
                } catch (Throwable throwable) {
                    if (manual) {
                        showToast("Update check failed");
                    }
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
            String releaseNotes = root.optString("body", "");
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
            return new UpdateInfo(version, apkName, apkUrl, releaseNotes);
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
                if (updatePromptShowing || isFinishing()) {
                    return;
                }
                updatePromptShowing = true;
                final Dialog dialog = new Dialog(MainActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                LinearLayout panel = new LinearLayout(MainActivity.this);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setPadding(34, 28, 34, 28);
                panel.setBackground(makeUpdatePanelBackground(0xf2222224, 18, 0xff55f0d8));

                TextView title = new TextView(MainActivity.this);
                title.setText("UPDATE AVAILABLE");
                title.setTextColor(Color.WHITE);
                title.setTextSize(30);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setGravity(Gravity.CENTER);
                title.setShadowLayer(8f, 0f, 0f, 0xaa000000);
                panel.addView(title, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView version = new TextView(MainActivity.this);
                version.setText("HVAC Float " + info.version + " is ready");
                version.setTextColor(0xff55f0d8);
                version.setTextSize(22);
                version.setTypeface(Typeface.DEFAULT_BOLD);
                version.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                versionParams.setMargins(0, 10, 0, 18);
                panel.addView(version, versionParams);

                TextView notesTitle = new TextView(MainActivity.this);
                notesTitle.setText("Release notes");
                notesTitle.setTextColor(Color.WHITE);
                notesTitle.setTextSize(18);
                notesTitle.setTypeface(Typeface.DEFAULT_BOLD);
                panel.addView(notesTitle);

                TextView notes = new TextView(MainActivity.this);
                notes.setText(cleanReleaseNotes(info.releaseNotes));
                notes.setTextColor(0xffe8e8ee);
                notes.setTextSize(16);
                notes.setLineSpacing(3f, 1.05f);
                notes.setPadding(18, 14, 18, 14);
                notes.setBackground(makeUpdatePanelBackground(0xd9141418, 10, 0x55ffffff));

                ScrollView scroll = new ScrollView(MainActivity.this);
                scroll.addView(notes);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        230);
                scrollParams.setMargins(0, 8, 0, 22);
                panel.addView(scroll, scrollParams);

                LinearLayout buttons = new LinearLayout(MainActivity.this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                buttons.setGravity(Gravity.CENTER);

                Button cancel = makeUpdateDialogButton("Not Now");
                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

                Button download = makeUpdateDialogButton("Download");
                download.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                        downloadUpdate(info);
                    }
                });

                buttons.addView(cancel);
                buttons.addView(download);
                panel.addView(buttons, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                dialog.setContentView(panel);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        updatePromptShowing = false;
                    }
                });
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                }
                dialog.show();
                Window shownWindow = dialog.getWindow();
                if (shownWindow != null) {
                    shownWindow.setLayout(760, LinearLayout.LayoutParams.WRAP_CONTENT);
                }
            }
        });
    }

    private Button makeUpdateDialogButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setBackground(makeUpdatePanelBackground(0xd9212120, 8, 0x99ffffff));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(210, 66);
        params.setMargins(10, 0, 10, 0);
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable makeUpdatePanelBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, strokeColor);
        return drawable;
    }

    private String cleanReleaseNotes(String notes) {
        if (notes == null || notes.trim().length() == 0) {
            return "No release notes supplied.";
        }
        return notes
                .replace("\r\n", "\n")
                .replace("###", "")
                .replace("##", "")
                .replace("#", "")
                .trim();
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
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(34, 28, 34, 28);
        panel.setBackground(makeUpdatePanelBackground(0xf2222224, 18, 0xffff8f4a));

        TextView title = new TextView(this);
        title.setText("DOWNLOAD FAILED");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText("The head unit refused the direct APK download. You can open the GitHub download link instead.");
        message.setTextColor(0xffe8e8ee);
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 18, 0, 24);
        panel.addView(message, messageParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button cancel = makeUpdateDialogButton("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        Button open = makeUpdateDialogButton("Open Link");
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                openUpdateLink(info);
            }
        });
        buttons.addView(cancel);
        buttons.addView(open);
        panel.addView(buttons);

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(680, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
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
        final String releaseNotes;

        UpdateInfo(String version, String apkName, String apkUrl, String releaseNotes) {
            this.version = version;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.releaseNotes = releaseNotes;
        }
    }
}
