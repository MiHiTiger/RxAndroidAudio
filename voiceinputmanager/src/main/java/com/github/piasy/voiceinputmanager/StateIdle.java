package com.github.piasy.voiceinputmanager;

/**
 * Created by Piasy{github.com/Piasy} on 3/10/16.
 */
final class StateIdle extends VoiceInputState {
    private final Runnable mStartRecord = new Runnable() {
        @Override
        public void run() {
            mManager.startRecord();
        }
    };

    StateIdle(VoiceInputManager manager) {
        super(manager);
    }

    @Override
    VoiceInputState pressed() {
        mManager.postTask(mStartRecord);
        return new StatePreparing(mManager);
    }

    @Override
    public String toString() {
        return "StateIdle";
    }
}
