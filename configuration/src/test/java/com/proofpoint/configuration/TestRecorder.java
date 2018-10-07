/*
 * Copyright 2018 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.configuration;

import org.testng.annotations.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestRecorder
{
    @Test
    public void TestRecordReplays() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.setIntValue(3);
        Duration duration = Duration.ofSeconds(5);
        TestingClass returnedTestingClass = recordingObject.duration(duration);
        assertThat(returnedTestingClass).isSameAs(recordingObject);

        Replayer<TestingClass> replayer = recorder.getReplayer();
        for (int i = 0; i < 3; i++) {
            TestingClass mock = mock(TestingClass.class);
            replayer.apply(mock);
            verify(mock).setIntValue(3);
            verify(mock).duration(same(duration));
            verifyNoMoreInteractions(mock);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "may only be called once")
    public void TestDoubleGetReplayerThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.setIntValue(3);
        recorder.getReplayer();
        recorder.getReplayer();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "may not record new calls to config object after binding Module returns")
    public void TestRecordAfterGetReplayerThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.setIntValue(3);
        recorder.getReplayer();
        recordingObject.duration(Duration.ofSeconds(5));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class, expectedExceptionsMessageRegExp = "may only invoke methods with @Config annotations")
    public void TestRecordNonConfigSetterThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.setNonConfigValue(3);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class, expectedExceptionsMessageRegExp = "may only invoke single-parameter methods")
    public void TestRecordNoArgMethodThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.noArg();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class, expectedExceptionsMessageRegExp = "may only invoke single-parameter methods")
    public void TestRecordTwoArgMethodThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.twoArg(1, 2);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void TestRecordNullThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.duration(null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class, expectedExceptionsMessageRegExp = "may only invoke methods returning TestingClass or void")
    public void TestRecordUnsupportedReturnValueThrows() {
        Recorder<TestingClass> recorder = new Recorder<>(TestingClass.class);
        TestingClass recordingObject = recorder.getRecordingObject();
        recordingObject.setVoid(1);
    }

    public static class TestingClass
    {
       @Config("int-value")
        void setIntValue(int intValue) {
        }

        @Config("duration")
        TestingClass duration(Duration duration) {
            return null;
        }

        void setNonConfigValue(int intValue) {
        }

        @Config("no-arg")
        void noArg() {
        }

        @Config("two-arg")
        void twoArg(int a, int b) {
        }

        @Config("void-setter")
        Void setVoid(int intValue) {
           return null;
        }
    }
}
