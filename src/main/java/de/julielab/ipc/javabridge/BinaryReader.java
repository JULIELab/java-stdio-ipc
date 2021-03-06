package de.julielab.ipc.javabridge;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BinaryReader extends Reader<byte[]> {
    private final static Logger log = LoggerFactory.getLogger(BinaryReader.class);

    private static final int INT_SIZE = 4;
    private List<byte[]> messageBuffer;
    private List<Integer> messageBufferSizes;
    private boolean gzipReceived;

    public BinaryReader(InputStream is, String externalProgramReadySignal, boolean gzipReceived) {
        super(is, null, externalProgramReadySignal);
        this.gzipReceived = gzipReceived;
    }

    public void run() {
        setName("BinaryReaderThread");
        log.debug("Starting binary reader thread");
        try {
            if (externalProgramReadySignal != null) {
                log.debug("Waiting for the signal that the external program is ready ('{}')", externalProgramReadySignal);
                String lastLine = "";
                StringBuilder currentLine = new StringBuilder();
                byte[] eol = System.getProperty("line.separator").getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (!lastLine.equals(externalProgramReadySignal)) {
                    boolean foundEol = false;
                    while (!foundEol) {
                        if (buffer.position() == buffer.limit()) {
                            currentLine.append(new String(buffer.array(), StandardCharsets.UTF_8));
                            buffer.clear();
                        }
                        buffer.put((byte) is.read());
                        if (buffer.position() > eol.length) {
                            foundEol = true;
                            for (int i = 0; i < eol.length && foundEol; i++)
                                foundEol &= eol[i] == buffer.get(i + buffer.position() - eol.length);
                            if (foundEol) {
                                byte[] bytes = new byte[buffer.position() - eol.length];
                                int length = buffer.position() - eol.length;
                                buffer.position(0);
                                buffer.get(bytes, 0, length);
                                currentLine.append(new String(bytes, StandardCharsets.UTF_8));
                                lastLine = currentLine.toString();
                                currentLine.setLength(0);
                            }
                        }
                    }
                    buffer.position(0);
                    if (!lastLine.equals(externalProgramReadySignal))
                        log.debug("Received non-ready signal line '{}'", lastLine);
                }
                log.debug("Received ready signal");
            }


            Supplier<byte[]> bufferSupplier = () -> new byte[8192];
            byte[] buffer = bufferSupplier.get();
            int lastReadSize;
            int bytesReadInCurrentMessage = 0;
            int currentMessageLength = -1;
            messageBuffer = new ArrayList<>();
            messageBufferSizes = new ArrayList<>();
            byte[] currentMessage;
            long time = -1;
            while ((lastReadSize = is.read(buffer)) != -1) {
                if (time == -1)
                    time = System.currentTimeMillis();
                bytesReadInCurrentMessage += lastReadSize;
                log.trace("Received: {} bytes, total: {}", lastReadSize, bytesReadInCurrentMessage);
                messageBufferSizes.add(lastReadSize);
                messageBuffer.add(buffer);
                if (currentMessageLength == -1 && bytesReadInCurrentMessage >= INT_SIZE) {
                    currentMessageLength = readMessageLength();
                }

                while (currentMessageLength != -1 && bytesReadInCurrentMessage - INT_SIZE >= currentMessageLength) {
                    currentMessage = assembleCurrentMessage(currentMessageLength);
                    if (gzipReceived) {
                        final ByteArrayInputStream bais = new ByteArrayInputStream(currentMessage);
                        final BufferedInputStream bis = new BufferedInputStream(new GZIPInputStream(bais));
                        currentMessage = IOUtils.toByteArray(bis);
                    }
                    inputDeque.add(currentMessage);
                    log.trace("Added message of length {} bytes to the queue", currentMessage.length);
                    time = System.currentTimeMillis() - time;
                    log.trace("Retrieving and assembling last message took {}ms", time);
                    time = System.currentTimeMillis();
                    bytesReadInCurrentMessage = messageBufferSizes.isEmpty() ? 0 : messageBufferSizes.stream().reduce(0, (a, b) -> a + b);
                    currentMessageLength = readMessageLength();
                }

                buffer = bufferSupplier.get();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("BinaryReader thread terminates.");
    }

    private byte[] assembleCurrentMessage(int currentMessageLength) {
        byte[] currentMessage;// Get the data of the current message
        currentMessage = new byte[currentMessageLength];
        int copied = 0;
        int prologPast = 0;
        boolean clearBuffer = true;
        for (int i = 0; i < messageBuffer.size(); i++) {
            // First thing: Skip over the message length integer
            byte[] b = messageBuffer.get(i);
            Integer readBytesLength = messageBufferSizes.get(i);
            int prologInCurrentBuffer = 0;
            if (readBytesLength > INT_SIZE && prologPast == 0) {
                prologPast = INT_SIZE;
                prologInCurrentBuffer = prologPast;
            } else if (prologPast < INT_SIZE && prologPast + readBytesLength <= INT_SIZE) {
                prologPast += readBytesLength;
                continue;
            } else if (prologPast < INT_SIZE) {
                int missingPrologSize = INT_SIZE - prologPast;
                prologPast += missingPrologSize;
                prologInCurrentBuffer = missingPrologSize;
            }

            // Now copy as much from the current message as is available (might well be the whole message)
            int toCopy = Math.min(currentMessageLength - copied, readBytesLength - prologInCurrentBuffer);
            System.arraycopy(b, prologInCurrentBuffer, currentMessage, copied, toCopy);
            copied += toCopy;

            // Handle the case that we have read the current message and also have the beginning of a next message
            if (toCopy + prologInCurrentBuffer < readBytesLength) {
                clearBuffer = false;
                int byteLength = readBytesLength - (toCopy + prologInCurrentBuffer);
                byte[] nextMessageBegin = new byte[byteLength];
                System.arraycopy(b, toCopy + prologInCurrentBuffer, nextMessageBegin, 0, byteLength);
                List<byte[]> newMessageBuffer = new ArrayList<>(messageBuffer.size());
                List<Integer> newMessageBufferSizes = new ArrayList<>(messageBufferSizes.size());
                newMessageBuffer.add(nextMessageBegin);
                newMessageBufferSizes.add(byteLength);
                for (int j = i + 1; j < messageBuffer.size(); j++) {
                    newMessageBuffer.add(messageBuffer.get(j));
                    newMessageBufferSizes.add(messageBufferSizes.get(j));
                }
                messageBuffer = newMessageBuffer;
                messageBufferSizes = newMessageBufferSizes;
                log.trace("Remaining message buffer length: {}", messageBuffer.get(0).length);
                // Stop copying data for the current message!
                break;
            }
        }
        if (clearBuffer) {
            // In case it just fit exactly (thus, we have read exactly the bytes of one message),
            // just clear the buffers.
            messageBuffer.clear();
            messageBufferSizes.clear();
        }
        log.trace("Copied {} bytes for the current message", copied);
        return currentMessage;
    }

    private int readMessageLength() {
        int currentMessageLength;
        // ONLY get the message length if we we even have enough bytes do determine it
        if (!messageBuffer.isEmpty() && messageBufferSizes.stream().reduce(0, (a, b) -> a + b) >= INT_SIZE) {
            byte[] intBytes = new byte[INT_SIZE];
            int pos = 0;
            for (int i = 0; i < messageBuffer.size(); i++) {
                byte[] b = messageBuffer.get(i);
                Integer readBytesLength = messageBufferSizes.get(i);
                int toCopy = Math.min(readBytesLength, intBytes.length - pos);
                System.arraycopy(b, 0, intBytes, pos, toCopy);
                pos += toCopy;
                if (pos >= INT_SIZE)
                    break;
            }

            // the &0xff operation removes the sign from the bytes; this is important we implictly convert
            // bytes to integers. There it happens that when a byte was starting with a 1, that this is
            // interpreted as a negative number (2-complement representation). Thus, the resulting integer
            // will have its first bit set to a 1 to keep the negative sign. We neutralize this using
            // the 11111111=0xff mask.
            currentMessageLength = ((intBytes[0] & 0xff) << 24) | ((intBytes[1] & 0xff) << 16) | ((intBytes[2] & 0xff) << 8) | (intBytes[3] & 0xff);
            log.trace("Current message size is {} bytes", currentMessageLength);
            return currentMessageLength;
        }
        return -1;
    }
}
