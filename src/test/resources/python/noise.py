'''
This program does not only repeat the input lines but also outputs other stuff.
"Interesting" output lines are always prefixed with "Output:"
'''
import sys
for line in sys.stdin:
    if line.strip() == "exit":
        sys.exit(0)
    print("Output: " + line.strip())
    print("Cows are great!")
    print("And cats are fluffy.")
