package com.github.piasy.voiceinputmanager;

/**
 * Created by Piasy{github.com/Piasy} on 3/10/16.
 */
final class StatePreparing extends VoiceInputState {
    private final Runnable mStopRecord = new Runnable() {
        @Override
        public void run() {
            mManager.stopRecord();
        }
    };

    StatePreparing(VoiceInputManager manager) {
        super(manager);
    }

    @Override
    VoiceInputState released() {
        mManager.postTask(mStopRecord);
        return new StateIdle(mManager);
    }

    @Override
    VoiceInputState elapsed() {
        return new StateRecording(mManager);
    }

    @Override
    public String toString() {
        return "StatePreparing";
    }
}
