#!/bin/bash

echo "=== RUNNING ALL TEST SUITES ==="
echo

echo "1. DEFAULT (./gradlew test)"
echo "=========================================="
time ./gradlew test --no-build-cache > /tmp/test_default.log 2>&1
echo "Exit code: $?"
echo

echo "2. INTEGRATION-HEAVY (./gradlew testIntegrationHeavy)"
echo "=========================================="
time ./gradlew testIntegrationHeavy --no-build-cache > /tmp/test_integration.log 2>&1
echo "Exit code: $?"
echo

echo "3. SLOW (./gradlew testSlow)"
echo "=========================================="
time ./gradlew testSlow --no-build-cache > /tmp/test_slow.log 2>&1
echo "Exit code: $?"
echo

echo "4. EXPLORATORY (./gradlew testExplore)"
echo "=========================================="
time ./gradlew testExplore --no-build-cache > /tmp/test_explore.log 2>&1
echo "Exit code: $?"
echo

echo "5. EXTERNAL (./gradlew testExternal)"
echo "=========================================="
time ./gradlew testExternal --no-build-cache > /tmp/test_external.log 2>&1
echo "Exit code: $?"
echo

echo "6. ALL (./gradlew testAll)"
echo "=========================================="
time ./gradlew testAll --no-build-cache > /tmp/test_all.log 2>&1
echo "Exit code: $?"
echo

echo "=== DONE ==="
