package kr.gildogam.car;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.WindowArea;
import ai.pleos.playground.vehicle.constant.area.WindowValue;
import ai.pleos.playground.vehicle.constant.control.WiperState;
import kotlin.Unit;

/**
 * Front wiper state through the Pleos Vehicle SDK (Wiper API), polled every two seconds.
 * The DJ treats "wipers on" as "it started raining".
 */
public final class PleosWiper {

    public interface Callback {
        void onWiper(boolean on);
    }

    private static final String TAG = "PleosWiper";
    private static final long POLL_MS = 2_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private final Callback callback;
    private final WindowArea front = new WindowArea(WindowValue.FRONT_WINDSHIELD);
    private Vehicle vehicle;
    private Boolean last;
    private boolean running;
    private int failures;
    private String status = "미연결";

    public PleosWiper(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public String status() {
        return status;
    }

    public void start() {
        if (running) return;
        try {
            vehicle = new Vehicle(context);
            vehicle.initialize();
            running = true;
            status = "연결 중";
            main.postDelayed(poll, 1_200L);
        } catch (Throwable t) {
            Log.w(TAG, "vehicle sdk unavailable", t);
            status = "Vehicle SDK 없음";
        }
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running || vehicle == null) return;
            try {
                vehicle.getWiper().getCurrentWiperState(front,
                        state -> {
                            main.post(() -> deliver(state == WiperState.ON));
                            return Unit.INSTANCE;
                        },
                        error -> {
                            main.post(() -> fail(error));
                            return Unit.INSTANCE;
                        });
            } catch (Throwable t) {
                fail(t);
            }
            if (failures < 5) main.postDelayed(this, POLL_MS);
            else status = "와이퍼 정보 없음";
        }
    };

    private void deliver(boolean on) {
        failures = 0;
        status = "와이퍼 연결됨";
        if (last == null || last != on) {
            last = on;
            callback.onWiper(on);
        }
    }

    private void fail(Throwable t) {
        failures++;
        Log.w(TAG, "wiper read failed: " + t);
    }

    public void stop() {
        running = false;
        main.removeCallbacks(poll);
        if (vehicle != null) {
            try {
                vehicle.release();
            } catch (Throwable ignored) {
            }
        }
        vehicle = null;
    }
}
