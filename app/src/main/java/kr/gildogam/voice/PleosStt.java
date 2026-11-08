package kr.gildogam.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;

import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.constant.ErrorType;
import ai.pleos.playground.stt.constant.Mode;
import ai.pleos.playground.stt.listener.OnInitializeListener;
import ai.pleos.playground.stt.listener.ResultListener;

/**
 * Pleos SpeechToText SDK (on-device, Korean). Written in Java so the SDK's listener
 * interfaces are implemented exactly as declared. Callbacks are delivered on the main thread.
 */
public final class PleosStt {

    public interface Events {
        void onInit(boolean ok);
        void onPartial(String text);
        void onFinal(String text);
        void onError(String reason);
    }

    private static final String TAG = "PleosStt";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SpeechToText stt;
    private boolean ready;
    private Events events;

    public PleosStt(Context context) {
        stt = new SpeechToText(context.getApplicationContext(), Mode.ON_DEVICE);
    }

    public boolean isReady() {
        return ready;
    }

    public void initialize(Events listener) {
        events = listener;
        stt.initialize(new OnInitializeListener() {
            @Override
            public void onSuccess() {
                main.post(() -> {
                    try {
                        stt.setLocale(Locale.KOREA);
                    } catch (Exception e) {
                        Log.w(TAG, "setLocale failed", e);
                    }
                    ready = true;
                    events.onInit(true);
                });
            }

            @Override
            public void onFailure(ErrorType errorType) {
                Log.w(TAG, "stt init failed " + errorType);
                main.post(() -> events.onInit(false));
            }
        });
        stt.addListener(new ResultListener() {
            @Override
            public void onUpdated(String text, boolean isFinal) {
                main.post(() -> {
                    if (events == null || text == null) return;
                    if (isFinal) events.onFinal(text);
                    else events.onPartial(text);
                });
            }

            @Override
            public void onUpdatedEpdData(long a, long b) {
            }

            @Override
            public void onStartedRecognition() {
            }

            @Override
            public void onEndedRecognition() {
            }

            @Override
            public void onError(ErrorType errorType) {
                main.post(() -> {
                    if (events != null) events.onError(String.valueOf(errorType));
                });
            }

            @Override
            public void onReady() {
            }

            @Override
            public void onUpdatedRms(double v) {
            }
        });
    }

    /** Starts one recognition. Results arrive through {@link Events}. */
    public boolean request() {
        if (!ready) return false;
        try {
            stt.request();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "request failed", e);
            return false;
        }
    }

    public void stop() {
        if (!ready) return;
        try {
            stt.stop();
        } catch (Exception e) {
            Log.w(TAG, "stop failed", e);
        }
    }

    public void release() {
        try {
            stt.release();
        } catch (Exception e) {
            Log.w(TAG, "release failed", e);
        }
        ready = false;
    }
}
