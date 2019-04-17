package de.julielab.ipc.javabridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Predicate;

public class StringReader extends Reader<String> {
    private final static Logger log = LoggerFactory.getLogger(StringReader.class);
    private final BufferedReader br;

    public StringReader(InputStream is, Predicate<String> resultLineIndicator, String externalProgramReadySignal) {
        super(is, resultLineIndicator, externalProgramReadySignal);
        br = new BufferedReader(new InputStreamReader(is));
    }

    public void run() {
        setName("ReaderThread");
        log.debug("Starting reader thread");
        String line;
        boolean externalReadySignalSent = externalProgramReadySignal == null;
        try {
            if (externalProgramReadySignal != null)
                log.debug("Waiting for the signal that the external program is ready ('{}')", externalProgramReadySignal);
            while (!externalReadySignalSent) {
                line = br.readLine();
                externalReadySignalSent = line.equals(externalProgramReadySignal);
            }
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
        log.debug("String reader thread terminates" );
    }
}
