'''
This program does not only repeat the input lines but also outputs other stuff.
"Interesting" output lines are always prefixed with "Output:"
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
    print("Output: " + line.strip())
    print("Cows are great!")
    print("And cats are fluffy.")
