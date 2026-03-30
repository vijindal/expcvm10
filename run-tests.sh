#!/bin/bash
# Test runner script for expcvm10

set -e

cd "$(dirname "$0")"

echo "=== Building with javac ==="
mkdir -p build/classes
javac -cp "lib/*" -d build/classes -sourcepath src $(find src -name "*.java" -type f) 2>&1 | grep -E "error:" || echo "Build successful"

echo ""
echo "=== Running GridMinimizerTest (synthetic phase models) ==="
java -cp "build/classes;lib/*" test.GridMinimizerTest

echo ""
echo "=== Running GridMinimizerCost507Test (Nb-Ti system, 300K-6000K) ==="
java -cp "build/classes;lib/*" test.GridMinimizerCost507Test

echo ""
echo "=== All tests completed ==="
