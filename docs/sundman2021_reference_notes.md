# Sundman 2021 (Calphad 75, 102330) -- extracted reference

Source: `docs/2021-calphad-sundman-Algorithms useful for calculating
multi-component equilibria, phase diagrams and other kinds of diagrams.pdf`.
Text below is extracted directly (`pdftotext -layout`) or transcribed from
rendered figures (PyMuPDF, figures are images not extractable as text).
Organized by the paper's own section numbers. Minimal commentary; this is
a reference copy of the paper's content, not an analysis.

Investigation findings (code cross-checks, gaps found in this codebase)
are kept separate, in the **Appendix** at the end.

## 1. Introduction

"Phase diagrams consist of lines separating regions with different sets
of stable phases at thermodynamic equilibrium."

"An algorithm able to calculate equilibria using as condition that a
phase is stable with zero amount together with a method to change the
condition values along the axes of the diagram is known as mapping."

"Both in OC and TC it is possible to plot the calculated ZPF lines with
other axes than those used for the calculation."

## 2. Computational thermodynamics basics

"To calculate an equilibrium in a system with n components, we must set
n + 2 thermodynamic variables as conditions. Frequently the temperature,
T, the pressure, P, and the number of moles of each component A, N_A,
are used but many others are possible, for example prescribing a stable
phase."

### 2.1. Composition variables

1. "An element is an atom in the periodic chart."
2. "A species is a molecular like aggregate of atoms with fixed
   stoichiometry, for example H2O... The values 2 and 0.5 are called
   stoichiometric factors and denoted a_iA where i is the species
   containing the element A... The vacancy, denoted Va, is a special
   species that does not take part to the mass balance but contributes
   to the configurational entropy."
3. "A constituent is a species in a phase... In a phase phi, the
   fraction of the sublattice s occupied by the constituent i is denoted
   y_is." Sublattice constraint: sum_i y_is = 1 (Eq. 1).
4. "A component, sometimes called system component, is normally an
   element... The number of components must be the same as the number
   of elements."

Eq. (2): `N_A^phi = sum_i a_iA y_is^phi` (moles of component A per
formula unit of phase phi).

Eq. (3): mole fraction `x_A^phi = N_A^phi / sum_B N_B^phi`.

### 2.2. Thermodynamic models

Eq. (4): `G_M^phi(T,P,y) = G^srf - T*S^cfg + G^E + G^phy`, where `G^srf`
is the surface of reference, `S^cfg` the configurational entropy, `G^E`
the excess Gibbs energy, `G^phy` the contribution from special physical
phenomena (e.g. ferromagnetic transitions).

Eq. (5): `G(T,P,N_A) = sum_phi N^phi * G_M^phi(T,P,y)`, where `N^phi >=
0` is the amount of formula units of phi. "At equilibrium the Gibbs
energy is minimum for a system with given values of T, P and N_A."

### 2.3. Single equilibrium calculations

#### 2.3.1. The Lagrange multiplier method

"Most algorithms for equilibrium calculations use the Lagrangian
multiplier method to eliminate constraints and conditions... In OC, it
is implemented following a proposal by Hillert [13] and first used by
Jansson [11] in the POLY module of Thermo-Calc software."

Eq. (6):
```
L = sum_phi N^phi * G_M^phi(T,P,y)
    + sum_A mu_A * ( sum_phi N^phi * M_A^phi - N~_A )
    + sum_phi sum_s eta_s^phi * ( 1 - sum_i y_is^phi )
    + sum_phi gamma^phi * N^phi
```
"where the summation over phi are for all phases and the summation over
phi~ is for all metastable phases. mu_A, eta and gamma are Lagrangian
multipliers. The function L will have the same extremum as G(T,P,N_A)
when all constraints are fulfilled... Eq. (6) can be solved by a linear
iterative method explained by Hillert [13]."

Eq. (7): at the minimum, `0 = dL/dN_A = mu_A + sum_A a_A ...` -- "An
important feature of Eq. (6) is that the Lagrangian multiplier mu_A is
the chemical potential... for each stable phase phi."

"Another interesting feature in Eq. (6) is that the multiplier gamma^phi
is the driving force of phi. At equilibrium, it is negative for a
metastable phase. The last term in Eq. (6) makes it possible to handle
the change of the set of stable phases during the iterations. **A
positive driving force gamma^phi means that the metastable phase phi is
to be added to the set of stable phases. If a stable phase alpha has
negative amount, N^alpha, at an iteration, it is removed from the set of
stable phases.**"

"Fig. 1 presents the flowchart of the algorithm to calculate a single
equilibrium with different sets of conditions, referred in this paper
as algorithm A. It is used by the algorithms presented later to
calculate diagrams with varying conditions. It is a linear algorithm
because it uses second derivatives of the Gibbs energy to convert the
conditions on extensive or normalized variables to be functions of the
chemical potentials and the phase amounts."

**Fig. 1 -- Algorithm A** (p.3, transcribed from the rendered figure):

```
A: single equilibrium calculation
optional initial estimate
-> [N^alpha, y_is^alpha, mu_A, gamma^phi]  <-------------------+
      -> solve eq.(6)                                          |
      -> gamma^phi>0 or N^alpha<0 ?                             |
            yes -> step or map?                                 |
                     yes -> C1                                   |
                     no  -> change set of stable phases ---------+
            no -> delta(mu_A)<eps, delta(y_is^alpha)<eps ?
                     yes -> converged
                     no  -> iter>max ?
                              yes -> failed
                              no  -> +iter -> (loop to top box)
```

#### 2.3.2. Conditions for an equilibrium calculation

"The Gibbs phase rule requires that n + 2 conditions are given to
calculate an equilibrium, in a system with n components. In most cases,
T, P and the overall amount or fraction of the components [are used].
Using such a set of conditions, which represents a closed system, the
equilibrium will be found by a minimum in the Gibbs energy. But a system
can be defined in many other ways by exchanging one or more of the n + 2
conditions... Using other conditions than T, P and N_A is taken care of
by Lagrange constraints in the minimizer. We can use:

- Conditions on the chemical potential or activity of a component. With
  such a condition the system is "open" as the amount of that component
  is not specified. It is also possible to set conditions on both
  amount and chemical potential of a component if, for example, some
  other component has neither amount nor potential set.
- Conditions on entropy, S, or enthalpy, H, for heat balance
  calculations.
- Conditions on the volume, V, provided that the database has volume
  description for the phases.
- Specifying that a phase must be stable with a given amount, for
  example zero.
- Conditions on the mole fraction of a component in a phase, x_A^phi,
  to find a solubility limit.
- Conditions on constituent fractions, y_is, to calculate a second
  order transition.
- Conditions that are an expression of state variables, for example
  T_Cr-bcc - T_Cr-fcc = 0 to calculate the congruent transformation of
  fcc to bcc.

Each condition is set separately and most of these conditions can also
be used as an axis variable for calculating equilibria along a line."

#### 2.3.3. Global equilibrium calculation

"In Fig. 1, algorithm A starts by an initial estimate of the stable
phases and their constitution. This estimate can be the results of a
previous calculation, provided by the user or by an automated
procedure. In OC, a grid minimizer is used to find this automated set
[14]. The grid minimizer approximates the Gibbs energy surface of each
phase with a set of gridpoints and selects the set that gives the
lowest Gibbs energy for the current conditions. The phases and
constitutions that these selected grid points represent are used as
starting values by algorithm A to solve Eq. (6). **The grid minimizer
can find the global equilibrium and detect phases with miscibility
gaps. A phase with a miscibility gap has a single Gibbs energy function
but can appear as two (or more) separate phases with different
compositions in the calculations and diagrams. In OC and TC they are
identified with a suffix #<digit>. The fact that the phases are modeled
with a single Gibbs energy function does not change Gibbs phase rule.**
The grid minimizer will also set the constitution of the metastable
phases using a gridpoint where its Gibbs energy is closest to the Gibbs
energy tangent plane of the selected gridpoints."

"If the set of conditions does not allow the use of the grid minimizer,
for example if T is not a condition, an initial set will be guessed and
after the equilibrium calculation, when T and the overall composition
has been calculated, the grid minimizer can be used as test and the
equilibrium is recalculated automatically if there are any gridpoints
below the calculated equilibrium."

"**During the line calculations, explained in Section 3, global checks
are made at node points and at regular intervals along a line. If such
a check finds that there is another set of phases which represent a
more stable equilibrium, the automatic procedure is to abandon this
line and suppress it in a subsequent plot. This can typically happen if
a stable phase would like to separate into a miscibility gap when
calculating along a line.**"

### 2.4. Calculated phase diagrams

"Most phase diagrams in materials science are at constant pressure.
Binary isobar T-x diagrams can be plotted as shown in Fig. 2. In such
diagrams, a line is either a solubility line or part of an invariant
equilibrium with fixed values of the potentials but with varying amount
of the phases. As the tie-lines are in the plane of the presentation,
the composition of the phases and their amounts can easily be read from
the diagram. Not only stable phase diagrams, as in Fig. 2.a and b, but
also metastable ones, as in Fig. 2.c, can be calculated."

"Ternary diagrams require that some additional system variable is
fixed, as for example the temperature for an iso-thermal diagram shown
in Fig. 3(a). In the case of iso-thermal sections of ternary systems
also, tie-lines are in the plane of representation... The green
triangles define 3-phase regions; their corners indicate the
compositions of the three phases in equilibrium. From such a diagram,
the composition and the amount of each phase can be read. In Fig. 3(b),
the results from the same iso-thermal calculation as in (a) is plotted
using the chemical potential of the components as axes. The two-phase
regions become lines and the three-phase regions points because the
elements in the stable phases have the same chemical potentials... The
phi phase area seems like a balloon on the line separating fcc and bcc."

"If one or more compositions are kept constant, an iso-pleth section is
obtained as shown in Fig. 3(c) for the Al-Mg-Zn system at Zn = 0.05. An
iso-pleth section can actually also be defined by a constant ratio
between two elements or more generally by a linear equation between
several compositions. In Fig. 3(c), the lines separate regions with
different sets of stable phases. The horizontal lines are invariant
equilibria with 4 stable phases. In such a section, there are no
tie-lines. The phase amounts and compositions are not shown and thus
cannot be read from such diagram except in the areas where a single
phase is stable."

## 3. Calculating lines with varying conditions

"A software which can calculate equilibria can be extended to calculate
equilibria along a line by varying a single condition between some
limits to find how the properties of the system changes with this
condition, this is a **step** calculation. The procedure to perform
such calculations follows the left branch in the algorithm B in Fig. 4
and will be discussed in more details in Section 3.2."

"Using more axes, it is possible to calculate a phase diagram. These
latter calculations will be made following Zero Phase Fraction (ZPF)
lines, a concept discussed in [12,18]. **ZPF lines separate regions in
a phase diagram where a phase is present from regions where it is not
present. A phase diagram with at least one composition variable
consists entirely of ZPF lines. The mapping procedure will replace one
axis condition by a condition that a phase is stable (fix) with zero
amount.** It will be discussed in more detail in Section 3.3."

"Both stepping and mapping will thus consist in calculating lines. The
basic operations to perform these two kinds of diagram calculations in
the OC software are illustrated in the general algorithm B in Fig. 4
where both branches of this general algorithm end in calling the
algorithm C1 in Fig. 5 whose task is to calculate a line."

"As shown in algorithm B, to calculate diagrams, the simplest way to
start is to set the appropriate conditions for a single equilibrium
calculation and then select one or more conditions as axis variable
with a minimum and maximum value and a maximum increment between each
calculation."

"In some cases, a single start point may not find all the lines in the
phase diagram. The Fe-Mo phase diagram shown in Fig. 2(b) is one of
these. The gamma loop close to pure Fe is not connected to the other
lines of the diagram. Depending on the starting conditions, either the
loop or the rest of the diagram will not be found by the basic
algorithms presented hereunder. More than one starting point has then
to be input. This can simply be done by the user or it is possible to
automatically predefine many starting points, in particular for binary
or ternary diagrams. **Such issues will not be considered in the
algorithms presented here, where instead the focus is on the way to
process connected lines.**"

**Fig. 4 -- Algorithm B** (p.5, transcribed from the rendered figure):

```
Make a diagram
      |
  set conditions
      |
      A   (single equilibrium calculation)
      |
      +---------------------+----------------------+
   set 1 axis                                  set 2 axes
      |                                              |
  +node, 2 exits                              increment axis
      |                                              |
      |                                              A  <---+
      |                                              |      |
      |                                        no phase change
      |                                              |
      |                                        phases changed
      |                                              |
      |                                        +node, 2 exits
      |                                              |
      +----------------------> C1 <-----------------+
                                |
                        set plot axes and plot
                                |
                               end
```

### 3.1. Data structures

"The design of the data structure to solve a computational problem is
of great importance for software development. With a poor data
structure the coding becomes more complex and difficult to maintain."

"The calculations are organized by following lines of single equilibria
with the same set of stable phases by incrementing one of the axis
conditions in algorithm C1 in Fig. 5."

"A **node** point is where a line starts or ends. They are created from
the start equilibria provided by the user or by algorithm C2 when the
set of stable phases changes along a line and the line is terminated.
All nodes contain **exits** which will be used as a start of a line to
be calculated. An exit will be marked as done if it is identical to the
end of a calculated line terminating at a node which already exists.
Each exit has a complete description of the equilibrium at the node,
i.e. T, P, the amount and constitution of all phases and the chemical
potentials."

"If there are more than one axis, each exit created in a node will have
an indication which phase should be held fix with zero amount along the
line, which axis to use initially to vary the conditions and in which
direction and a phase which is forbidden to become stable at the first
axis increments. The last information is used to determine the sign of
the first increment of the axis as shown in algorithm C1. There are
loops in a phase diagram and one may eventually find the same node
following a line, but not at the first increments."

"All node points are stored in a list searched by algorithms C1 and C2.
The lines share a buffer for sequential storage of all calculated
equilibria along the lines."

### 3.2. Calculate diagrams with a single axis variable

"Diagrams with equilibria calculated varying a single condition along
an axis are useful to plot how various properties in the system varies
with this condition. Such a step calculation can be used to represent
many different types of property diagrams. The way to do such
calculations is presented on the left branch of algorithm B in Fig. 4."

"The user selects one of the conditions used for the initial equilibrium
calculation as axis variable with a minimal and a maximal value as well
as a maximum increment. Then a step command generates a node at the
initial equilibrium with two exits, one in each direction of the axis.
**Algorithm C1 described in Fig. 5 will then calculate equilibria at
each increment along the axis condition until it reaches the axis
limits or the set of stable phases changes.**"

"In the latter case algorithm C2 in Fig. 6 will handle the end of the
current line and generate a node. **In order to end the current line,
the phase which appears or disappears is set fix with zero amount, the
axis condition released and the equilibrium is calculated. Once this
equilibrium has been calculated and checked, the line will be
terminated** and a new node will be created with one exit to continue
calculating along the axis in the same direction with the new set of
stable phases. Algorithm C2 returns to algorithm C1 to calculate a new
line. An example of plots after a step calculation is shown in Fig.
14."

**Fig. 5 -- Algorithm C1** (p.5, "Flowchart of algorithm C1 to follow
equilibria along lines using algorithm A to calculate each equilibrium
and call algorithm C2 when algorithm A finds a change of the set of
stable phases"; transcribed from the rendered figure):

```
C1
 |
 v
search --no exit left--> Finished
 |
small axis increment
 |
 v
 A <--------------------------------------------+
 |                                               |
forbidden? --yes--> sign of increment changed ---+
 |no
 v
 A <--------------------------------------------+
 |                                               |
error? --yes--> [condition] increment (3x max) --+
 |no                     |
 |                  >3 times -> line terminated --+ (to search)
 v                                                 |
axis limit? --yes--> line terminated --------------+
 |no
 v
phase change? --yes--> C2
 |no
 v
save results
 |
Select axis with largest variation
 |
increment axis --(loop to "A")
```

"If algorithm A does not converge, the axis increment is decreased and
the condition modified up to three times. If algorithm A has not
converged after three times, the line is terminated and another exit is
searched. The line is also terminated when the axis limit is reached."

"The change of the set of stable phases is handled by algorithm C2. As
in the case of stepping, in order to handle the change of line,
algorithm C2 in Fig. 6 will first calculate the end point for the
current line. This is done by fixing to zero amount the phase that
wants to appear or disappear and release the current axis condition.
The current line is terminated at this point and algorithm C2 will
first check if the node has already been found. If so, an exit from the
node, corresponding to the current line, is marked as done. Otherwise a
new node point is created with one or more exits. **In the rather
simple cases where tie-lines are in the calculated plane, two exits
will be created.**"

**Fig. 6 -- Algorithm C2** (p.6, "Flowchart of algorithm C2 to handle a
change of the set of stable phases at the end of a line and generate a
node with exits for further lines" -- box sequence per the paper's own
prose above): `set alpha fix with 0 amount, release axis condition ->
call Algorithm A -> checks -> line terminated, +node`.

### 3.3. Mapping with 2 axes

"The same algorithms are used for calculating with one or two axis
variables but the handling of the nodes is different because the nodes
can contain more exits when there are two axes. The goal is to
calculate all lines without duplication or forgetting any connected
lines. The handling of the exits related to invariants in
multicomponent systems is particularly tricky. As previously discussed,
lines not connected to other lines is an issue that should be treated
with extra starting points."

"The mapping with two axes follows the branch on the right of algorithm
B in Fig. 4. After an initial equilibrium calculation, two conditions
are set as axes with a minimum and a maximum value and a maximum
increment for each."

"**One of the axes, normally with a potential condition, is then
incremented to calculate the equilibrium using algorithm A until the
set of the stable phases changes. A first node point will be created
with the appearing/disappearing phase as fix with zero amount and with
two exits, one in positive and the other in negative direction of the
other axis and algorithm C1 will be called.**"

"Algorithm C1 will begin searching the list of nodes to find exits to
generate lines. If there are none the mapping is finished. If it finds
an exit it will generate the line, similarly to the step case discussed
in Section 3.2, but in this case **one phase will be fix with zero
amount along the line, it will be a ZPF line**. Algorithm C1 may
increment either axis to follow the curvature of the line."

"When starting a line from an exit, there is an initial axis to vary.
It is first followed with a small increment in order to check that the
direction is correct. The axis condition will then be incremented
between each call of the algorithm A to calculate single equilibria
along the line. When an equilibrium has been properly calculated, it is
stored in the buffer and **algorithm C1 will check which axis varies
most rapidly and possibly change the axis to use for incrementing the
next iteration.**"

Node classification, Gibbs phase rule (Eq. 8):
```
f = n + 2 - p - c
```
"where f is the degree of freedoms, n is the number of components and p
is the number of stable phases. For each condition on a potential,
which is not an axis variable, c is incremented by 1. At an invariant
equilibrium f = 0. A binary isobaric phase diagram has f = 3 - p in Eq.
(8) and an invariant has thus 3 stable phases. An isobaric system with n
elements has p = n+1 number of stable phases at the invariant if T is
an axis variable and no other conditions on potentials. Each stable
phase at the invariant must have a ZPF line entering the invariant and
another ZPF exiting the invariant, the maximum number of exits is thus
equal to 2p. Amongst these, algorithm D finds the ones in the section to
be calculated."

"At the calculated invariant equilibrium all phases have known
compositions and other properties, it is only the amounts of phases
that are different at the exits. We have 2 axes and c-1 additional
conditions on extensive or normalized variables. Two of the p+1 phases
stable at the invariant must have zero amount at each exit as they
represent ZPF lines. We can use the remaining p-1 phases and the c-1
conditions to formulate a system of linear equations to obtain the
phase amounts at each exit." Eq. (9):
```
sum_{phi != phi1,phi2} A_C^phi * N^phi = A_C - sum_{phi=phi1,phi2} A_C^phi * N^phi
```
"where A_C etc. are the conditions and the sum over phi is for all
stable phases excluding two, phi1 and phi2, which are the two phases
with ZPF lines at the exit. If any N^phi is negative solving this
system of linear equations the exit is not in the calculated section."

"At each exit, the compositions of the stable phases are the same but
the amounts N^phi will be different and must be stored together with
the phases phi1 and phi2. Both of these have zero amount at the exit
but along one line phi1 will be stable with zero amount and phi2
forbidden to become stable at the first increments and along the other
line phi2 will be stable with zero amount and phi1 forbidden. **This is
tested by algorithm C1 in Fig. 5 at the first axis increment.**"

"As explained in Fig. 7, this procedure has to be repeated on all the
possible exits for the invariant i.e. looping over the pairs of phases
stable at the invariant."

"In iso-pleths, with extensive or normalized properties as conditions,
in addition to T most node points correspond to two crossing lines as
in Fig. 13(b). There are several such crossings in Fig. 13(a). Along
each line a phase is fixed with zero amount and at the crossing two of
the opposite regions has the same number of stable phases, in the other
two opposite regions the number of stable phase differ by two. This is
a characteristic feature of such iso-pleth sections. **Such a node
requires the creation of 3 exits when they are found.**"

"Finally invariant equilibria require special attention. In Fig. 13(c)
the two horizontal lines represent invariant equilibria with 6 stable
phases in a 5 component isobaric system. Algorithm D, in Fig. 7,
explains how to define the exits from such nodes. An invariant node can
be detected using the Gibbs phase rule" [Eq. (8) above].

**Fig. 7 -- Algorithm D**: "Flowchart of algorithm D to find all exits
in the plane of calculation from a multi-component invariant equilibrium
i.e. to find all pairs (phi_i, phi_j) with zero amount together with
positive amount of all other stable phases." (Image not transcribed
box-by-box; described fully in prose above, Eq. 9.)

**Fig. 8** (invariant/iso-pleth intersection geometry): "In (a), the
composition of the four phases taking part in the invariant equilibrium
are shown at the invariant temperature together with a red dashed line
showing the section being calculated... In (b), in red when alpha and
beta are in zero amount there is no intersection with the calculated
section but when gamma and delta are in zero amount, the intersection
of the green segment with the calculated section defines the node N2
with positive amount of alpha and beta. In (c), at a small increment in
x, the two exits of N2 are indicated with the status of the phases
alpha and beta."

### 3.4. Mapping explained line by line

"An example how the mapping of a ternary iso-pleth progresses is shown
in Fig. 9... The system is Fe-Cr-C with fixed 13% Cr by mass."

"The initial conditions selected by the user at 1100 K and 1% C by mass
gives an initial equilibrium where fcc and M7C3 are stable. Algorithm B
decreases C until it finds a new phase stable, bcc in this case."

"Algorithm B will generate a node with two exits to follow this phase
stable with zero amount in both directions of the C composition axis
using algorithm C1. One of these lines ends at the axis limit in Fig.
9(a). In Fig. 9(b) the line is followed in the other direction until a
new phase becomes stable, M23C6. This is an invariant equilibrium as we
now have 4 stable phases."

"Each line ends at an axis limit or when there is a change of the set
of stable phases. In algorithm C2 it is checked if the end of the line
represents an already existing exit node and the corresponding exit is
eliminated, otherwise a new node is created with new exits."

## 4. Plotting

"After a calculation with varying conditions has been performed, OC has
saved the results for all calculated equilibria. This means that the
results can be plotted in many different ways, not just a traditional
phase diagram."

### 4.1. Binary diagram

"The Ag-Cu diagram shown in Fig. 2(a) was calculated using T and mole
fraction of Cu, x_Cu, as axis variables but for the plotting many
different axes can be used as shown in Fig. 10... In (b) the overall
composition in Cu is plotted on the horizontal axis. **The lines
corresponds to the overall composition and that does not include the
composition of the phase fix with zero amount. But as OC saves complete
descriptions of each calculated equilibrium along the lines, the
composition of the phase with zero amount can be extracted and plotted
as in Fig. 10(a) if the user selects the mole fraction of Cu in ALL
STABLE PHASES as axis variable (which is the default when there are
tie-lines in the plane).**"

"All diagrams in Fig. 10, except for (b), are proper phase diagrams. **An
inexperienced user may obtain a diagram as (b) without intention.** It
is quite easy to detect that the proper axes have not been used in a
binary case but could be undetected in more complex cases."

"In Fig. 10(c) the activity of Cu is plotted as horizontal axis. Thus
the two solubility lines becomes a single one and the invariant
equilibrium is a point because the chemical potentials in the stable
phases are the same."

"In (d) the molar enthalpy of the different phases is plotted on the
vertical axis and the diagrams becomes topologically similar to a
ternary iso-thermal section with a large invariant area. Inside this
area the amount of phases varies but all potentials are constant."

"In (e), a similar diagram with the molar entropy of the phases on the
vertical axis is shown. In (f), the molar Gibbs energy of each phase is
plotted on the vertical axis. As the G functions vary with temperature,
the fcc single phase areas are not any more a single line but a portion
of the plane."

### 4.2. Ternary iso-pleth

"In Fig. 11(a), the phase diagram generated after the mapping explained
in detail in Fig. 9 is shown. The labels linked to the color of the
lines allow to know which phases are stable in the different regions.
It is however impossible from this diagram to read the composition of
the phases and their amount. The two iso-thermal diagrams plotted in
Fig. 11(b) and 11(c) can provide this information at the corresponding
temperatures."

"In Fig. 11(b) and 11(c), the tie-lines are drawn as light green lines.
The large green areas correspond to tie-triangles i.e. areas where
three phases are stable."

### 4.3. Iso-pleth section in a complex system

"When increasing the number of components, phase diagrams may become
more complex and difficult to understand. In Fig. 13 an iso-pleth
section for a five component High Speed Steel (HSS) is presented. It
has 5% Cr, 8% Mo, 1% V by mass, rest Fe and C. The lines separate
regions with different sets of stable phases and tie-lines cannot be
drawn because at least one of the phase compositions is outside the
plane of the diagram."

"Fig. 13(b) is a zoom in the area around 1600 K and low C where there is
a node involving three phases: the liquid, bcc and fcc. Four lines are
intersecting at this node. In Fig. 13(c) some of the invariants at low
T are shown, they have constant T and each has 6 stable phases and in
these cases 8 exits."

### 4.4. Step calculation

"It is useful to complement the phase diagram in Fig. 13 with additional
single equilibria or variations of a single axis to understand a
complex material under consideration."

"For this example, we have calculated a 'step' diagram at fixed content
of C, 0.8% per mass, and varied T. In Fig. 14, three diagrams from this
calculation have been plotted."

## 5. Summary

"The main intention of this paper was to explain how one can calculate
ZPF lines in multi-component systems. It is possible thanks to the
flexibility of the use of Lagrange multiplier method in the calculation
of single equilibria where most of the computation time is spent."

"**The algorithms presented here can be extended to calculate ZPF lines
with 3 or more axes. In such a case there will be 2 or more phases fix
with zero amount along each line.**"

---

## Appendix: investigation findings against this codebase (brief)

Cross-checked against this codebase (`calc/diagram`, `calc/equil`),
OpenCalphad (`D:\codes\opencalphad\src\stepmapplot\smp2A.F90`, `src/models
/gtp3Y.F90`/`gtp3_dd2.F90`), and pycalphad (`D:\codes\pycalphad\pycalphad
\mapping\`).

- **Algorithm A `step or map?` early exit (Fig. 1) -- gap found and
  fixed.** `EquilibriumSolverV2.solve()` had no exit matching Fig. 1's
  "gamma^phi>0 or N^alpha<0 -> step or map? -> yes -> C1" branch; it
  always resolved a stable-set change in place and kept iterating.
  Fixed: new `solve(..., boolean stopOnStableSetChange)` overload +
  `EquilibriumResult.StableSetChange`/`getStableSetChange()`. Solver-only,
  opt-in; `MapTracer`/`StepTracer` untouched. 104 tests, 0
  failures/errors/skipped after the change.
- **T/P-walk composition-holding bug (§3.3, ZPF line interior) -- found,
  not yet fixed.** `MapTracer.walkOneSegmentInternal`'s T/P-walk branch
  holds overall composition fixed for a whole 2+-phase line; per phase
  rule, once 2+ phases are stable, composition is Algorithm A's OUTPUT,
  not an input condition. Confirmed against a real OC reference
  (`docs/oc_reference_tests/agcu_full_map_clean_narrow_output.txt`): OC's
  boundary composition drifts away from the fixed column, so OC's walk
  survives cleanly across the whole T range while this codebase's fixed-
  column walk spuriously terminates. Fix design stalled on a
  degrees-of-freedom mismatch (dropping one mass-balance row while
  keeping every lambda/DeltaOmega unknown is under-determined by 1) --
  NOT resolved, needs re-derivation from first principles before any new
  solver code.
- **Composition sets / miscibility gaps (§2.3.3) -- gap, reporting layer
  only.** The paper's own `#<digit>` convention for a miscibility-gap
  phase splitting into two stable instances is confirmed (also in OC's
  `CSAUTO`/`enter_composition_set`, `gtp3Y.F90`). `EquilibriumSolverV2`/
  `GridMinimizer` already carry two independent stable slots internally
  when needed; `Node`'s plain `Set<String>` stable-phase representation
  cannot distinguish them -- open at the `Node`/diagram-reporting layer,
  not the solver.
- **Axis-switching mid-line (§3.3, "select axis with largest variation")
  -- gap, not implemented.** `MapDiagramTracer`/`MapTracer` fix one
  walked axis for an entire line (documented v1 scope limit); the paper
  and OC's `map_step` (`smp2A.F90` ~line 696) both reselect the
  fastest-varying axis after every point.
- **Algorithm B (Fig. 4) dispatch -- role clarified, one real gap found
  and FIXED.** Algorithm B's defining shape, from Fig. 4 + the paper's
  own prose ("the simplest way to start is to set the appropriate
  conditions for a single equilibrium calculation **and then** select
  one or more conditions as axis variable"): call Algorithm A EXACTLY
  ONCE at the user's starting conditions to get a genuine, converged
  equilibrium, THEN branch on axis count (1 -> STEP, 2 -> MAP) using
  THAT SAME solved point -- never re-deriving it. §3.3 confirms the MAP
  branch's own increment loop is a continuation FROM that point, not an
  independent search: "**After an initial equilibrium calculation**, two
  conditions are set as axes... One of the axes... is then
  incremented... until the set of the stable phases changes."
  - STEP branch (`StepDiagramTracer.drain`) already matched this: solves
    once, then nodes that exact result directly -- correct per §3.2
    ("generates a node at the initial equilibrium").
  - MAP branch: on a second, closer re-read, **retracting the "starting
    point must be checked for a boundary" half of an earlier draft of
    this finding -- not something Sundman's Algorithm B asks for.** The
    paper's exact sentence describes solving once, THEN incrementing; it
    never asks Algorithm B to special-case "what if the very first point
    is already a boundary" -- the paper's model presumes a user starts a
    map from inside a known phase field. `MapTracer`'s walk behavior
    (test for a stable-set change starting from the FIRST increment past
    the seed, not the seed itself) is paper-faithful.
  - **The genuine gap, now fixed:** before this fix, `CalculationSession
    .calculatePhaseDiagram`'s STEP/MAP fork ran BEFORE any equilibrium
    was solved -- `StepDiagramTracer.drain` and `MapDiagramTracer.drain`
    (via `MapTracer.findInitialBoundary`) each independently called
    `EquilibriumSolveHelper.solveOrSentinel` on their own, so two
    separate solves existed at the same starting condition where the
    paper's diagram has exactly one shared one. **Fix**: added
    `PhaseDiagramEngine.solveInitialEquilibrium` (the shared `A` box,
    called once in `calculatePhaseDiagram` before the fork), plus
    `EquilibriumResult`-accepting overloads threaded through
    `PhaseDiagramEngine.drainStepLoop`/`drainMapLoop`,
    `StepDiagramTracer.drain`, `MapDiagramTracer.drain` (both the
    `AxisConfig`-pair and `ConditionSet` forms), and two new
    `MapTracer.findInitialBoundary` overloads that accept the
    already-solved result instead of re-solving. Existing no-`startResult`
    overloads are kept (`null` delegates to the old self-solving
    behavior) for standalone callers, e.g. tests, with no shared result
    to offer -- no existing call site changed behavior. Full test suite
    re-run clean after the change (`./gradlew test --rerun`, no
    filtering).
- **Node matching / identity, exit-count-at-a-node (§3.1, §3.3) -- open
  implementation choices, not gaps.** The paper states these as prose
  requirements without a concrete algorithm (node identity: stable phase
  set + chemical potentials, no tolerance specified; non-invariant exit
  count: 2 for tie-line-in-plane, 3 for isopleth-crossing, stated
  directly in §3.3's prose, not derived from Eq. 8). This codebase's
  `Node.matches`/`NodeGeometry` make an explicit documented choice here,
  cross-checked against OC's `map_node`/`smp2A.F90` ~4788-4806.
- **Multi-start-point search (§3, Fe-Mo gamma-loop) -- confirmed NOT a
  gap.** Neither the paper nor OC has a working algorithm here: the
  paper explicitly declines ("such issues will not be considered"); OC's
  own `auto_startpoints` (`smp2A.F90:9342-9498`) is unreachable dead code
  behind `GSNOAUTOSP`, every call site commented out.
- **pycalphad cross-reference.** `pycalphad/mapping/zpf_equilibrium.py`'s
  `ExitHint.NORMAL`/`POINT_IS_EXIT` independently confirms the same
  STEP-vs-MAP start-node distinction under different names; no
  pycalphad-specific finding beyond structural confirmation.
