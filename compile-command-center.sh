#!/bin/bash

git pull

ant jar-cc

cp commandcenter.jar ~/mulage_project/commandcenter

echo "generated jar file copied to ~/mulage_project/commandcenter"
