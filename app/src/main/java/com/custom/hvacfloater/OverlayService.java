package com.custom.hvacfloater;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Parcel;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

public class OverlayService extends Service {
    public static final String EXTRA_START_HIDDEN = "com.custom.hvacfloater.START_HIDDEN";
    public static final String EXTRA_REFRESH_STYLE = "com.custom.hvacfloater.REFRESH_STYLE";
    public static final String EXTRA_REFRESH_OVERLAY = "com.custom.hvacfloater.REFRESH_OVERLAY";
    private static final int NOTIFICATION_ID = 44;
    private static final String NOTIFICATION_CHANNEL_ID = "hvac_float_overlay";
    private static final String SYSTEMUI_ACTION = "com.android.systemui.saicmotor.action.StartActivity";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String SYSTEMUI_SERVICE = "com.android.systemui.StartActivityService";
    private static final String START_ACTIVITY_INTERFACE = "com.android.systemui.IStartActivityService";
    private static final int TRANSACTION_START_HVAC = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_STOP_HVAC = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int BAR_HEIGHT = 92;
    private static final int ICON_BAR_HEIGHT = 104;
    private static final int HANDLE_SIZE = BAR_HEIGHT;
    private static final long DOUBLE_TAP_MS = 350;

    private WindowManager windowManager;
    private LinearLayout overlayView;
    private WindowManager.LayoutParams overlayParams;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean dragging;
    private boolean longPressReady;
    private float downRawX;
    private float downRawY;
    private int downParamX;
    private int downParamY;
    private boolean hidden;
    private long lastHandleTapMs;
    private HvacController hvacController;
    private String theme = HvacTheme.TEXT;
    private String overlayMode = HvacTheme.OVERLAY_MODE_BAR;
    private SharedPreferences prefs;
    private static boolean overlayActive;
    private boolean factoryHvacOpen;
    private boolean factoryHvacBinding;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            longPressReady = true;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        overlayActive = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    static boolean isOverlayActive() {
        return overlayActive;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (overlayView != null) {
            if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_OVERLAY, false)) {
                refreshOverlayMode();
                return START_STICKY;
            }
            if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_STYLE, false)) {
                loadTheme();
                if (!hidden && !isFactoryHvacMode()) {
                    populateExpandedShell();
                    try {
                        windowManager.updateViewLayout(overlayView, overlayParams);
                    } catch (Throwable ignored) {
                    }
                }
                return START_STICKY;
            }
            loadTheme();
            if (isFactoryHvacMode()) {
                ensureFactoryHandle();
                return START_STICKY;
            }
            if (intent != null && intent.getBooleanExtra(EXTRA_START_HIDDEN, false)) {
                collapseBar();
            } else if (hidden) {
                expandBar();
            }
            return START_STICKY;
        }

        loadTheme();
        boolean startHidden = isFactoryHvacMode()
                || (intent != null && intent.getBooleanExtra(EXTRA_START_HIDDEN, false));
        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.HORIZONTAL);
        overlayView.setGravity(Gravity.CENTER);
        overlayView.setOnTouchListener(new DragTouchListener());
        if (startHidden) {
            hidden = true;
            populateCollapsedBar();
        } else {
            hidden = false;
            populateExpandedShell();
            populateExpandedBar();
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                startHidden ? HANDLE_SIZE : WindowManager.LayoutParams.WRAP_CONTENT,
                startHidden ? HANDLE_SIZE : expandedBarHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        if (prefs.getBoolean(HvacTheme.KEY_POSITION_SAVED, false)) {
            params.x = prefs.getInt(HvacTheme.KEY_POSITION_X, 0);
            params.y = prefs.getInt(HvacTheme.KEY_POSITION_Y, metrics.heightPixels - expandedBarHeight() - 8);
        } else {
            params.x = 0;
            params.y = Math.max(0, metrics.heightPixels - expandedBarHeight() - 8);
        }
        overlayParams = params;

        try {
            windowManager.addView(overlayView, params);
        } catch (Throwable throwable) {
            overlayView = null;
            Toast.makeText(this, "Overlay add failed", Toast.LENGTH_LONG).show();
            stopSelf();
        }

        return START_STICKY;
    }

    private void startAsForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "HVAC Float",
                        NotificationManager.IMPORTANCE_MIN);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    : new Notification.Builder(this);
            Notification notification = builder
                    .setContentTitle("HVAC Float")
                    .setContentText("Overlay running")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        } catch (Throwable ignored) {
        }
    }

    private void loadTheme() {
        prefs = getSharedPreferences(HvacTheme.PREFS, MODE_PRIVATE);
        theme = prefs.getString(HvacTheme.KEY_THEME, HvacTheme.TEXT);
        overlayMode = prefs.getString(HvacTheme.KEY_OVERLAY_MODE, HvacTheme.OVERLAY_MODE_BAR);
    }

    private boolean isFactoryHvacMode() {
        return HvacTheme.OVERLAY_MODE_FACTORY_HVAC.equals(overlayMode);
    }

    private boolean controlEnabled(String key) {
        if (prefs == null) {
            prefs = getSharedPreferences(HvacTheme.PREFS, MODE_PRIVATE);
        }
        return prefs.getBoolean(key, true);
    }

    private int expandedBarHeight() {
        return HvacTheme.ICON_SET_1.equals(theme) ? ICON_BAR_HEIGHT : BAR_HEIGHT;
    }

    private void savePosition() {
        if (prefs == null) {
            prefs = getSharedPreferences(HvacTheme.PREFS, MODE_PRIVATE);
        }
        if (overlayParams == null) return;
        prefs.edit()
                .putBoolean(HvacTheme.KEY_POSITION_SAVED, true)
                .putInt(HvacTheme.KEY_POSITION_X, overlayParams.x)
                .putInt(HvacTheme.KEY_POSITION_Y, overlayParams.y)
                .apply();
    }

    private void populateExpandedShell() {
        int barColor = configuredBarBackgroundColor();
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            overlayView.setPadding(12, 8, 12, 8);
            overlayView.setBackground(makeBackground(barColor, 18, 0x66ffffff));
        } else {
            overlayView.setPadding(14, 10, 14, 10);
            overlayView.setBackground(makeBackground(barColor, 18, 0x66ffffff));
        }
    }

    private int configuredBarBackgroundColor() {
        if (prefs == null) {
            prefs = getSharedPreferences(HvacTheme.PREFS, MODE_PRIVATE);
        }
        int rgb = parseRgbColor(
                prefs.getString(HvacTheme.KEY_BAR_COLOR, HvacTheme.DEFAULT_BAR_COLOR),
                0x181a20);
        int opacity = prefs.getInt(HvacTheme.KEY_BAR_OPACITY, HvacTheme.DEFAULT_BAR_OPACITY);
        opacity = Math.max(0, Math.min(100, opacity));
        int alpha = Math.round(opacity * 255f / 100f);
        return (alpha << 24) | rgb;
    }

    private int parseRgbColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String clean = value.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(clean, 16) & 0x00ffffff;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private TextView makeLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(92, 62);
        params.setMargins(5, 0, 5, 0);
        view.setLayoutParams(params);
        return view;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setPadding(2, 0, 2, 0);
        button.setBackground(makeBackground(0xd9212120, 10, 0x88ffffff));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(76, 62);
        params.setMargins(5, 0, 5, 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button makeIconButton(String drawableName) {
        Button button = makeButton("");
        makeBareIconButton(button);
        setButtonIcon(button, drawableName, 42);
        return button;
    }

    private void makeBareIconButton(Button button) {
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
    }

    private void setButtonIcon(Button button, String drawableName, int size) {
        setButtonIcon(button, drawableName, size, size);
    }

    private void setButtonIcon(Button button, String drawableName, int width, int height) {
        try {
            int drawableResId = getResources().getIdentifier(drawableName, "drawable", getPackageName());
            Drawable icon = getResources().getDrawable(drawableResId);
            icon.setBounds(0, 0, width, height);
            button.setText("");
            button.setCompoundDrawables(null, null, null, null);
            button.setForeground(icon);
            button.setForegroundGravity(Gravity.CENTER);
            button.setGravity(Gravity.CENTER);
        } catch (Throwable ignored) {
        }
    }

    private Button makePlusMinusButton(String text) {
        Button button = makeButton(text);
        button.setTextSize(24);
        return button;
    }

    private Button makeTempDownButton() {
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            return makeIconButton("hvac_icon_temp_reduce");
        }
        return makePlusMinusButton("-");
    }

    private Button makeTempUpButton() {
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            return makeIconButton("hvac_icon_temp_add");
        }
        return makePlusMinusButton("+");
    }

    private Button makeFanDownButton() {
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            return makeIconButton("hvac_icon_fan_reduce");
        }
        return makePlusMinusButton("-");
    }

    private Button makeFanUpButton() {
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            return makeIconButton("hvac_icon_fan_add");
        }
        return makePlusMinusButton("+");
    }

    private Button makeFlowButton() {
        int lastMode = prefs != null ? prefs.getInt(HvacTheme.KEY_LAST_FLOW_MODE, 2) : 2;
        Button button = makeButton(flowLabel(lastMode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                HvacTheme.ICON_SET_1.equals(theme) ? 124 : 86,
                62);
        params.setMargins(5, 0, 5, 0);
        button.setLayoutParams(params);
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            makeBareIconButton(button);
            button.setText("");
            button.setTag("hvac_icon_flow");
            setButtonIcon(button, flowIcon(lastMode), 110, 50);
        }
        return button;
    }

    private String flowLabel(int mode) {
        if (mode == 2) return "Feet";
        if (mode == 1) return "Feet\nFace";
        if (mode == 0) return "Face";
        return "Feet";
    }

    private String flowIcon(int mode) {
        if (mode == 2) return "hvac_icon_flow_feet";
        if (mode == 1) return "hvac_icon_flow_feet_face";
        if (mode == 0) return "hvac_icon_flow_face";
        return "hvac_icon_flow_feet";
    }

    private Button makeSeatButton(String text, String iconPrefix) {
        Button button = makeButton(text);
        button.setTextSize(14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                HvacTheme.ICON_SET_1.equals(theme) ? 104 : 88,
                HvacTheme.ICON_SET_1.equals(theme) ? 82 : 62);
        params.setMargins(5, 0, 5, 0);
        button.setLayoutParams(params);
        if (HvacTheme.ICON_SET_1.equals(theme)) {
            makeBareIconButton(button);
            button.setText("");
            button.setTag(iconPrefix);
            setButtonIcon(button, iconPrefix + "_0", 100, 66);
        }
        return button;
    }

    private Button makeMinimizeButton() {
        Button button = makeButton("HIDE");
        button.setTextSize(18);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                collapseBar();
            }
        });
        return button;
    }

    private Button makeSettingsButton() {
        final Button button = makeButton("");
        try {
            if (HvacTheme.ICON_SET_1.equals(theme)) {
                makeBareIconButton(button);
                setButtonIcon(button, "ic_settings_gear", 54);
            } else {
                int drawableResId = getResources().getIdentifier("settings_button_bg", "drawable", getPackageName());
                button.setBackgroundResource(drawableResId);
                button.setCompoundDrawables(null, null, null, null);
            }
        } catch (Throwable ignored) {
            button.setText("Settings");
            button.setTextSize(12);
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(OverlayService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        return button;
    }

    private void populateExpandedBar() {
        loadTheme();
        final Button passengerSeat = controlEnabled(HvacTheme.KEY_PASSENGER_HEAT) ? makeSeatButton("PSG\nHeat 0", "hvac_icon_psg_seat") : null;
        final Button tempDown = controlEnabled(HvacTheme.KEY_TEMP) ? makeTempDownButton() : null;
        final TextView temp = controlEnabled(HvacTheme.KEY_TEMP) ? makeLabel("22 C") : null;
        final Button tempUp = controlEnabled(HvacTheme.KEY_TEMP) ? makeTempUpButton() : null;
        final Button fanDown = controlEnabled(HvacTheme.KEY_FAN) ? makeFanDownButton() : null;
        final TextView fan = controlEnabled(HvacTheme.KEY_FAN) ? makeLabel("Fan 1") : null;
        final Button fanUp = controlEnabled(HvacTheme.KEY_FAN) ? makeFanUpButton() : null;
        final Button auto = controlEnabled(HvacTheme.KEY_AUTO) ? makeButton("AUTO\nFAN") : null;
        final Button loop = controlEnabled(HvacTheme.KEY_LOOP) ? makeButton("Loop") : null;
        if (loop != null && HvacTheme.ICON_SET_1.equals(theme)) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(108, 62);
            params.setMargins(5, 0, 5, 0);
            loop.setLayoutParams(params);
            makeBareIconButton(loop);
            loop.setText("");
            loop.setTag("hvac_icon_loop");
            setButtonIcon(loop, "hvac_icon_loop_recirc", 86, 50);
        }
        final Button flow = controlEnabled(HvacTheme.KEY_FLOW) ? makeFlowButton() : null;
        final Button defrost = controlEnabled(HvacTheme.KEY_DEFROST) ? makeButton("Defrost") : null;
        if (defrost != null && HvacTheme.ICON_SET_1.equals(theme)) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(108, 62);
            params.setMargins(5, 0, 5, 0);
            defrost.setLayoutParams(params);
            makeBareIconButton(defrost);
            defrost.setText("");
            defrost.setTag("hvac_icon_defrost");
            setButtonIcon(defrost, "hvac_icon_defrost_off", 86, 50);
        }
        final Button wheel = controlEnabled(HvacTheme.KEY_STEERING_HEAT) ? makeButton("Wheel") : null;
        if (wheel != null && HvacTheme.ICON_SET_1.equals(theme)) {
            makeBareIconButton(wheel);
            wheel.setText("");
            wheel.setTag("hvac_icon_wheel");
            setButtonIcon(wheel, "hvac_icon_wheel_0", 48);
        }
        final Button driverSeat = controlEnabled(HvacTheme.KEY_DRIVER_HEAT) ? makeSeatButton("DRV\nHeat 0", "hvac_icon_drv_seat") : null;
        final Button settings = controlEnabled(HvacTheme.KEY_SETTINGS) ? makeSettingsButton() : null;

        hvacController = new HvacController(temp, fan, null, auto, loop, flow, defrost, passengerSeat, driverSeat, wheel);
        hvacController.init(this);

        if (passengerSeat != null) passengerSeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.cyclePassengerSeat();
            }
        });
        if (tempDown != null) tempDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.tempDown();
            }
        });
        if (tempUp != null) tempUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.tempUp();
            }
        });
        if (fanDown != null) fanDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.fanDown();
            }
        });
        if (fanUp != null) fanUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.fanUp();
            }
        });
        if (auto != null) auto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.toggleAuto();
            }
        });
        if (loop != null) loop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.toggleLoop();
            }
        });
        if (flow != null) flow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.cycleFlow();
            }
        });
        if (defrost != null) defrost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.toggleDefrost();
            }
        });
        if (wheel != null) wheel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.toggleSteeringWheelHeat();
            }
        });
        if (driverSeat != null) driverSeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hvacController.cycleDriverSeat();
            }
        });

        if (passengerSeat != null) passengerSeat.setOnTouchListener(new DragTouchListener(true));
        if (tempDown != null) tempDown.setOnTouchListener(new DragTouchListener(true));
        if (temp != null) temp.setOnTouchListener(new DragTouchListener(false));
        if (tempUp != null) tempUp.setOnTouchListener(new DragTouchListener(true));
        if (fanDown != null) fanDown.setOnTouchListener(new DragTouchListener(true));
        if (fan != null) fan.setOnTouchListener(new DragTouchListener(false));
        if (fanUp != null) fanUp.setOnTouchListener(new DragTouchListener(true));
        if (auto != null) auto.setOnTouchListener(new DragTouchListener(true));
        if (loop != null) loop.setOnTouchListener(new DragTouchListener(true));
        if (flow != null) flow.setOnTouchListener(new DragTouchListener(true));
        if (defrost != null) defrost.setOnTouchListener(new DragTouchListener(true));
        if (wheel != null) wheel.setOnTouchListener(new DragTouchListener(true));
        if (driverSeat != null) driverSeat.setOnTouchListener(new DragTouchListener(true));
        if (settings != null) settings.setOnTouchListener(new DragTouchListener(true));

        if (passengerSeat != null) overlayView.addView(passengerSeat);
        if (tempDown != null) overlayView.addView(tempDown);
        if (temp != null) overlayView.addView(temp);
        if (tempUp != null) overlayView.addView(tempUp);
        if (fanDown != null) overlayView.addView(fanDown);
        if (fan != null) overlayView.addView(fan);
        if (fanUp != null) overlayView.addView(fanUp);
        if (auto != null) overlayView.addView(auto);
        if (loop != null) overlayView.addView(loop);
        if (flow != null) overlayView.addView(flow);
        if (defrost != null) overlayView.addView(defrost);
        if (wheel != null) overlayView.addView(wheel);
        if (driverSeat != null) overlayView.addView(driverSeat);
        if (settings != null) overlayView.addView(settings);
        overlayView.addView(makeMinimizeButton());
    }

    private GradientDrawable makeBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, strokeColor);
        return drawable;
    }

    private void releaseHvacController() {
        if (hvacController != null) {
            hvacController.release();
            hvacController = null;
        }
    }

    private void refreshOverlayMode() {
        loadTheme();
        if (overlayView == null || overlayParams == null || windowManager == null) {
            return;
        }
        overlayView.removeAllViews();
        if (isFactoryHvacMode()) {
            factoryHvacOpen = false;
            releaseHvacController();
            hidden = true;
            populateCollapsedBar();
            overlayParams.width = HANDLE_SIZE;
            overlayParams.height = HANDLE_SIZE;
        } else if (hidden) {
            populateCollapsedBar();
            overlayParams.width = HANDLE_SIZE;
            overlayParams.height = HANDLE_SIZE;
        } else {
            populateExpandedShell();
            populateExpandedBar();
            overlayParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            overlayParams.height = expandedBarHeight();
        }
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
            savePosition();
        } catch (Throwable ignored) {
        }
    }

    private void ensureFactoryHandle() {
        if (overlayView == null || overlayParams == null || windowManager == null) {
            return;
        }
        if (!hidden || overlayView.getChildCount() != 1
                || overlayParams.width != HANDLE_SIZE
                || overlayParams.height != HANDLE_SIZE) {
            overlayView.removeAllViews();
            releaseHvacController();
            hidden = true;
            populateCollapsedBar();
            overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
            overlayParams.width = HANDLE_SIZE;
            overlayParams.height = HANDLE_SIZE;
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
                savePosition();
            } catch (Throwable ignored) {
            }
        }
    }

    private void collapseBar() {
        if (hidden || overlayView == null || overlayParams == null || windowManager == null) {
            return;
        }
        releaseHvacController();
        hidden = true;
        overlayView.removeAllViews();
        populateCollapsedBar();
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.width = HANDLE_SIZE;
        overlayParams.height = HANDLE_SIZE;
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
            savePosition();
        } catch (Throwable ignored) {
        }
    }

    private void populateCollapsedBar() {
        overlayView.setPadding(8, 8, 8, 8);
        overlayView.setBackground(makeBackground(0xcc181a20, 18, 0x66ffffff));
        TextView handle = makeLabel("HVAC");
        handle.setTextSize(14);
        handle.setBackgroundColor(Color.TRANSPARENT);
        handle.setOnTouchListener(new DragTouchListener());
        overlayView.addView(handle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private void expandBar() {
        if (!hidden || overlayView == null || overlayParams == null || windowManager == null) {
            return;
        }
        if (isFactoryHvacMode()) {
            return;
        }
        hidden = false;
        overlayView.removeAllViews();
        populateExpandedShell();
        populateExpandedBar();
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        overlayParams.height = expandedBarHeight();
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
            savePosition();
        } catch (Throwable ignored) {
        }
    }

    private void toggleFactoryHvacOverlay() {
        if (factoryHvacBinding) {
            return;
        }
        final boolean open = !factoryHvacOpen;
        Intent intent = new Intent();
        intent.setAction(SYSTEMUI_ACTION);
        intent.setComponent(new ComponentName(SYSTEMUI_PACKAGE, SYSTEMUI_SERVICE));
        factoryHvacBinding = true;
        try {
            boolean bound = bindService(intent, new FactoryHvacConnection(open), BIND_AUTO_CREATE);
            if (!bound) {
                factoryHvacBinding = false;
                Toast.makeText(this, "Factory HVAC bind failed", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable throwable) {
            factoryHvacBinding = false;
            Toast.makeText(this, "Factory HVAC unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean callFactoryHvac(IBinder service, boolean open) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(START_ACTIVITY_INTERFACE);
            service.transact(open ? TRANSACTION_START_HVAC : TRANSACTION_STOP_HVAC, data, reply, 0);
            reply.readException();
            return true;
        } catch (Throwable throwable) {
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private final class FactoryHvacConnection implements ServiceConnection {
        private final boolean open;

        FactoryHvacConnection(boolean open) {
            this.open = open;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            boolean ok = callFactoryHvac(service, open);
            if (ok) {
                factoryHvacOpen = open;
                Toast.makeText(
                        OverlayService.this,
                        open ? "Factory HVAC opened" : "Factory HVAC closed",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(OverlayService.this, "Factory HVAC call failed", Toast.LENGTH_SHORT).show();
            }
            try {
                unbindService(this);
            } catch (Throwable ignored) {
            }
            factoryHvacBinding = false;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            factoryHvacBinding = false;
        }
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private final boolean performClickOnTap;

        DragTouchListener() {
            this(false);
        }

        DragTouchListener(boolean performClickOnTap) {
            this.performClickOnTap = performClickOnTap;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (overlayParams == null || overlayView == null || windowManager == null) {
                return false;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = false;
                    longPressReady = false;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downParamX = overlayParams.x;
                    downParamY = overlayParams.y;
                    handler.removeCallbacks(longPressRunnable);
                    handler.postDelayed(longPressRunnable, 450);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (longPressReady || dragging) {
                        dragging = true;
                        overlayParams.x = downParamX + Math.round(event.getRawX() - downRawX);
                        overlayParams.y = downParamY + Math.round(event.getRawY() - downRawY);
                        try {
                            windowManager.updateViewLayout(overlayView, overlayParams);
                        } catch (Throwable ignored) {
                        }
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    if (hidden && !dragging && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        long now = System.currentTimeMillis();
                        if (now - lastHandleTapMs <= DOUBLE_TAP_MS) {
                            lastHandleTapMs = 0;
                            if (isFactoryHvacMode()) {
                                toggleFactoryHvacOverlay();
                            } else {
                                expandBar();
                            }
                        } else {
                            lastHandleTapMs = now;
                        }
                    }
                    if (dragging) {
                        savePosition();
                    } else if (!hidden && performClickOnTap && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        view.performClick();
                    }
                    longPressReady = false;
                    dragging = false;
                    return true;

                default:
                    return false;
            }
        }
    }

    @Override
    public void onDestroy() {
        overlayActive = false;
        handler.removeCallbacks(longPressRunnable);
        if (hvacController != null) {
            hvacController.release();
            hvacController = null;
        }
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Throwable ignored) {
            }
            overlayView = null;
        }
        super.onDestroy();
    }
}
