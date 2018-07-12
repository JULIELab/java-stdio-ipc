# Java STDIO Inter Process Communication

This library organizes the exchange of string messages between a Java program (the user of this library)
and an external program via standard input and output.

Currently, the communication works with one-line responses. If the external program outputs more than is required
for the Java program, a filter can be delivered that recognizes the desired lines.

For information on how to use the library and what settings exist, please refer to the JavaDoc given at
the objects and methods. 

For some simple examples, consult the tests. They use some Python scripts to communicate with.
