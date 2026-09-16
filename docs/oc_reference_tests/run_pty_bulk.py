#!/usr/bin/env python3
"""Run a command under a real pseudo-terminal, writing an entire input file's
bytes up front (like a terminal paste) rather than waiting for per-prompt
triggers, then draining output until the child exits or goes quiet."""
import os
import pty
import sys
import time
import select

def main():
    cmd = sys.argv[1]
    args = sys.argv[2:-1]
    inputs_file = sys.argv[-1]
    with open(inputs_file, "rb") as f:
        data = f.read()

    pid, fd = pty.fork()
    if pid == 0:
        os.execvp(cmd, [cmd] + args)
    else:
        os.write(fd, data)
        last_activity = time.time()
        quiet_timeout = float(os.environ.get("PTY_QUIET_TIMEOUT", "20"))
        while True:
            r, _, _ = select.select([fd], [], [], 1.0)
            if fd in r:
                try:
                    chunk = os.read(fd, 4096)
                except OSError:
                    break
                if not chunk:
                    break
                sys.stdout.buffer.write(chunk)
                sys.stdout.flush()
                last_activity = time.time()
            else:
                if time.time() - last_activity > quiet_timeout:
                    break
        try:
            os.kill(pid, 9)
        except ProcessLookupError:
            pass

if __name__ == "__main__":
    main()
