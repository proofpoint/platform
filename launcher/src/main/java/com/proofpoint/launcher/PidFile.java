/*
 * Copyright 2012 Proofpoint, Inc.
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
package com.proofpoint.launcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.LockSupport;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("FieldCanBeLocal")
class PidFile implements PidStatusSource
{
    // Starting , Refreshing, Pausing, Paused, Resuming, Stopping, Running
    private static final int STARTING = 1;
    private static final int RUNNING = 7;
    private static final int NOT_YET_RUNNING = 10;

    private FileChannel pidChannel = null;
    private FileChannel lockChannel = null;
    private FileLock startLock = null;
    @SuppressWarnings("unused") // Need to hold this lock while the server is running
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    private FileLock runningLock = null;
    private FileLock notYetRunningLock = null;

    PidFile(String pidFilePath)
    {
        requireNonNull(pidFilePath, "pidFilePath is null");

        if (!new File(pidFilePath).getParentFile().mkdirs()) {
            // ignore failure
        }
        try {
            pidChannel = new RandomAccessFile(pidFilePath, "rw").getChannel();
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot open pid file: " + e);
        }

        if (System.getProperty("os.name").startsWith("Windows")) {
            // Windows file locks are mandatory: use a separate lock file so that
            // the pid file can be read by other processes
            try {
                lockChannel = new RandomAccessFile(pidFilePath + ".lck", "rw").getChannel();
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException("Cannot open pid lockfile: " + e);
            }
        }
        else {
            lockChannel = pidChannel;
        }
    }

    // Protect against finalizer attacks, as constructor can throw exception.
    @SuppressWarnings("deprecation")
    @Override
    protected final void finalize()
    {
    }

    void indicateStarting()
            throws AlreadyRunningError
    {
        try {
            // If obtained, startLock will be held for the lifetime of this process,
            // indicating that this is the active server.
            startLock = lockChannel.tryLock(0, STARTING, false);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        if (startLock == null) {
            throw new AlreadyRunningError(readPid());
        }

        // There is a race here between the time we lock the file and the
        // time we write our pid into it. This could be fixed by readers using
        // the pid returned by fcntl(F_GETLK) instead of the file contents, but
        // have not yet found a way to call fcntl(F_GETLK) from Java.
        try {
            pidChannel.truncate(0);
            pidChannel.write(ByteBuffer.wrap((Integer.toString(Processes.getpid()) + "\n").getBytes(US_ASCII)));
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot write to pid file: " + e);
        }

        try {
            // notYetRunningLock is released in indicateRunning(). The parent
            // process, running the launcher 'start' command, will block on this
            // in waitRunning().
            notYetRunningLock = lockChannel.lock(NOT_YET_RUNNING - 1, 1, false);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }
    }

    @SuppressFBWarnings(value = "OS_OPEN_STREAM", justification = "false positive")
    private int readPid()
    {
        for (int i = 0; i < 10; ++i) {
            try {
                pidChannel.position(0);
                String line = new BufferedReader(Channels.newReader(pidChannel, "us-ascii")).readLine();
                if (line != null) {
                    return Integer.decode(line);
                }
            }
            catch (IOException | NumberFormatException e) {
                throw new RuntimeException("Cannot read pid file: " + e);
            }
            LockSupport.parkNanos(10_000_000);
        }
        return 0;
    }

    void indicateRunning()
    {
        // runningLock is held for the lifetime of this process, indicating that
        // this server has completed startup.
        try {
            runningLock = lockChannel.lock(STARTING, RUNNING - STARTING, false);
            notYetRunningLock.release();
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        notYetRunningLock = null;
    }

    @Override
    public PidStatus getStatus()
    {
        FileLock fileLock;
        try {
            fileLock = lockChannel.tryLock(STARTING, NOT_YET_RUNNING - STARTING, true);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        return getPidStatus(fileLock);
    }

    public PidStatus getRunning()
    {
        FileLock fileLock;
        try {
            fileLock = lockChannel.tryLock(STARTING, RUNNING - STARTING, true);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        return getPidStatus(fileLock);
    }

    private PidStatus getPidStatus(FileLock fileLock)
    {
        if (fileLock == null) {
            return PidStatus.heldBy(readPid());
        }

        try {
            fileLock.release();
        }
        catch (IOException ignored) {
        }

        return PidStatus.notHeld();
    }

    PidStatus waitRunning()
    {
        FileLock fileLock;
        try {
            fileLock = lockChannel.lock(NOT_YET_RUNNING - 1, 1, true);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        try {
            fileLock.release();
        }
        catch (IOException ignored) {
        }

        try {
            // Possible race: a child that hasn't yet locked NOT_YET_RUNNING.
            // We only check the lock up to RUNNING so that the returned PidStatus
            // will have held set to false in that case.
            fileLock = lockChannel.tryLock(STARTING, RUNNING - STARTING, true);
        }
        catch (IOException e) {
            throw new RuntimeException("Cannot lock pid file: " + e);
        }

        return getPidStatus(fileLock);
    }
}
