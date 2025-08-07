package com.github.sinakarimi.jdown.download;

import java.io.IOException;
import java.io.InputStream;

public class PausableInputStream extends InputStream {

    private final InputStream source;
    private final Object lock = new Object();
    private volatile boolean paused = false;

    public PausableInputStream(InputStream source) {
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        waitForResume();
        return source.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        waitForResume();
        return source.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        waitForResume();
        return source.read(b);
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    public void resume() {
        synchronized (lock) {
            paused = false;
            lock.notifyAll();
        }
    }

    public void pause() {
        synchronized (lock) {
            paused = true;
        }
    }

    private void waitForResume() throws IOException {
        synchronized (lock) {
            while (paused) {
                try {
                    lock.wait(); // Block until resumed
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted while paused", e);
                }
            }
        }
    }
}
