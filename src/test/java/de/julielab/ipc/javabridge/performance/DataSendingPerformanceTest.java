package de.julielab.ipc.javabridge.performance;

import de.julielab.ipc.javabridge.Options;
import de.julielab.ipc.javabridge.StdioBridge;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * This class computes performance speed values for larger amounts of data sent through the pipe.
 */
public class DataSendingPerformanceTest {
    private int numRuns = 1;
    private String data;
    @Test
    public void run() throws Exception {
        data = IOUtils.toString(new FileInputStream("src/test/resources/dummydata.txt"), "UTF-8");
        long timeString = runStringExchange();
        long timeBinaryDoubles = runBinaryDoubleExchange();

        System.out.println("String: " + timeString);
        System.out.println("BinaryDoubles: " + timeBinaryDoubles);
    }

    public long runStringExchange() throws Exception {
        Options<byte[]> options = new Options<>(byte[].class);
        options.setExternalProgramTerminationSignal("exit");
        options.setExecutable("python");

        StdioBridge<byte[]> bridge = new StdioBridge<>(options, "-u", "src/test/resources/python/dataio/gzipstringsending.py");
        bridge.start();
        long time = System.currentTimeMillis();
        for (int i = 0; i < numRuns; i++) {
            final byte[] stringVector = bridge.sendAndReceive(data.getBytes()).findAny().get();
//            final double[] doubles = Stream.of(stringVector.split("[\\]\\[,]")).filter(Predicate.not(String::isEmpty)).mapToDouble(Double::parseDouble).toArray();
        }
        time = System.currentTimeMillis() - time;
        bridge.stop();
        return time;
    }

    public long runBinaryDoubleExchange() throws Exception {
        Options<byte[]> options = new Options<>(byte[].class);
        options.setExternalProgramTerminationSignal("exit");
        options.setExecutable("python");

        StdioBridge<byte[]> bridge = new StdioBridge<>(options, "-u", "src/test/resources/python/numeric/binarydoubleio.py");
        bridge.start();
        long time = System.currentTimeMillis();
        for (int i = 0; i < numRuns; i++) {
            final byte[] byteArrayVector = bridge.sendAndReceive("dummy").findAny().get();
            DoubleBuffer db = ByteBuffer.wrap(byteArrayVector).asDoubleBuffer();
            double[] array = new double[db.capacity()];
            for (int j = 0; j < db.capacity(); j++)
                array[j] = db.get(j);
        }
        time = System.currentTimeMillis() - time;
        bridge.stop();
        return time;
    }
}
