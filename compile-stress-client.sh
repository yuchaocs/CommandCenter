#!/bin/bash

cp test/edu/umich/clarity/service/StressClient.java src/edu/umich/clarity/service/

ant jar-sc

cp stressclient.jar ~/mulage_project/loadgen/ 

echo "generated jar file copied to ~/mulage_project/loadgen/"
