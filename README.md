# Java STDIO Inter Process Communication

__Requirements__: Java >= 11, for tests: Python >= 3.6

## Overview
This library organizes the exchange of messages between a Java program (the user of this library)
and an external program via standard input and output. It thus uses a pipe for communication.

For information on how to use the library and what settings exist, please refer to the JavaDoc given at
the objects and methods. 

For some simple examples, consult the tests. They use some Python scripts to communicate with.

## Workflow

The central class of the Library is `de.julielab.ipc.javabridge.StdioBridge`. This is the class that sends messages
to the pipe connecting it with the external program and receives messages from the external program to use within
the Java code. The exact behaviour of the bridge is defined by `de.julielab.ipc.javabridge.Options`. There, the
executable of the external program is defined, how to distinguish result lines from normal external program
output, the termination signal and more. Most importantly, the class of the received (not sent) messages is configured there.

When a bridge is created, it is started via the `start()` method. This will start the external program.

The external program has to be delivered from the user of the library. It will typically read from STDIN in a loop,
process the message, produce some output and send it back via STDOUT. This can be done as a normal string or in
binary mode (Python, for example, offers `sys.stdout.buffer.write` to write bytes directly into the output buffer).

Messages sent from Java to the external program are always strings. Received messages may be in string or binary form.
In the latter case, the user of the library needs to interpret the received messages.

At the end of processing, the `stop()` method of the bridge should be called. This closes the pipe and sends the
termination signal (configured in the `Options` object) to the external program. The external program should react
to this special message - that the user has to define and can be something trivial like `quit` - by shutting itself down.