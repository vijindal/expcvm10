#!/bin/bash

# Count test methods in default suite (no excluded tags)
echo "=== DEFAULT SUITE (no @Tag or excluded tags) ==="
files_default=$(find src-test -name "*.java" -type f | while read f; do
  if ! grep -q "@Tag(\"exploratory\"\|@Tag(\"slow\"\|@Tag(\"integration-heavy\"\|@Tag(\"external\"" "$f"; then
    if grep -q "@Test" "$f"; then
      echo "$f"
    fi
  fi
done)
count_default=$(echo "$files_default" | while read f; do grep -c "^\s*@Test$" "$f"; done | paste -sd+ | bc)
echo "Files: $(echo "$files_default" | wc -l)"
echo "Test methods: $count_default"
echo "Files: $(echo "$files_default" | tr '\n' ' ')"
echo

echo "=== EXPLORATORY (@Tag(\"exploratory\")) ==="
files_explore=$(find src-test -name "*.java" -exec grep -l "@Tag(\"exploratory\"" {} \;)
count_explore=$(echo "$files_explore" | while read f; do grep -c "^\s*@Test$" "$f"; done | paste -sd+ | bc)
echo "Files: $(echo "$files_explore" | wc -l)"
echo "Test methods: $count_explore"
echo "Files: $files_explore"
echo

echo "=== SLOW (@Tag(\"slow\")) ==="
files_slow=$(find src-test -name "*.java" -exec grep -l "@Tag(\"slow\"" {} \;)
count_slow=$(echo "$files_slow" | while read f; do grep -c "^\s*@Test$" "$f"; done | paste -sd+ | bc)
echo "Files: $(echo "$files_slow" | wc -l)"
echo "Test methods: $count_slow"
echo "Files: $files_slow"
echo

echo "=== INTEGRATION-HEAVY (@Tag(\"integration-heavy\")) ==="
files_heavy=$(find src-test -name "*.java" -exec grep -l "@Tag(\"integration-heavy\"" {} \;)
count_heavy=$(echo "$files_heavy" | while read f; do grep -c "^\s*@Test$" "$f"; done | paste -sd+ | bc)
echo "Files: $(echo "$files_heavy" | wc -l)"
echo "Test methods: $count_heavy"
echo "Files: $files_heavy"
echo

echo "=== EXTERNAL (@Tag(\"external\")) ==="
files_ext=$(find src-test -name "*.java" -exec grep -l "@Tag(\"external\"" {} \;)
count_ext=$(echo "$files_ext" | while read f; do grep -c "^\s*@Test$" "$f"; done | paste -sd+ | bc)
echo "Files: $(echo "$files_ext" | wc -l)"
echo "Test methods: $count_ext"
echo "Files: $files_ext"
echo

total=$(($count_default + $count_explore + $count_slow + $count_heavy + $count_ext))
echo "=== TOTAL ==="
echo "Default: $count_default"
echo "Exploratory: $count_explore"
echo "Slow: $count_slow"
echo "Integration-heavy: $count_heavy"
echo "External: $count_ext"
echo "TOTAL: $total"
