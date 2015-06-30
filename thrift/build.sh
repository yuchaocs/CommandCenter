#!/bin/bash

rm -rf gen-java

thrift -r --gen java service.thrift

