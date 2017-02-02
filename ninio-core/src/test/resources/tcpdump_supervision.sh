#!/bin/sh

itf=$1
port=$2
time=$3

while [ 1 ];
do
tcpdump -i $itf -A -w /dev/null dst port $port 2> tcpdump_sent.txt &
pid_sent=$!
tcpdump -i $itf -A -w /dev/null src port $port 2> tcpdump_received.txt &
pid_received=$!
sleep $time
kill $pid_sent
kill $pid_received
sent=`cat tcpdump_sent.txt | head -2 | tail -1 | cut -d ' ' -f 1`
received=`cat tcpdump_received.txt | head -2 | tail -1 | cut -d ' ' -f 1`

precision=1000

if [ $sent -eq "0" ];
then
percent="NaN"
else
delta=$(( $sent - $received ))
delta1=$(( precision * $delta ))
percent=$(( $delta1 / $sent ))
fi

echo "sent=$sent, received=$received, $percent /$precision lost"
done

