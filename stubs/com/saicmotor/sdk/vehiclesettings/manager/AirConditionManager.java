package com.saicmotor.sdk.vehiclesettings.manager;

import android.content.Context;

import com.saicmotor.sdk.vehiclesettings.VehicleServiceContract;
import com.saicmotor.sdk.vehiclesettings.bean.AirConditionBean;

public class AirConditionManager extends BaseManager {
    public static void init(Context context, VehicleServiceContract.IVehicleServiceListener listener, long timeout) {
    }

    public AirConditionBean getAirConditionStatus() {
        return null;
    }

    public void registerAirConditionCallback(VehicleServiceContract.IAirConditionCallback callback) {
    }

    public void unregisterAirConditionCallback(VehicleServiceContract.IAirConditionCallback callback) {
    }

    public void release() {
    }

    public void setDrvTemp(int value) {
    }

    public void setAirVolumeLevel(int value) {
    }

    public void setBlowerDirectionMode(int value) {
    }

    public void setAcStatus(int value) {
    }

    public void setAutoStatus(int value) {
    }

    public void setLoopMode(int value) {
    }

    public void openLoopOutside() {
    }

    public void openLoopInner() {
    }

    public void openLoopAuto() {
    }

    public void openFrontWindowDefroster() {
    }

    public void closeFrontWindowDefroster() {
    }

    public void setDrvSeatHeatLevel(int value) {
    }

    public void setPsgSeatHeatLevel(int value) {
    }

    public void setSteeringWheelHeat(int value) {
    }
}
