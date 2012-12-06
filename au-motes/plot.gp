#!/usr/bin/gnuplot -persist

set ylabel "current (mA)"
set xlabel "simulation time (seconds)"

plot 'power-saving-current.dat'    using ($1 / $1000000000):($2 / 1000000) with steps title "Power saving mode",
     'no-power-saving-current.dat' using ($1 / $1000000000):($2 / 1000000) with steps title "Transmitting at max tx power"
