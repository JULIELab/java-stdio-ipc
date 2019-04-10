package de.julielab.ipc.javabridge;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

public abstract class Reader<T> extends Thread {
    protected InputStream is;
    protected Predicate<T> resultLineIndicator;
    protected String externalProgramReadySignal;
    protected BlockingQueue<T> inputDeque;

    public Reader(InputStream is, Predicate<T> resultLineIndicator, String externalProgramReadySignal) {
        this.is = is;
        this.resultLineIndicator = resultLineIndicator;
        this.externalProgramReadySignal = externalProgramReadySignal;
        this.inputDeque = new LinkedBlockingQueue<>();
    }

    public BlockingQueue<T> getInputDeque() {
        return inputDeque;
    }

    public void close() throws IOException {
        is.close();
    }
}
