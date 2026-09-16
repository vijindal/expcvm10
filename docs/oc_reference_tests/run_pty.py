#!/usr/bin/env python3
"""Run a command under a real pseudo-terminal, feeding it a scripted list of
lines with small delays, to work around programs (like OpenCalphad's tqex1)
that use raw/character-mode terminal I/O and don't behave correctly when fed
plain piped (non-TTY) stdin."""
import os
import pty
import sys
import time
import select

def main():
    cmd = sys.argv[1]
    args = sys.argv[2:-1]
    inputs_file = sys.argv[-1]
    with open(inputs_file) as f:
        lines = [l.rstrip("\n") + "\r" for l in f]

    pid, fd = pty.fork()
    if pid == 0:
        os.execvp(cmd, [cmd] + args)
    else:
        output = b""
        line_idx = 0
        last_activity = time.time()
        while True:
            r, _, _ = select.select([fd], [], [], 1.0)
            if fd in r:
                try:
                    chunk = os.read(fd, 4096)
                except OSError:
                    break
                if not chunk:
                    break
                output += chunk
                sys.stdout.buffer.write(chunk)
                sys.stdout.flush()
                last_activity = time.time()
                # crude heuristic: if the output ends with a recognized prompt
                # marker, send the next scripted line
                tail = chunk.decode(errors="replace").rstrip()
                is_prompt = (tail.endswith(":") or tail.endswith("/")
                             or tail.endswith("OC6:") or tail.endswith(">"))
                if line_idx < len(lines) and is_prompt:
                    time.sleep(0.2)
                    os.write(fd, (lines[line_idx] + "\n").encode())
                    line_idx += 1
                    last_activity = time.time()
            else:
                if time.time() - last_activity > 8:
                    break
        try:
            os.kill(pid, 9)
        except ProcessLookupError:
            pass

if __name__ == "__main__":
    main()
