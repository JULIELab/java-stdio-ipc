'''
A program that returns the single double array vector on each request
'''
import sys
from struct import *

def decodeString(buffer):
    lengthBuffer = bytearray(4)
    buffer.readinto(lengthBuffer)
    length = int.from_bytes(lengthBuffer, 'big')
    content = bytearray(length)
    buffer.readinto(content)
    return content.decode("utf-8")

vector = [0.1, 0.2, 0.3, -0.4, 0, 42.1337]
stdbuffer = sys.stdin.buffer
print("Some pre-ready blabla")
print("This is also a very important information")
print("Ready!")
while True:
    line = decodeString(stdbuffer)
    if line.strip() == "exit":
        sys.exit(0)
    bytes = pack('>dddddd', *vector)
    sys.stdout.buffer.write(pack('>i', len(bytes)))
    sys.stdout.buffer.write(bytes)
    print(end='')

    print('interrupting text')

    vector = [0.7, 0.8, 0.9]
    bytes = pack('>ddd', *vector)
    sys.stdout.buffer.write(pack('>i', len(bytes)))
    sys.stdout.buffer.write(bytes)
    print(end='')
    print("Received " + line, file=sys.stderr, end='')
