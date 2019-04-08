'''
A very simple program that just repeats its input and ends when
the "exit" line is sent.
'''
import sys
for line in sys.stdin:
    if line.strip() == "exit":
        sys.exit(0)
    print("answer line 1")
    print("answer line 2")
    print("answer line 3")
    print("last line")
