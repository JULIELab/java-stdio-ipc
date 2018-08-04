package de.julielab.ipc.javabridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BinaryReader extends Reader<byte[]> {
    private final static Logger log = LoggerFactory.getLogger(BinaryReader.class);

    private static final int INT_SIZE = 4;
    private List<byte[]> messageBuffer;
    private List<Integer> messageBufferSizes;

    public BinaryReader(InputStream is, Predicate<byte[]> resultLineIndicator) {
        super(is, resultLineIndicator);
    }

    public void run() {
        setName("BinaryReaderThread");
        log.debug("Starting binary reader thread");
        try {
            Supplier<byte[]> bufferSupplier = () -> new byte[8192];
            byte[] buffer = bufferSupplier.get();
            int lastReadSize;
            int bytesReadInCurrentMessage = 0;
            int currentMessageLength = -1;
            messageBuffer = new ArrayList<>();
            messageBufferSizes = new ArrayList<>();
            byte[] currentMessage = null;
            while ((lastReadSize = is.read(buffer)) != -1) {
                bytesReadInCurrentMessage += lastReadSize;
                messageBufferSizes.add(lastReadSize);
                messageBuffer.add(buffer);
                if (currentMessageLength == -1 && bytesReadInCurrentMessage >= INT_SIZE) {
                    currentMessageLength = readMessageLength();
                }

                if (currentMessageLength != -1 && bytesReadInCurrentMessage - INT_SIZE >= currentMessageLength) {
                    currentMessage = assembleCurrentMessage(currentMessageLength);
                    inputDeque.add(currentMessage);
                    currentMessageLength = -1;
                    bytesReadInCurrentMessage = 0;

                    System.out.println(readMessageLength());
                }

                log.trace("Received: {} bytes", lastReadSize);
                buffer = bufferSupplier.get();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private byte[] assembleCurrentMessage(int currentMessageLength) {
        byte[] currentMessage;// Get the data of the current message
        int effectiveMessageLength = currentMessageLength;
        currentMessage = new byte[effectiveMessageLength];
        int copied = 0;
        int prologPast = 0;
        boolean clearBuffer = true;
        for (int i = 0; i < messageBuffer.size(); i++) {
            byte[] b = messageBuffer.get(i);
            Integer readBytesLength = messageBufferSizes.get(i);
            int begin = 0;
            if (readBytesLength > INT_SIZE && prologPast < INT_SIZE) {
                begin = INT_SIZE;
                prologPast = INT_SIZE;
            } else if (prologPast < INT_SIZE) {
                prologPast += readBytesLength;
                continue;
            }
            int toCopy = Math.min(effectiveMessageLength - copied, readBytesLength);
            System.arraycopy(b, begin, currentMessage, copied, toCopy);
            copied += toCopy;

            // Handle the case that we have already the beginning of the next message
            if (toCopy + INT_SIZE < readBytesLength) {
                clearBuffer = false;
                int byteLength = readBytesLength - toCopy;
                byte[] nextMessageBegin = new byte[byteLength];
                System.arraycopy(b, toCopy + INT_SIZE, nextMessageBegin, 0, byteLength);
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
        currentMessageLength = (intBytes[0] << 24) | (intBytes[1] << 16) | (intBytes[2] << 8) | intBytes[3];
        log.trace("Current message size is {} bytes", currentMessageLength);
        return currentMessageLength;
    }
}
