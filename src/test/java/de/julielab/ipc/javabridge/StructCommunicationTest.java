package de.julielab.ipc.javabridge;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
public class StructCommunicationTest {
    @Test
    public void test() throws InterruptedException, IOException {
        Options<byte[]> options = new Options( byte[].class);
        options.setExecutable("python");
        options.setExternalProgramTerminationSignal("exit");
        StdioBridge<byte[]> bridge = new StdioBridge(options, "-u", "src/test/resources/python/arrayVectorExchange.py");
        bridge.start();

        Stream<byte[]> response = bridge.sendAndReceive("blabla");
        byte[] bytes = response.findAny().get();
        DoubleBuffer db = ByteBuffer.wrap(bytes).asDoubleBuffer();
        double[] array = new double[db.capacity()];
        for (int i = 0; i < db.capacity(); i++)
            array[i] = db.get(i);
        assertThat(array).isEqualTo(new double[]{0.1, 0.2, 0.3, -0.4, 0, 42.1337});

        // In this example, each send produces TWO responses. It currently only possible to fetch them
        // via one receive() for each response. This would actually make the returning of a Stream
        // obsolete. Will perhaps be changed in the future. Also, it would be nice to have an information
        // about how many responses to expect.
        response = bridge.receive();
        bytes = response.findAny().get();
        db = ByteBuffer.wrap(bytes).asDoubleBuffer();
        array = new double[db.capacity()];
        for (int i = 0; i < db.capacity(); i++)
            array[i] = db.get(i);
        assertThat(array).isEqualTo(new double[]{0.7, 0.8, 0.9});
    }

}


