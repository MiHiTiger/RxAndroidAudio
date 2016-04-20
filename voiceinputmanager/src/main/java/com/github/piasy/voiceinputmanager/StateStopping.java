package com.github.piasy.voiceinputmanager;

/**
 * Created by Piasy{github.com/Piasy} on 3/10/16.
 */
final class StateStopping extends VoiceInputState {

    StateStopping(VoiceInputManager manager) {
        super(manager);
    }

    @Override
    VoiceInputState elapsed() {
        return new StateIdle(mManager);
    }

    @Override
    public String toString() {
        return "StateStopping";
    }
}
