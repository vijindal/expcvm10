#!/usr/bin/env python3
"""Run a command under a real pseudo-terminal, sending each input line on a
fixed 1.5s interval regardless of any detected prompt.

Why this exists alongside run_pty.py (prompt-detection heuristic) and
run_pty_bulk.py (paste the whole file at once): both were found NOT to work
reliably for a multi-command OC macro (set cond / c e / set ax / step /
l r ... sequences) in this project's own sessions --
  - run_pty.py's tail.endswith(...) prompt heuristic can miss OC's actual
    prompt ("--->OC6: ", with a trailing space) if it doesn't arrive as the
    tail of a single read() chunk, silently stalling after the banner with
    no further input ever sent.
  - run_pty_bulk.py pastes the entire file as one write() immediately after
    fork; OC's raw-mode terminal reader cannot keep up with an instant
    multi-line paste and simply echoes the pasted text back without
    processing any of it as commands (confirmed directly: OC prints its
    banner and nothing else, the whole macro comes back as terminal echo).
This driver sidesteps both failure modes by never trying to detect a
prompt at all -- it just sends the next line every 1.5s, trusting that OC
will have caught up with the previous one by then. Slower than prompt-
matching in principle, but the only one of the three confirmed to
correctly drive an OC `step`/`map` macro (including sub-prompts like
`step`'s own "Step options? /NORMAL/:") end-to-end in this project.

Usage: python3 run_pty_interval.py <path-to-oc-binary> <macro-file>
(no extra args between them -- unlike run_pty.py's optional arg list).
See the top-level "Running OC calculations" section of
docs/roadmap_phase_diagrams.md for the full invocation recipe (WSL route,
working directory, macro-file conventions).
"""
import os, pty, sys, time, select

def main():
    cmd = sys.argv[1]
    inputs_file = sys.argv[-1]
    with open(inputs_file) as f:
        lines = [l.rstrip("\n") for l in f]

    pid, fd = pty.fork()
    if pid == 0:
        os.execvp(cmd, [cmd])
    else:
        line_idx = 0
        last_activity = time.time()
        last_send = time.time()
        while True:
            r, _, _ = select.select([fd], [], [], 0.5)
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
            now = time.time()
            # send next line every 1.5s regardless of prompt detection
            if line_idx < len(lines) and (now - last_send) > 1.5:
                os.write(fd, (lines[line_idx] + "\r\n").encode())
                line_idx += 1
                last_send = now
                last_activity = now
            if now - last_activity > 15 and line_idx >= len(lines):
                break
            if now - last_activity > 30:
                break
        try:
            os.kill(pid, 9)
        except ProcessLookupError:
            pass

if __name__ == "__main__":
    main()
