package com.custom.hvacfloater;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.saicmotor.sdk.vehiclesettings.VehicleServiceContract;
import com.saicmotor.sdk.vehiclesettings.bean.AirConditionBean;
import com.saicmotor.sdk.vehiclesettings.manager.AirConditionManager;
import com.saicmotor.sdk.vehiclesettings.manager.BaseManager;

final class HvacController {
    private static final int ACTIVE_COLOR = Color.rgb(48, 209, 88);
    private static final int INACTIVE_COLOR = Color.WHITE;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextView tempText;
    private final TextView fanText;
    private final TextView acButton;
    private final TextView autoButton;
    private final TextView loopButton;
    private final TextView flowButton;
    private final TextView defrostButton;
    private final TextView passengerSeatButton;
    private final TextView driverSeatButton;
    private final TextView steeringWheelHeatButton;

    private AirConditionManager manager;
    private AirConditionBean bean;
    private int loopMode;
    private int passengerSeatLevel;
    private int driverSeatLevel;
    private int steeringWheelHeatLevel;
    private int flowMode = 2;
    private long lastLoopCommandMs;

    HvacController(TextView tempText, TextView fanText, TextView acButton, TextView autoButton,
                   TextView loopButton, TextView flowButton, TextView defrostButton,
                   TextView passengerSeatButton, TextView driverSeatButton,
                   TextView steeringWheelHeatButton) {
        this.tempText = tempText;
        this.fanText = fanText;
        this.acButton = acButton;
        this.autoButton = autoButton;
        this.loopButton = loopButton;
        this.flowButton = flowButton;
        this.defrostButton = defrostButton;
        this.passengerSeatButton = passengerSeatButton;
        this.driverSeatButton = driverSeatButton;
        this.steeringWheelHeatButton = steeringWheelHeatButton;
    }

    void init(Context context) {
        try {
            AirConditionManager.init(context, new VehicleServiceContract.IVehicleServiceListener() {
                @Override
                public void onServiceConnected(BaseManager baseManager) {
                    manager = (AirConditionManager) baseManager;
                    try {
                        bean = manager.getAirConditionStatus();
                        updateFromBean(bean);
                        manager.registerAirConditionCallback(callback);
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onServiceDisconnected() {
                    manager = null;
                }
            }, 1200L);
        } catch (Throwable ignored) {
        }
    }

    void release() {
        try {
            if (manager != null) {
                manager.unregisterAirConditionCallback(callback);
                manager.release();
            }
        } catch (Throwable ignored) {
        }
        manager = null;
    }

    void tempDown() {
        if (manager == null) return;
        int temp = currentTemp();
        if (temp > 17) setTemp(temp - 1);
    }

    void tempUp() {
        if (manager == null) return;
        int temp = currentTemp();
        if (temp < 33) setTemp(temp + 1);
    }

    void fanDown() {
        if (manager == null) return;
        int fan = currentFan();
        if (fan > 1) setFan(fan - 1);
    }

    void fanUp() {
        if (manager == null) return;
        int fan = currentFan();
        if (fan < 10) setFan(fan + 1);
    }

    void acOn() {
        if (manager == null) return;
        try {
            manager.setAcStatus(1);
        } catch (Throwable ignored) {
        }
    }

    void cycleFlow() {
        if (manager == null) return;
        int next;
        if (flowMode == 2) {
            next = 1;
        } else if (flowMode == 1) {
            next = 0;
        } else {
            next = 2;
        }
        try {
            manager.setBlowerDirectionMode(next);
            if (bean != null) bean.setBlowerDirectionMode(next);
            updateFlow(next);
        } catch (Throwable ignored) {
        }
    }

    void toggleAuto() {
        if (manager == null) return;
        int next = currentAuto() == 1 ? 0 : 1;
        try {
            manager.setAutoStatus(next);
            updateAuto(next);
            if (next == 1 && fanText != null) fanText.setText("Fan Auto");
        } catch (Throwable ignored) {
        }
    }

    void toggleLoop() {
        if (manager == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLoopCommandMs < 700) return;
        lastLoopCommandMs = now;
        try {
            manager.setLoopMode(1);
        } catch (Throwable ignored) {
        }
    }

    void toggleDefrost() {
        if (manager == null) return;
        int next = currentDefrost() == 1 ? 0 : 1;
        try {
            if (next == 1) {
                manager.openFrontWindowDefroster();
            } else {
                manager.closeFrontWindowDefroster();
            }
            updateDefrost(next);
        } catch (Throwable ignored) {
        }
    }

    void cyclePassengerSeat() {
        if (manager == null) return;
        int next = nextSeatLevel(passengerSeatLevel);
        try {
            manager.setDrvSeatHeatLevel(1);
            updatePassengerSeat(next);
        } catch (Throwable ignored) {
        }
    }

    void cycleDriverSeat() {
        if (manager == null) return;
        int next = nextSeatLevel(driverSeatLevel);
        try {
            manager.setPsgSeatHeatLevel(1);
            updateDriverSeat(next);
        } catch (Throwable ignored) {
        }
    }

    void toggleSteeringWheelHeat() {
        if (manager == null) return;
        int nextVisual = steeringWheelHeatLevel == 3 ? 0 : 3;
        try {
            manager.setSteeringWheelHeat(1);
            if (bean != null) bean.setSteeringWheelHeat(nextVisual);
            updateSteeringWheelHeat(nextVisual);
        } catch (Throwable ignored) {
        }
    }

    private int nextSeatLevel(int current) {
        current = normalizeSeatLevel(current);
        return current >= 3 ? 0 : current + 1;
    }

    private void setTemp(int temp) {
        try {
            manager.setDrvTemp(temp);
            if (bean != null) bean.setDrvTemp(temp);
            updateTemp(temp);
        } catch (Throwable ignored) {
        }
    }

    private void setFan(int fan) {
        try {
            manager.setAirVolumeLevel(fan);
            if (bean != null) bean.setAirVolumeLevel(fan);
            updateFan(fan);
        } catch (Throwable ignored) {
        }
    }

    private int currentTemp() {
        return bean != null ? bean.getDrvTemp() : 22;
    }

    private int currentFan() {
        int fan = bean != null ? bean.getAirVolumeLevel() : 1;
        if (fan < 1) return 1;
        if (fan > 10) return 10;
        return fan;
    }

    private int currentAuto() {
        return bean != null ? bean.getAutoStatus() : 0;
    }

    private int currentLoop() {
        return bean != null ? bean.getLoopMode() : 0;
    }

    private int currentDefrost() {
        return bean != null ? bean.getFrontWindowDefroster() : 0;
    }

    private int currentSteeringWheelHeat() {
        return bean != null ? bean.getSteeringWheelHeatLevel() : 0;
    }

    private int normalizeSeatLevel(int level) {
        if (level < 0) return 0;
        if (level > 3) return 3;
        return level;
    }

    private int normalizeLoopMode(int mode) {
        if (mode < 0) return 0;
        if (mode > 2) return 2;
        return mode;
    }

    private void updateFromBean(final AirConditionBean value) {
        if (value == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bean = value;
                updateTemp(value.getDrvTemp());
                if (value.getAutoStatus() == 1) {
                    if (fanText != null) fanText.setText("Fan Auto");
                } else {
                    updateFan(value.getAirVolumeLevel());
                }
                updateAc(value.getAcSwitch());
                updateAuto(value.getAutoStatus());
                updateLoop(value.getLoopMode());
                updateFlow(value.getBlowerDirectionMode());
                updateDefrost(value.getFrontWindowDefroster());
                updatePassengerSeat(value.getDrvSeatHeatLevel());
                updateDriverSeat(value.getPsgSeatHeatLevel());
                updateSteeringWheelHeat(value.getSteeringWheelHeatLevel());
            }
        });
    }

    private void updateTemp(int temp) {
        if (tempText != null) tempText.setText(temp + " C");
    }

    private void updateFan(int fan) {
        if (fan < 1) fan = 1;
        if (fan > 10) fan = 10;
        if (fanText != null) fanText.setText("Fan " + fan);
    }

    private void updateAc(int state) {
        if (acButton != null) acButton.setTextColor(state == 1 ? ACTIVE_COLOR : INACTIVE_COLOR);
    }

    private void updateAuto(int state) {
        if (autoButton != null) autoButton.setTextColor(state == 1 ? ACTIVE_COLOR : INACTIVE_COLOR);
    }

    private void updateLoop(int state) {
        loopMode = normalizeLoopMode(state);
        if (loopButton != null) {
            if (loopMode == 0) {
                loopButton.setText("Recirc");
            } else if (loopMode == 1) {
                loopButton.setText("Air In");
            } else {
                loopButton.setText("Air Auto");
            }
            loopButton.setTextColor(INACTIVE_COLOR);
        }
    }

    private void updateDefrost(int state) {
        if (defrostButton != null) defrostButton.setTextColor(state == 1 ? ACTIVE_COLOR : INACTIVE_COLOR);
    }

    private void updateFlow(int mode) {
        flowMode = mode;
        if (flowButton != null) {
            if (mode == 2) {
                flowButton.setText("Feet");
            } else if (mode == 1) {
                flowButton.setText("Feet\nFace");
            } else if (mode == 0) {
                flowButton.setText("Face");
            } else {
                flowButton.setText("Flow");
            }
            flowButton.setTextColor(INACTIVE_COLOR);
        }
    }

    private void updatePassengerSeat(int level) {
        level = normalizeSeatLevel(level);
        passengerSeatLevel = level;
        if (passengerSeatButton != null) {
            passengerSeatButton.setText("PSG\nHeat " + level);
            passengerSeatButton.setTextColor(level > 0 ? ACTIVE_COLOR : INACTIVE_COLOR);
        }
    }

    private void updateDriverSeat(int level) {
        level = normalizeSeatLevel(level);
        driverSeatLevel = level;
        if (driverSeatButton != null) {
            driverSeatButton.setText("DRV\nHeat " + level);
            driverSeatButton.setTextColor(level > 0 ? ACTIVE_COLOR : INACTIVE_COLOR);
        }
    }

    private void updateSteeringWheelHeat(int state) {
        steeringWheelHeatLevel = state;
        if (steeringWheelHeatButton != null) {
            steeringWheelHeatButton.setTextColor(state == 3 ? ACTIVE_COLOR : INACTIVE_COLOR);
        }
    }

    private final VehicleServiceContract.IAirConditionCallback callback =
            new VehicleServiceContract.IAirConditionCallback() {
                @Override
                public void onAirConditionChangeEvent(AirConditionBean airConditionBean) {
                    updateFromBean(airConditionBean);
                }

                @Override
                public void onAirConditionErrorEvent(String message, int code) {
                }
            };
}
