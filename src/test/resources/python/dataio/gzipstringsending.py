'''
A program that returns the single double array vector on each request
'''
import sys
import gzip

def decodeString(buffer):
    lengthBuffer = bytearray(4)
    buffer.readinto(lengthBuffer)
    length = int.from_bytes(lengthBuffer, 'big')
    content = bytearray(length)
    buffer.readinto(content)
    return gzip.decompress(content).decode("utf-8")

stdbuffer = sys.stdin.buffer
while True:
    line = decodeString(stdbuffer)
    if line.strip() == "exit":
        sys.exit(0)
    print("response")
