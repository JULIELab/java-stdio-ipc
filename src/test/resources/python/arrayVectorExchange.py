'''
A program that returns the single double array vector on each request
'''
import sys
from struct import *
import base64

vector = [0.1, 0.2, 0.3, -0.4, 0, 42.1337]
for line in sys.stdin:
    if line.strip() == "exit":
        sys.exit(0)
    bytes = pack('>dddddd', *vector)
    sys.stdout.buffer.write(pack('>i', len(bytes)))
    sys.stdout.buffer.write(bytes)
    print(end='')
    vector = [0.7, 0.8, 0.9]
    bytes = pack('>ddd', *vector)
    sys.stdout.buffer.write(pack('>i', len(bytes)))
    sys.stdout.buffer.write(bytes)
    print(end='')
    print("Received " + line, file=sys.stderr)
