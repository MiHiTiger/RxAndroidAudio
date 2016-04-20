package com.github.piasy.voiceinputmanager;

/**
 * Created by Piasy{github.com/Piasy} on 3/10/16.
 */
final class StateRecording extends VoiceInputState {
    private final Runnable mStopRecord = new Runnable() {
        @Override
        public void run() {
            mManager.stopRecord();
        }
    };

    StateRecording(VoiceInputManager manager) {
        super(manager);
    }

    @Override
    VoiceInputState released() {
        mManager.postTask(mStopRecord);
        return new StateStopping(mManager);
    }

    @Override
    VoiceInputState timeout() {
        mManager.postTask(mStopRecord);
        return new StateStopping(mManager);
    }

    @Override
    public String toString() {
        return "StateRecording";
    }
}
