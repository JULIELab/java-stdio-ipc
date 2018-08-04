package de.julielab.ipc.javabridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

public class StringReader extends Reader<String> {
    private final static Logger log = LoggerFactory.getLogger(StringReader.class);
    private final BufferedReader br;

    public StringReader(InputStream is, Predicate<String> resultLineIndicator) {
        super(is, resultLineIndicator);
        br = new BufferedReader(new InputStreamReader(is));
    }

    public void run() {
        setName("ReaderThread");
        log.debug("Starting reader thread");
        String line;
        try {
            while ((line = br.readLine()) != null) {
                synchronized (this) {
                    if (resultLineIndicator == null || resultLineIndicator.test(line)) {
                        if (line.length() > 0)
                            inputDeque.add(line);
                        notify();
                    }
                }
                log.trace("Received: {}", line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
