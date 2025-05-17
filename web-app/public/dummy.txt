* Dummy Netlist for Tech Components

* Power Supply
VDD Vdd 0 DC 1.8V       ; Supply Voltage
VSS Vss 0 DC 0V         ; Ground

* NMOS and PMOS transistors
M1 out in Vdd Vdd PMOS  ; PMOS transistor
M2 out in Vss Vss NMOS  ; NMOS transistor

* Capacitor
C1 out 0 1p             ; 1 pF capacitor

* Resistor
R1 in 0 10k             ; 10 kΩ resistor

* Diode
D1 out 0 DModel         ; Diode

* BJT
Q1 out in Vdd NPNModel  ; NPN BJT

* Models
.model PMOS PMOS (LEVEL=1)
.model NMOS NMOS (LEVEL=1)
.model DModel D
.model NPNModel NPN

* Simulation Command
.tran 1n 10u
.end
