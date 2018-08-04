package de.julielab.ipc.javabridge;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

public class StructCommunicationTest {
    @Test
    public void test() throws InterruptedException, IOException {
        Options<byte[]> options = new Options(null, byte[].class);
        options.setExecutable("python");
        StdioBridge<byte[]> bridge = new StdioBridge(options, "-u", "src/test/resources/python/arrayVectorExchange.py");
        bridge.start();
        bridge.send("nonsense");
        while (true) {
            Stream<byte[]> responses = bridge.receive();
            Optional<byte[]> bytes = responses.findAny();
            while (!bytes.isPresent()) {
                responses = bridge.receive();
                bytes = responses.findAny();
            }
            DoubleBuffer db = ByteBuffer.wrap(bytes.get()).asDoubleBuffer();
            for (int i = 0; i < db.capacity(); ++i)
                System.out.println("Hier: " + db.get(i));
        }

    }

    @Test
    public void test2() {
        byte[] b = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        DoubleBuffer db = ByteBuffer.wrap(b).asDoubleBuffer();
        System.out.println(db.get());
    }
}
