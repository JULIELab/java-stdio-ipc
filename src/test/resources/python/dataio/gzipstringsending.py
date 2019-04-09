'''
A program that returns the single double array vector on each request
'''
import sys
import gzip

for line in sys.stdin:
    if line.strip() == "exit":
        sys.exit(0)
    b = gzip.decompress(line)
    print("response")
