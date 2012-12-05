

set ylabel "current (mA)"
set xlabel "simulation time (milli-seconds)"
plot 'current.dat' using ($1 / 1000000):($2 / 1000000) with steps title "Power saving mode"
