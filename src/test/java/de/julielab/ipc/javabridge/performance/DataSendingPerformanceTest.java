package de.julielab.ipc.javabridge.performance;

import de.julielab.ipc.javabridge.Options;
import de.julielab.ipc.javabridge.StdioBridge;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;

import java.io.FileInputStream;

/**
 * This class computes performance speed values for larger amounts of data sent through the pipe.
 */
public class DataSendingPerformanceTest {
    private int numRuns = 100;
    private String data;
    @Test
    public void run() throws Exception {
        data = IOUtils.toString(new FileInputStream("src/test/resources/dummydata.txt"), "UTF-8");
        long timeGzip = runGzip();
        long timeUncompressed = runUncompressed();

        System.out.println("Gzip: " + timeGzip);
        System.out.println("Uncompressed: " + timeUncompressed);
    }

    public long runGzip() throws Exception {
        Options<String> options = new Options<>(String.class);
        options.setExternalProgramTerminationSignal("exit");
        options.setExecutable("python");
        options.setGzipSentData(true);

        StdioBridge<String> bridge = new StdioBridge<>(options, "-u", "src/test/resources/python/dataio/gzipstringsending.py");
        bridge.start();
        long time = System.currentTimeMillis();
        for (int i = 0; i < numRuns; i++) {
            final String response = bridge.sendAndReceive(data).findAny().get();
        }
        time = System.currentTimeMillis() - time;
        bridge.stop();
        return time;
    }

    public long runUncompressed() throws Exception {
        Options<String> options = new Options<>(String.class);
        options.setExternalProgramTerminationSignal("exit");
        options.setExecutable("python");

        StdioBridge<String> bridge = new StdioBridge<>(options, "-u", "src/test/resources/python/simple.py");
        bridge.start();
        long time = System.currentTimeMillis();
        for (int i = 0; i < numRuns; i++) {
            final String response = bridge.sendAndReceive("dummy").findAny().get();
        }
        time = System.currentTimeMillis() - time;
        bridge.stop();
        return time;
    }
}
