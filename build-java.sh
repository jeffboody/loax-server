#!/bin/bash
echo `date` > project/res/raw/timestamp.raw
cp -f fonts/whitrabt/whitrabt.texgz project/res/raw/whitrabt.raw
ant -f project/build.xml clean
ant -f project/build.xml debug
