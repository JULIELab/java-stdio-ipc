'''
A very simple program that just repeats its input and ends when
the "exit" line is sent.
'''
import sys


def decodeString(buffer):
    lengthBuffer = bytearray(4)
    buffer.readinto(lengthBuffer)
    length = int.from_bytes(lengthBuffer, 'big')
    content = bytearray(length)
    buffer.readinto(content)
    return content.decode("utf-8")

stdbuffer = sys.stdin.buffer
while True:
    line = decodeString(stdbuffer)
    if line.strip() == "exit":
        sys.exit(0)
    print("answer line 1")
    print("answer line 2")
    print("answer line 3")
    print("last line")
