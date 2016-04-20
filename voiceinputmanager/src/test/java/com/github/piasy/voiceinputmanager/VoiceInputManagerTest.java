package com.github.piasy.voiceinputmanager;

import com.github.piasy.rxandroidaudio.AudioRecorder;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Created by Piasy{github.com/Piasy} on 3/10/16.
 */
public class VoiceInputManagerTest {

    private AudioRecorder mAudioRecorder;
    private VoiceInputManager.EventListener mEventListener;
    private VoiceInputManager mVoiceInputManager;

    @Before
    public void setUp() {
        mAudioRecorder = mock(AudioRecorder.class);
        mEventListener = mock(VoiceInputManager.EventListener.class);
        mVoiceInputManager =
                VoiceInputManager.getInstance(mAudioRecorder, new File("."), mEventListener);
        mVoiceInputManager.init();
    }

    @After
    public void tearDown() {
        mVoiceInputManager.reset();
    }

    @Test
    public void testNormalRecord() {
        willReturn(true).given(mAudioRecorder)
                .prepareRecord(anyInt(), anyInt(), anyInt(), any(File.class));
        willReturn(true).given(mAudioRecorder).startRecord();
        willReturn(10).given(mAudioRecorder).stopRecord();

        mVoiceInputManager.toggleOn();
        sleep(500);
        mVoiceInputManager.toggleOff();
        sleep(500);

        then(mAudioRecorder).should(times(1))
                .prepareRecord(anyInt(), anyInt(), anyInt(), any(File.class));
        then(mAudioRecorder).should(times(1)).startRecord();
        then(mAudioRecorder).should(times(1)).stopRecord();

        then(mEventListener).should(times(1)).onPrepared();
        then(mEventListener).should(times(1)).onSucceed(any(File.class), anyInt());
        then(mEventListener).should(atLeastOnce()).onAmplitudeChanged(anyInt());
        then(mEventListener).should(never()).onExpireCountdown(anyInt());
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
