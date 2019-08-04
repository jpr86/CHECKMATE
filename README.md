# Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and Multi-Agent Training Environment (CHECKMATE)
CHECKMATE is a fast executing, agent-based simulation of air combat intended to support development of advanced algorithms

[This Version is Pre-release and is not yet stable. Patience, Padawan.]

CHECKMATE is fast executing agent-based simulation of air combat designed to support
rapid development of advanced algorithms. It is constructed using the Fast Universal
Simulation Engine (FUSE). CHECKMATE has many features and can support complex 3D simulations
with terrain and sophisticated systems models. However, it was designed to support use of
advanced algorithms for CONOPS, system, and behavior development and optimization. CHECKMATE
is frequently used with JEvolve, JPSO, JFuzzy, and other open source AI/ML software for
autonomy behavior development.

The "scenarios" directory contains examples of scenario files, as well as platform, systems, and weapons database files. 
To launch CHECKMATE, use the provided script files -- checkmate.bat on Windows and checkmate.sh on Linux. 
For example, to run a provided scenario from the Windows command line, use @checkmate.bat file:scenarios/dev_scenario.xml. 
Other command line arguments allow you to configure your run. To see these, use the following command (on Windows): @checkmate.bat help.

CHECKMATE uses FUSE for 2D visualization and NRL's SIMDIS for 3D. To generate a SIMDIS .asi file, 
use the "-s" command line argument. For example: @checkmate.bat -s simdis.asi file:scenarios/rounddirectional/rd_scenario.xml 
will create a simdis.asi file for a run of the provided rd_scenario that can be read and visualized by SIMDIS. 
To get SIMDIS, go to the SIMDIS website. Also, the simdis directory contains a SIMDIS preferences file for use with CHECKMATE. 
For more information, see the README file in the simdis folder.