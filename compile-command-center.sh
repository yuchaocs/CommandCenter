#!/bin/bash

git pull

ant jar-cc

cp commandcenter.jar ~/nutch-test/commandcenter

echo "generated jar file copied to ~/nutch-test/commandcenter"
