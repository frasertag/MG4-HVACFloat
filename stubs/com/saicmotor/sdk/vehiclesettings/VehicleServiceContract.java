package com.saicmotor.sdk.vehiclesettings;

import com.saicmotor.sdk.vehiclesettings.bean.AirConditionBean;
import com.saicmotor.sdk.vehiclesettings.manager.BaseManager;

public final class VehicleServiceContract {
    private VehicleServiceContract() {
    }

    public interface IVehicleServiceListener {
        void onServiceConnected(BaseManager baseManager);
        void onServiceDisconnected();
    }

    public interface IAirConditionCallback {
        void onAirConditionChangeEvent(AirConditionBean airConditionBean);
        void onAirConditionErrorEvent(String message, int code);
    }
}

