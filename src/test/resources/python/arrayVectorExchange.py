'''
A program that returns the single double array vector on each request
'''
import sys
from struct import *
import base64

vector = [0.1, 0.2, 0.3, 0.4, 0, 42.1337]
for line in sys.stdin:
    if line.strip() == "exit":
        sys.exit(0)

    bytes = pack('>dddddd', *vector)
    #b64 = base64.b64encode(bytes)
    #print("Vector bytes: " + str(b64))
    sys.stdout.buffer.write(b"some binary data")
    print()
    print("huhu", file=sys.stderr)
