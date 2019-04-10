package de.julielab.ipc.javabridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * <p>
 * This class offers a possibility to communicate from within a Java application with another, external program.
 * </p>
 * <p>
 * This is useful for the connection to applications written in a language for which no direct binding from Java exist.
 * This class resorts to the simple exchange of messages via standard
 * input and output, thus, a pipe. For this purpose, the external process is started using a {@link ProcessBuilder}.
 * Once the external process is started, messages can be send to the process via {@link #send(byte[])} and
 * {@link #sendAndReceive(byte[])}. After a message is sent, an answer can be received via {@link #receive()}.
 * </p>
 * <p>The {@link #send(byte[])} method is <em>synchronous</em>, i.e. this class waits until the given message
 * has been sent to the external process. To not miss the answer, a background thread is always reading lines
 * from the standard input of the external process. To get these messages, call {@link #receive()}. This method
 * either gets existing messages that already have been read or it <em>waits</em> for a message to arrive. Thus,
 * the method might block <em>indefinitely</em> in case that no message is sent by the external process.
 * This is why calls to {@link #send(byte[])} and {@link #receive()} should always come in pairs. For this purpose,
 * the method {@link #sendAndReceive(byte[])} is helpful.</p>
 * <p>This class supports arbitrary requests and one-line responses by default. By setting the {@link Options#multilineResponseDelimiter} field,
 * multiple lines can be read for each request.
 * </p><p>
 * If the external
 * program outputs logging that is not a result in the sense of this class the actual output line must be marked, e.g. by a prefix like
 * <code>Result:</code> or similar</p>
 * <p>The {@link Options} class accepts a {@link Predicate} to recognize such specific output lines. The Options
 * class also allows setting the actual program command whereas the program arguments are set to the descriptor of this
 * class. Refer to {@link Options} for details about configuration settings offered there.</p>
 * <p>The terminate the external process and the receiver thread and also the STDERR reading thread, call
 * {@link #stop()} one the bridge is no longer required.</p>
 * <p>IMPORTANT NOTE FOR PYTHON PROGRAMS: When working with Python for the external program, always specify the <em>-u</em>
 * switch in the program arguments. It causes Python to refrain from internal input/output buffering. Without the switch,
 * the communication between Java and Python via STDOUT/IN will most like get stuck because the buffers don't get
 * filled at some point an no data is actually sent.</p>
 */
public class StdioBridge<O> {

    private final static Logger log = LoggerFactory.getLogger(StdioBridge.class);

    private String[] arguments;
    private Process process;
    private GenericCommunicator communicator;
    private ErrorStreamConsumer errorStreamConsumer;
    private Options<O> options;

    public StdioBridge(Options<O> options, String... arguments) {
        this.options = options;
        if (arguments.length == 0)
            throw new IllegalArgumentException("No external program to run has been specified.");
        this.arguments = arguments;
    }


    public void start() throws IOException {
        String[] command = new String[arguments.length + 1];
        command[0] = options.getExecutable();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        ProcessBuilder builder = new ProcessBuilder(command);
        process = builder.start();
        errorStreamConsumer = new
                ErrorStreamConsumer(process.getErrorStream());
        errorStreamConsumer.start();
        log.info("Started process with arguments {}", Arrays.toString(arguments));
        BufferedInputStream bis = new BufferedInputStream(process.getInputStream());
        BufferedOutputStream bos = new BufferedOutputStream(process.getOutputStream());

        Reader<O> r;
        if (options.getResultType().equals(String.class))
            r = (Reader<O>) new StringReader(bis, (Predicate<String>) options.getResultLineIndicator());
        else if (options.getResultType().equals(byte[].class))
            r = (Reader<O>) new BinaryReader(bis, (Predicate<byte[]>) options.getResultLineIndicator(), options.isGzipReceivedData());
        else
            throw new IllegalArgumentException("The result type must be String or byte[] but was " + options.getResultType());
        communicator = new GenericCommunicator<O>(r, bos, options.getMultilineResponseDelimiter(), options.isGzipSentData());
    }

    public void stop() throws InterruptedException {
        if (options.getExternalProgramTerminationSignal() != null) {
            communicator.send(options.getExternalProgramTerminationSignal().getBytes());
            log.info("Sent the external process termination signal \"{}\" and waiting for the process to end.", options.getExternalProgramTerminationSignal());
            process.waitFor();
        }
        if (communicator != null)
            communicator.close();
        if (errorStreamConsumer != null)
            errorStreamConsumer.close();
        if (process != null) {
            if (process.isAlive()) {
                process.destroy();
                process.waitFor();
            }
            int exitValue = process.exitValue();
            log.info("Process exited with exit value {}. The run arguments was: {}", exitValue, Arrays.toString(arguments));
        }
    }

    /**
     * Synchronously sends the given string data to the external program. It must be programmed in a way to accept
     * these data.
     *
     * @param data The message to be sent to the external process.
     */
    public void send(byte[] data) {
        if (communicator == null)
            throw new IllegalStateException("The internal Python-Java communicator has not been initialized. Did you forget to execute start()?");
        communicator.send(data);
    }

    public void send(String data) {
        send(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>Receives data from the external process.</p>
     * <p>For this purpose, this method will <em>block</em> until data is available. If {@link Options#getResultLineIndicator()}
     * is given, the method will wait until a line is encountered that is accepted by the respective predicate.</p>
     * <p>This method should only be called if {@link #send(byte[])} has been called before. Otherwise, there will
     * probably be nothing to read the the method will block indefinitely</p>
     *
     * @return The next data line received from the external process.
     * @throws InterruptedException If the method is interrupted while waiting for the next input.
     */
    public Stream<O> receive() throws InterruptedException {
        List<O> lines = communicator.receive();
        if (options.getResultReshaper() != null) {
            Function<O, O> transformator = options.getResultReshaper();
            return lines.stream().map(transformator::apply);
        }
        return lines.stream();
    }

    /**
     * Just calls {@link #send(byte[])} and {@link #receive()} one ofter the other. Exclusively using this method
     * ensures that there is always something to read and the receive method does not block forever.
     *
     * @param data The data to send.
     * @return The received response.
     * @throws InterruptedException It waiting for a response is interrupted.
     */
    public Stream<O> sendAndReceive(byte[] data) throws InterruptedException {
        send(data);
        return receive();
    }

    public Stream<O> sendAndReceive(String data) throws InterruptedException {
        return sendAndReceive(data.getBytes(StandardCharsets.UTF_8));
    }
}


class GenericCommunicator<O> {
    private final static Logger log = LoggerFactory.getLogger(GenericCommunicator.class);
    private final Reader<O> reader;
    private final Writer writer;
    private BlockingQueue<O> inputDeque;
    private Deque<byte[]> outputDeque = new ArrayDeque<>();
    private BufferedOutputStream bos;
    private String multilineResponseDelimiter;
    private boolean gzipSent;

    public GenericCommunicator(Reader<O> reader, BufferedOutputStream bos, String multilineResponseDelimiter, boolean gzipSent) {
        this.bos = bos;
        this.multilineResponseDelimiter = multilineResponseDelimiter;
        this.gzipSent = gzipSent;
        this.writer = new Writer();
        this.reader = reader;
        this.inputDeque = reader.getInputDeque();
        this.reader.start();
    }

    public void close() {
        if (!inputDeque.isEmpty())
            log.warn("Python-Java bridge was closed before all data was received from the external program:" + inputDeque.stream().map(Object::toString).collect(Collectors.joining(", ")));
        reader.interrupt();
        if (!outputDeque.isEmpty())
            log.warn("Python-Java bridge was closed before all data was sent to the external program: " + outputDeque.stream().map(Object::toString).collect(Collectors.joining(", ")));
        inputDeque = null;
        outputDeque = null;
    }

    public void send(byte[] data) {
        outputDeque.add(data);
        writer.run();
    }

    public List<O> receive() throws InterruptedException {
        List<O> receivedData = new ArrayList<>();
        if (inputDeque == null)
            throw new IllegalStateException("This communicator has already been closed, further calls to receive() are not permitted.");
        log.trace("Waiting for something to be read");
        if (multilineResponseDelimiter == null) {
            receivedData.add(inputDeque.take());
        } else {
            O response;
            while (!(response = inputDeque.take()).equals(multilineResponseDelimiter)) {
                receivedData.add(response);
            }
        }
        log.trace("Reading from internal buffer {} messages.", receivedData.size());
        return receivedData;
    }

    public List<O> sendAndReceive(byte[] data) throws InterruptedException {
        send(data);
        return receive();
    }


    private class Writer {
        private ByteBuffer buffer = ByteBuffer.allocate(4);
        public void run() {
            try {
                while (!outputDeque.isEmpty()) {
                    byte[] toWrite = outputDeque.pop();
                    log.trace("Writing: " + toWrite);
                    if (gzipSent) {
                        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        final BufferedOutputStream bos = new BufferedOutputStream(new GZIPOutputStream(baos));
                        bos.write(toWrite);
                        bos.close();
                        toWrite = baos.toByteArray();
                    }
                    buffer.putInt(toWrite.length);
                    bos.write(buffer.array());
                    bos.write(toWrite);
                    buffer.clear();
                    // Important! When we don't flush, the data so sent will most like just reside in the buffer,
                    // at least the last part of it, and dont get sent to the external process. The external process
                    // will then probably block indefinitely, waiting for our request to finish.
                    bos.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class ErrorStreamConsumer extends Thread {
    private final static Logger log = LoggerFactory.getLogger(ErrorStreamConsumer.class);
    InputStream is;

    ErrorStreamConsumer(InputStream is) {
        this.is = is;
    }

    public void close() {
        interrupt();
    }

    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null)
                log.error(line);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}