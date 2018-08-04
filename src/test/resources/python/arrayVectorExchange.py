'''
A program that returns the single double array vector on each request
'''
import sys
from struct import *
import base64

vector = [0.1]
for line in sys.stdin:
    if line.strip() == "exit":
        sys.exit(0)

    bytes = pack('>d', *vector)
    #b64 = base64.b64encode(bytes)
    #print("Vector bytes: " + str(b64))
    sys.stdout.buffer.write(pack('>i', len(bytes)))
    sys.stdout.buffer.write(bytes)
    print()
