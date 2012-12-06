#!/usr/bin/gnuplot -persist

set ylabel "current (mA)"
set xlabel "simulation time (seconds)"

plot 'power-saving-current.dat'     with steps title "Power saving mode", \
     'power-saving-current-2.dat'   with steps title "Manually setting min tx power", \
     'no-power-saving-current.dat'  with steps title "Transmitting at max tx power"
