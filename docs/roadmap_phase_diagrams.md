```
// ============================================================
// Data structures
// ============================================================
// Paper-mandated fields (§2.3, exit-record requirements) vs. this
// implementation's own organization are distinguished per struct below.

// Complete output of Algorithm A for one point. §3.1's literal list of
// what an exit's equilibrium description must retain is exactly: "T, P,
// the amount and constitution of all phases and the chemical
// potentials" -- so T, P, phaseAmounts, phaseConstitutions and
// chemicalPotentials are paper-mandated. conditions/stablePhases/
// converged/globallyStable are this implementation's own additions:
// the paper describes convergence (Fig. 1) and global-stability
// checking (§2.3.3) as algorithm BEHAVIORS, not as stored fields of
// the equilibrium record itself, but C1/C2's pseudocode needs them on
// this object to call converged(result)/globallyStable(result)/
// stablePhaseSet(result).
EquilibriumResult:
    T, P                       // paper-mandated
    phaseAmounts                // paper-mandated
    phaseConstitutions           // paper-mandated
    chemicalPotentials           // paper-mandated
    conditions                 // this implementation's own addition: T, P, compositions, potentials, etc.
    stablePhases                // this implementation's own addition
    converged                   // this implementation's own addition
    globallyStable               // this implementation's own addition

// A node is the point where a line starts or ends (paper-mandated concept).
Node:
    equilibrium : EquilibriumResult
    exits       : list<Exit>

// One pending or resolved direction to walk away from a node.
// node/equilibrium/done plus the four mapping fields (fixedPhase,
// initialAxis, direction, forbiddenPhase) are paper-mandated -- §3.3
// specifies exactly these for multi-axis exits. For a one-axis STEP
// calculation, fixedPhase and forbiddenPhase are simply none.
Exit:
    node
    equilibrium              // complete equilibrium at the node
    done
    fixedPhase                // phase held fixed with zero amount
    initialAxis
    direction
    forbiddenPhase

// The paper requires only a shared buffer for sequential storage of
// all calculated equilibria along the lines ("the lines share a
// buffer for sequential storage of all calculated equilibria along
// the lines"). LineResult itself -- and the startNode/endNode/
// terminatedReason breakdown -- is this implementation's own
// organization of that requirement, not something Fig. 4/5 spells out.
LineResult:
    startNode
    endNode
    equilibria[]              // sequential EquilibriumResult entries, in walk order
    terminatedReason           // axis limit / phase change / convergence failure / excluded / etc.

// Top-level result of Algorithm B: the complete diagram.
// nodes/lines are this implementation's own bookkeeping over the
// paper-mandated node/exit graph and shared equilibrium buffer.
DiagramResult:
    nodes[]
    lines[]
    equilibriaBuffer[]


// ============================================================
// Algorithm B (Fig. 4): make a diagram
// ============================================================
procedure B(conditions) -> DiagramResult:
    set conditions
    diagram = new DiagramResult()
    equilibrium0 = A(conditions)                      // single equilibrium calculation

    if numAxes(conditions) == 1:
        axis = the one axis condition
        node0 = new Node(equilibrium0)
        node0.exits = [ Exit(equilibrium=equilibrium0, initialAxis=axis, direction=+1),
                        Exit(equilibrium=equilibrium0, initialAxis=axis, direction=-1) ]
                                                          // "+node, 2 exits"
        diagram.nodes += [ node0 ]
        C1(node0.exits, diagram)

    else if numAxes(conditions) == 2:
        axis = one of the two axes                    // normally a potential
        otherAxis = the other axis

        currentConditions = copy(conditions)          // A takes the FULL condition set, not a bare
        current = equilibrium0                        // scalar -- start from the shared initial equilibrium
        loop:
            currentConditions[axis] += increment(axis)
            current = A(currentConditions)
            if stablePhaseSet(current) == stablePhaseSet(equilibrium0):
                continue                                // "no phase change"
            else:
                break                                    // "phases changed"

        alpha = the phase that appeared/disappeared
        node0 = new Node(current)                        // fix belongs to the EXITS, not the node --
                                                          // §3.1: "each exit created in a node will
                                                          // have an indication which phase should be
                                                          // held fix with zero amount along the line"
        node0.exits = [ Exit(equilibrium=current, initialAxis=otherAxis, direction=+1, fixedPhase=alpha),
                        Exit(equilibrium=current, initialAxis=otherAxis, direction=-1, fixedPhase=alpha) ]
                                                          // "+node, 2 exits" -- §3.3: fixed
                                                          // ALONG THE LINE, not just at the node
        diagram.nodes += [ node0 ]
        C1(node0.exits, diagram)

    set plot axes and plot
    return diagram


// ============================================================
// Algorithm C1 (Fig. 5): follow equilibria along lines using A;
// call C2 when A finds a change of the set of stable phases
// ============================================================
procedure C1(pendingExits, diagram):
    loop:
        exit = search(pendingExits)                    // scan node list for a pending exit
        if exit == none:
            return                                      // Finished

        runningStableSet = exit.startNode.stablePhaseSet    // snapshotted once, fixed for this line

        line = new LineResult(startNode = exit.startNode)   // this implementation's own bookkeeping;
        diagram.lines += [ line ]                            // paper only mandates the shared buffer

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
                    lineTerminated(line, reason="convergence failure")   // ">3 times"
                    break out to search()
                currentConditions = smallerIncrement(currentConditions, axis)  // "[condition] increment"
                result = A(currentConditions, exit.fixedPhase)
                attempts += 1
                continue

            if outOfBounds(currentConditions, axis):
                lineTerminated(line, reason="axis limit")   // "axis limit? yes"
                break out to search()

            if stablePhaseSet(result) != runningStableSet:   // vs. the line's OWN starting set
                C2(exit, result, axis, currentConditions, diagram, line)  // `axis` = THIS iteration's
                break out to search()                        // axis, not exit.axis (may have changed
                                                               // via selectAxisWithLargestVariation)
                                                               // "phase change? yes"

            saveResult(result, currentConditions)           // "save results" -- paper-mandated buffer
            line.equilibria += [ result ]                    // this implementation's own per-line index

            // §2.3.3: global check at regular intervals along the line
            if atCheckInterval() and not globallyStable(result):
                lineTerminated(line, reason="excluded")
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
procedure C2(exit, crossingResult, axis, currentConditions, diagram, line):
    // diagram/line: this implementation's bookkeeping, not paper concepts.
    alpha = phase that appeared/disappeared
             (symmetric difference of stable sets before/after)

    // §3.2: fix alpha at zero amount, release the walked axis.
    // exit.fixedPhase (multi-axis ZPF line) stays fixed.
    boundaryConditions = copy(currentConditions)
    boundaryConditions = fix(boundaryConditions, alpha, amount=0)
    boundaryConditions = release(boundaryConditions, axis)

    boundary = A(boundaryConditions, exit.fixedPhase)

    if not converged(boundary):
        line.terminatedReason = "boundary solve failed"
        return

    lineTerminated(boundary)
    line.terminatedReason = "phase change"

    if not globallyStable(boundary):                        // §2.3.3
        markExcluded()
        line.terminatedReason = "excluded"
        return

    node = search(boundary)                                 // match by T,P,stable set,mu
    if node != none:
        exitMarkedDone(exit, node)
        line.endNode = node
        return

    node = new Node(boundary)
    diagram.nodes += [ node ]
    line.endNode = node

    if exit.isStepLine:                                     // §3.2: one exit, same direction
        node.exits += [ Exit(axis, exit.direction) ]
        return

    if tieLinesInCalculatedPlane(node):
        node.exits += createTieLineExits(
            node, axis, exit.direction, alpha, exit.arrivingFixedPhase)
        return

    f = numComponents + 2 - numStablePhases(node) - numFixedPotentialConditions
                                                             // Eq. 8

    if f == 0:
        arrivalPair = exit.arrivingFixedPhase == none ? none : (alpha, exit.arrivingFixedPhase)
        D(node, arrivalPair)                                  // invariant -> D owns the whole node
        return

    // f > 0, no tie-lines: isopleth crossing
    if exit.arrivingFixedPhase == none:
        node.exits += [ Exit(axis, +1, fix=alpha), Exit(axis, -1, fix=alpha) ]
        return

    node.exits += [ Exit(axis, exit.direction, fix=exit.arrivingFixedPhase, forbidden=alpha),
                     Exit(axis, +1, fix=alpha, forbidden=exit.arrivingFixedPhase),
                     Exit(axis, -1, fix=alpha, forbidden=exit.arrivingFixedPhase) ]
    // new exits re-enter pendingExits via C1's own search() over diagram.nodes


// Boolean test not specified explicitly in Sundman et al.; §2.4's
// Fig. 3(a) vs. 3(c) contrast.
function tieLinesInCalculatedPlane(node):
    return no condition outside the diagram's own axes is COMPOSITION or
           an extensive/normalized (linear-composition) condition


// §3.3 specifies only the exit count at a tie-line node; fix/direction
// assignment is this implementation's own choice.
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
    stablePhases = node.stablePhases                        // p stable phases at the invariant (Fig. 7)

    for each pair (phi1, phi2) in combinations(stablePhases, 2):
        if arrivalPair != none and { phi1, phi2 } == { arrivalPair }:
            continue                                          // exclude the pair already on the arriving line

        // Eq. 9: solve the remaining (p-1) phase amounts + (c-1) conditions,
        // excluding phi1, phi2:
        //   sum_{phi!=phi1,phi2} A_C^phi*N^phi = A_C - sum_{phi=phi1,phi2} A_C^phi*N^phi
        amounts = solveLinear(node, exclude={phi1, phi2})

        if any amount in amounts <= 0:                         // all remaining phase amounts must be > 0
            continue                                          // "not in the calculated section"

        // "the compositions of the stable phases are the same but the
        // amounts N^phi will be different and must be stored together
        // with the phases phi1 and phi2" -- one equilibrium per exit.
        exitEquilibrium = copy(node.equilibrium)
        exitEquilibrium.phaseAmounts = amounts
        exitEquilibrium.phaseAmounts[phi1] = 0
        exitEquilibrium.phaseAmounts[phi2] = 0

        node.exits += [ Exit(equilibrium=exitEquilibrium, fix=phi2, forbidden=phi1),
                         Exit(equilibrium=exitEquilibrium, fix=phi1, forbidden=phi2) ]
                                                                 // "+2 exits" per valid pair
```
