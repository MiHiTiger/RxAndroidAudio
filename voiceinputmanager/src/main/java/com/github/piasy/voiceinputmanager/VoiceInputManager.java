package com.github.piasy.voiceinputmanager;

import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import com.github.piasy.rxandroidaudio.AudioRecorder;
import com.github.piasy.rxandroidaudio.RxAmplitude;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by Piasy{github.com/Piasy} on 16/2/29.
 */
public final class VoiceInputManager {
    static final String TAG = "VoiceInputManager";

    private static final int DEFAULT_MIN_AUDIO_LENGTH_SECONDS = 2;
    private static final int DEFAULT_MAX_AUDIO_LENGTH_SECONDS = 15;

    private AudioRecorder mAudioRecorder;
    private final File mAudioFilesDir;
    private final EventListener mEventListener;
    private ExecutorService mExecutorService;
    private final Handler mMainThreadHandler;

    private final int mMinAudioLengthSeconds;
    private final int mMaxAudioLengthSeconds;

    private File mAudioFile;

    private VoiceInputState mVoiceInputState;
    private final Runnable mOnErrorRunnable = new Runnable() {
        @Override
        public void run() {
            mEventListener.onError();
        }
    };
    private Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            timeout();
        }
    };
    private Subscription mAmplitudeSubscription;

    static class SucceedRunnable implements Runnable {
        private final EventListener mEventListener;
        private final File mAudioFile;
        private final int mDuration;

        SucceedRunnable(EventListener eventListener, File audioFile, int duration) {
            mEventListener = eventListener;
            mAudioFile = audioFile;
            mDuration = duration;
        }

        @Override
        public void run() {
            mEventListener.onSucceed(mAudioFile, mDuration);
        }
    }

    private static volatile VoiceInputManager sInstance;

    public static VoiceInputManager getInstance(AudioRecorder audioRecorder, File audioFilesDir,
            EventListener eventListener) {
        return getInstance(audioRecorder, audioFilesDir, eventListener,
                DEFAULT_MIN_AUDIO_LENGTH_SECONDS, DEFAULT_MAX_AUDIO_LENGTH_SECONDS);
    }

    public static VoiceInputManager getInstance(AudioRecorder audioRecorder, File audioFilesDir,
            EventListener eventListener, int minAudioLengthSeconds, int maxAudioLengthSeconds) {
        if (sInstance == null) {
            synchronized (VoiceInputManager.class) {
                if (sInstance == null) {
                    sInstance = new VoiceInputManager(audioRecorder, audioFilesDir, eventListener,
                            minAudioLengthSeconds, maxAudioLengthSeconds);
                }
            }
        }
        return sInstance;
    }

    private VoiceInputManager(AudioRecorder audioRecorder, File audioFilesDir,
            EventListener eventListener, int minAudioLengthSeconds, int maxAudioLengthSeconds) {
        mAudioRecorder = audioRecorder;
        mAudioFilesDir = audioFilesDir;
        mEventListener = eventListener;
        mMinAudioLengthSeconds = minAudioLengthSeconds;
        mMaxAudioLengthSeconds = maxAudioLengthSeconds;
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public interface EventListener {
        /**
         * This let you play some ready sound after prepared but before start recording.
         */
        @WorkerThread
        void onPrepared();

        /**
         * Will only be called when the recorded file length >= {@link #mMinAudioLengthSeconds}
         */
        @MainThread
        void onSucceed(File audioFile, int duration);

        @MainThread
        void onAmplitudeChanged(int level);

        @MainThread
        void onExpireCountdown(int second);

        @MainThread
        void onError();
    }

    public synchronized void init() {
        mVoiceInputState = VoiceInputState.init(this);
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    public synchronized void toggleOn() {
        Log.d(TAG, "before toggleOn " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.pressed();
        Log.d(TAG, "after toggleOn " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    public synchronized void toggleOff() {
        Log.d(TAG, "before toggleOff " + mVoiceInputState + " @ " + System.currentTimeMillis());
        if (mAmplitudeSubscription != null && !mAmplitudeSubscription.isUnsubscribed()) {
            mAmplitudeSubscription.unsubscribe();
        }
        mMainThreadHandler.removeCallbacks(mTimeoutRunnable);
        mVoiceInputState = mVoiceInputState.released();
        Log.d(TAG, "after toggleOff " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    public synchronized void reset() {
        resetState();
    }

    void postTask(final Runnable runnable) {
        Log.d(TAG, "postTask in " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mExecutorService.submit(runnable);
    }

    void startRecord() {
        Log.d(TAG, "startRecord in " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mAudioFile = new File(mAudioFilesDir.getAbsolutePath() +
                File.separator + System.currentTimeMillis() + ".file.m4a");
        boolean prepared = mAudioRecorder.prepareRecord(MediaRecorder.AudioSource.MIC,
                MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC, mAudioFile);
        if (!prepared) {
            resetState();
            mMainThreadHandler.post(mOnErrorRunnable);
            return;
        }

        Log.d(TAG, "before onPrepared in " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mEventListener.onPrepared();
        Log.d(TAG, "after onPrepared in " + mVoiceInputState + " @ " + System.currentTimeMillis());
        boolean started = mAudioRecorder.startRecord();
        if (!started) {
            resetState();
            mMainThreadHandler.post(mOnErrorRunnable);
            return;
        }

        elapsed();
        mMainThreadHandler.postDelayed(mTimeoutRunnable, mMaxAudioLengthSeconds * 1000);
        mAmplitudeSubscription = RxAmplitude.from(mAudioRecorder)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer level) {
                        int progress = mAudioRecorder.progress();
                        Log.d(TAG, "RxAmplitude " + progress + ", " + level + " in " +
                                mVoiceInputState + " @ " + System.currentTimeMillis());
                        mEventListener.onAmplitudeChanged(level);

                        if (progress >= mMaxAudioLengthSeconds - 3) {
                            mEventListener.onExpireCountdown(mMaxAudioLengthSeconds - progress);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mEventListener.onError();
                    }
                });
    }

    void stopRecord() {
        Log.d(TAG, "stopRecord in " + mVoiceInputState + " @ " + System.currentTimeMillis());
        final int duration = mAudioRecorder.stopRecord();
        Log.d(TAG, "stop succeed " + duration + " s, in " + mVoiceInputState + " @ " +
                System.currentTimeMillis());
        if (duration >= mMinAudioLengthSeconds) {
            mMainThreadHandler.post(new SucceedRunnable(mEventListener, mAudioFile, duration));
        }
        elapsed();
    }

    private void elapsed() {
        Log.d(TAG, "before elapsed " + mVoiceInputState + " @ " + System.currentTimeMillis());
        mVoiceInputState = mVoiceInputState.elapsed();
        Log.d(TAG, "after elapsed " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private void timeout() {
        Log.d(TAG, "before timeout " + mVoiceInputState + " @ " + System.currentTimeMillis());
        if (mAmplitudeSubscription != null && !mAmplitudeSubscription.isUnsubscribed()) {
            mAmplitudeSubscription.unsubscribe();
        }
        mVoiceInputState = mVoiceInputState.timeout();
        Log.d(TAG, "after timeout " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }

    private void resetState() {
        Log.d(TAG, "before resetState " + mVoiceInputState + " @ " + System.currentTimeMillis());
        if (mAmplitudeSubscription != null && !mAmplitudeSubscription.isUnsubscribed()) {
            mAmplitudeSubscription.unsubscribe();
        }
        mVoiceInputState = VoiceInputState.init(this);
        mAudioRecorder.stopRecord();
        mExecutorService.shutdown();
        Log.d(TAG, "after resetState " + mVoiceInputState + " @ " + System.currentTimeMillis());
    }
}
