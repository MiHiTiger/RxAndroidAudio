package com.github.piasy.voiceinputmanager;

/**
 * Created by Piasy{github.com/Piasy} on 3/10/16.
 */
abstract class VoiceInputState {
    protected final VoiceInputManager mManager;

    protected VoiceInputState(VoiceInputManager manager) {
        mManager = manager;
    }

    static VoiceInputState init(VoiceInputManager manager) {
        return new StateIdle(manager);
    }

    VoiceInputState pressed() {
        return this;
    }

    VoiceInputState released() {
        return this;
    }

    VoiceInputState elapsed() {
        return this;
    }

    VoiceInputState timeout() {
        return this;
    }
}
