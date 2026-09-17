```
// ============================================================
// Algorithm B (Fig. 4): make a diagram
// ============================================================
procedure B(conditions):
    set conditions
    equilibrium0 = A(conditions)                      // single equilibrium calculation

    if numAxes(conditions) == 1:
        axis = the one axis condition
        node0 = new Node(equilibrium0)
        node0.exits = [ Exit(axis, direction=+1),
                        Exit(axis, direction=-1) ]     // "+node, 2 exits"
        C1(node0.exits)

    else if numAxes(conditions) == 2:
        axis = one of the two axes                    // normally a potential
        otherAxis = the other axis

        axisValue = equilibrium0.valueOf(axis)        // start from the shared initial equilibrium
        current = equilibrium0
        loop:
            axisValue = axisValue + increment(axis)
            current = A(axisValue)
            if stablePhaseSet(current) == stablePhaseSet(equilibrium0):
                continue                                // "no phase change"
            else:
                break                                    // "phases changed"

        alpha = the phase that appeared/disappeared
        node0 = new Node(current, fix(alpha, amount=0))
        node0.exits = [ Exit(otherAxis, direction=+1, fix=alpha),
                        Exit(otherAxis, direction=-1, fix=alpha) ]
                                                          // "+node, 2 exits" -- §3.3: fixed
                                                          // ALONG THE LINE, not just at the node
        C1(node0.exits)

    set plot axes and plot
    end


// ============================================================
// Algorithm C1 (Fig. 5): follow equilibria along lines using A;
// call C2 when A finds a change of the set of stable phases
// ============================================================
procedure C1(pendingExits):
    loop:
        exit = search(pendingExits)                    // scan node list for a pending exit
        if exit == none:
            return                                      // Finished

        runningStableSet = exit.startNode.stablePhaseSet    // snapshotted once, fixed for this line

        axis = exit.initialAxis
        currentConditions = copy(exit.startNode.conditions) // full condition state: both axes, T, P
        currentConditions = increment(currentConditions, axis, smallIncrement(exit.direction))
        result = A(currentConditions, exit.fixedPhase)

        if exit.forbiddenPhase in stablePhaseSet(result):
            exit.direction = -exit.direction             // "sign of increment changed"
            currentConditions = copy(exit.startNode.conditions)
            currentConditions = increment(currentConditions, axis, smallIncrement(exit.direction))
            result = A(currentConditions, exit.fixedPhase)

        attempts = 0
        loop:                                            // main line-walking loop
            if not converged(result):
                if attempts >= 3:
                    lineTerminated()                      // ">3 times"
                    break out to search()
                currentConditions = smallerIncrement(currentConditions, axis)  // "[condition] increment"
                result = A(currentConditions, exit.fixedPhase)
                attempts += 1
                continue

            if outOfBounds(currentConditions, axis):
                lineTerminated()                           // "axis limit? yes"
                break out to search()

            if stablePhaseSet(result) != runningStableSet:   // vs. the line's OWN starting set
                C2(exit, result, axis, currentConditions)    // `axis` = THIS iteration's axis, not
                break out to search()                        // exit.axis (may have changed via
                                                               // selectAxisWithLargestVariation)
                                                               // "phase change? yes"

            saveResult(result, currentConditions)           // "save results"

            // §2.3.3: global check at regular intervals along the line
            if atCheckInterval() and not globallyStable(result):
                lineTerminated()
                markExcluded()
                break out to search()

            axis = selectAxisWithLargestVariation()          // MAP only; §3.3
            attempts = 0
            currentConditions = increment(currentConditions, axis, increment(axis, exit.direction))
                                                             // "increment axis"
            result = A(currentConditions, exit.fixedPhase)   // loop back to "A"


// ============================================================
// Algorithm C2 (Fig. 6): handle a change of the set of stable
// phases at the end of a line and generate a node with exits
// ============================================================
procedure C2(exit, crossingResult, axis, currentConditions):
    // `axis`/`currentConditions` are C1's CURRENT state at the crossing,
    // not exit's own starting ones -- §3.3's axis-switching can change
    // which axis is walked mid-line, so release is a per-crossing fact.
    alpha = phase that appeared/disappeared
             (symmetric difference of stable sets before/after)

    fix(alpha, amount=0)
    releasedConditions = release(currentConditions, axis)  // drop the CURRENTLY WALKED axis

    boundary = A(fixed=alpha@0, conditions=releasedConditions)  // exact zero-amount solve

    if not converged(boundary):
        return                                              // "error? yes" -- line never reached its endpoint

    // paper: "the current line is terminated AT THIS POINT" -- the
    // boundary solve itself is the endpoint, before node search runs.
    lineTerminated(boundary)

    if not globallyStable(boundary):                        // §2.3.3: abandon and suppress the line
        markExcluded()
        return                                               // "global? no -> delete line"

    node = search(boundary)                                 // match by T,P,stable set,mu; none if not found
    if node != none:                                        // "the node has already been found"
        exitMarkedDone(exit, node)
        return                                               // "already calculated"

    node = new Node(boundary)                               // "+node"

    if exit.isStepLine:                                     // §3.2: "one exit to continue... in
        node.exits += [ Exit(axis, exit.direction) ]        // the SAME direction" -- no forbidden
        return                                               // phase (that's a ZPF/multi-exit concept)

    if tieLinesInCalculatedPlane(node):                      // "tie-lines?" -- §3.3's own framing
        node.exits += createTieLineExits(
            node, axis, exit.direction, alpha, exit.arrivingFixedPhase)   // "+2 exits"
        return

    f = numComponents + 2 - numStablePhases(node) - numFixedPotentialConditions
                                                             // Eq. 8, Gibbs phase rule

    if f == 0:
        // D owns exit generation for the WHOLE node; only needs the node
        // plus which pair to skip (alpha + whatever was already fixed on
        // the arriving line, if any).
        arrivalPair = exit.arrivingFixedPhase == none ? none : (alpha, exit.arrivingFixedPhase)
        D(node, arrivalPair)                                  // "invariant? yes -> D"
        return

    // f > 0, no tie-lines in the calculated plane: isopleth crossing
    if exit.arrivingFixedPhase == none:                     // degrades to "+2 exits"
        node.exits += [ Exit(axis, +1, fix=alpha), Exit(axis, -1, fix=alpha) ]
        return

    node.exits += [ Exit(axis, exit.direction, fix=exit.arrivingFixedPhase, forbidden=alpha),
                                                             // LFIX continues, SAME direction the
                                                             // arriving line was already walking
                     Exit(axis, +1, fix=alpha, forbidden=exit.arrivingFixedPhase),  // PHFIX, both dirs
                     Exit(axis, -1, fix=alpha, forbidden=exit.arrivingFixedPhase) ]
                                                             // "+3 exits"


// Implementation-level test for the Fig. 6 "tie-lines?" decision.
// Exact Boolean definition is not specified explicitly in Sundman et al.
// This one: every condition outside the diagram's two axes is a
// potential (T, P, mu), never composition or a linear-composition
// (iso-pleth) constraint -- consistent with §2.4's Fig. 3(a) vs. 3(c)
// contrast (ordinary binary/ternary tie-lines vs. iso-pleth sections).
function tieLinesInCalculatedPlane(node):
    return no condition outside the diagram's own axes is COMPOSITION or
           an extensive/normalized (linear-composition) condition


// §3.3 only specifies the exit COUNT at a tie-line node ("two exits
// will be created") -- exact fix/direction assignment is this
// implementation's own choice, not something Fig. 6 itself spells out.
function createTieLineExits(node, axis, incomingDirection, alpha, arrivingFixedPhase):
    return [ Exit(axis, +1, fix=alpha, forbidden=arrivingFixedPhase),
             Exit(axis, -1, fix=alpha, forbidden=arrivingFixedPhase) ]


// ============================================================
// Algorithm D (Fig. 7): find all exits in the plane of
// calculation from a multi-component invariant equilibrium --
// all pairs (phi_i, phi_j) with zero amount, all other stable
// phases with positive amount (Eq. 9). Owns exit generation for
// the WHOLE node (§3.3: loop over every pair of phases stable at
// the invariant), not just one exit's own arrival context.
// ============================================================
procedure D(node, arrivalPair = none):
    stablePhases = node.stablePhases                        // p+1 phases at the invariant

    for each pair (phi1, phi2) in combinations(stablePhases, 2):
        if arrivalPair != none and { phi1, phi2 } == { arrivalPair }:
            continue                                          // exclude the pair already on the arriving line

        // Eq. 9: solve the remaining (p-1) phase amounts + (c-1) conditions,
        // excluding phi1, phi2:
        //   sum_{phi!=phi1,phi2} A_C^phi*N^phi = A_C - sum_{phi=phi1,phi2} A_C^phi*N^phi
        amounts = solveLinear(node, exclude={phi1, phi2})

        if any amount in amounts <= 0:                         // Eq. 9's own literal test ("negative");
            continue                                          // exactly-zero is a degenerate/coincident
                                                                // boundary the paper doesn't resolve
                                                                // "not in the calculated section"

        // both phi1 and phi2 have zero amount at this exit; valid pair.
        // §3.3: "along one line phi1 will be stable with zero amount and
        // phi2 forbidden... along the other line phi2 will be stable
        // with zero amount and phi1 forbidden" -- one exit per role.
        node.exits += [ Exit(fix=phi2, forbidden=phi1),
                         Exit(fix=phi1, forbidden=phi2) ]
                                                                 // "+2 exits" per valid pair
```
