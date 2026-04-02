\## Overview



This document defines a simplified and implementation-focused architecture for a CALPHAD-based thermodynamic engine.



The system is organized into \*\*three core layers only\*\*:



1\. UI Layer  

2\. Thermodynamic System Layer (includes data + models)  

3\. Calculation Layer  



This structure follows PANDAT philosophy and Sundman’s separation of \*\*static (system)\*\* and \*\*dynamic (calculation)\*\* parts fileciteturn1file0.



\---



\## Core Data Flow (UI → UI)



A typical calculation starts when the user provides input through the UI, such as the elements involved, selected phases, temperature, composition, and the type of calculation (for example equilibrium or step). The UI converts this into a \*\*problem object\*\*, which contains all required information to define the thermodynamic problem.



At this point, the \*\*Thermodynamic System Layer is activated once\*\*. The database is parsed, relevant parameters are extracted, and \*\*phase models are constructed\*\* for all required phases. These models encapsulate the Gibbs energy functions and their derivatives. This collection of models forms a \*\*thermodynamic system\*\*, which remains fixed throughout the calculation.



The problem object, together with the thermodynamic system, is then passed to the \*\*Calculation Layer\*\*. The calculation engine examines the problem type and selects the appropriate calculation module (for example, equilibrium calculation).



The selected calculation module begins the computation. Internally, it runs a numerical solver that iteratively updates variables such as phase amounts, compositions, and chemical potentials. During each iteration, the solver requires thermodynamic quantities. For this, it calls the phase models in the thermodynamic system layer, passing the current temperature and composition variables. The thermodynamic system evaluates the Gibbs energy and its derivatives and returns these values to the calculation module.



Using the returned values, the calculation module assembles the governing equations, solves for updates to the variables, and proceeds to the next iteration. This \*\*two-way interaction between the calculation layer and the thermodynamic system layer\*\* continues until convergence is achieved.



Once the solution converges, the calculation module constructs a \*\*result object\*\* containing phase amounts, compositions, chemical potentials, and other relevant outputs. This result is returned to the UI layer.



Finally, the UI interprets the result object and presents it to the user in the form of tables, graphs, or logs.



\---



\## Layer Definitions



\### 1. UI Layer



\*\*Responsibilities:\*\*

\- Accept user input

\- Create problem object

\- Trigger calculations

\- Display results (tables, graphs, logs)



\*\*Does NOT:\*\*

\- Perform thermodynamic calculations

\- Access phase models directly



\---



\### 2. Thermodynamic System Layer (Static Layer)



This layer combines both \*\*data\*\* and \*\*models\*\*.



\#### (a) Database Sub-layer

\- Parses `.tdb` files

\- Stores thermodynamic parameters



\#### (b) Gibbs Model Sub-layer

\- Builds phase models

\- Provides:

&#x20; - G(T, y)

&#x20; - ∂G/∂y

&#x20; - ∂²G/∂y²



\*\*Key Property:\*\*

\- Built once per system

\- Remains unchanged during calculation



\---



\### 3. Calculation Layer (Dynamic Layer)



\*\*Responsibilities:\*\*

\- Receives problem + thermodynamic system

\- Selects calculation type (equilibrium, step, map, etc.)

\- Runs numerical solver

\- Controls iteration and convergence



\*\*Core Behavior:\*\*

\- Drives solver loop

\- Repeatedly queries thermodynamic system

\- Updates variables until equilibrium



\---



\## Static vs Dynamic Separation



\### Static (Thermodynamic System Layer)

\- Phase models

\- Parameters

\- Database-derived structure



\### Dynamic (Calculation Layer)

\- Phase amounts

\- Compositions

\- Chemical potentials

\- Solver iterations



\---



\## Two-Way Interaction (Critical Concept)



The core of the engine is a feedback loop:



Calculation Layer ↔ Thermodynamic System Layer



\- Calculation layer sends: T, compositions, phase info  

\- System layer returns: G, ∂G, ∂²G  



This loop continues until equilibrium is reached.



\---



\## Summary



This architecture:



\- Uses only \*\*three layers\*\* (UI, System, Calculation)

\- Clearly separates \*\*static and dynamic parts\*\*

\- Ensures \*\*clean two-way interaction\*\*

\- Aligns with CALPHAD theory and Sundman algorithm structure fileciteturn1file0

\- Is minimal, scalable, and ready for implementation

