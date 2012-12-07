#!/usr/bin/gnuplot -persist

set yrange [10:35]
set ylabel "current (mA)"
set xlabel "simulation time (seconds)"

scaleY(y)=((y/1000000) > 40) ? 1/0 : (y/1000000)
#  
# 
plot 'power-saving-current.dat'    using ($1/1000000000):(scaleY($2)) with steps title "Power saving mode", \
     'no-power-saving-current.dat' using ($1/1000000000):(scaleY($2)) with steps title "Transmitting at max tx power"
