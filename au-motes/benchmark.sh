#!/bin/bash

for i in {0..9}
do
  # Use one of the 10 predefined sink setups
  ruby generate_sinks.rb $i

  mrsh -i reset | grep "In Reception" &

  process=$!

  # The extra 5 seconds allows leeway for setup etc.
  sleep 65

  killall mrsh
  killall _mrsh
done
