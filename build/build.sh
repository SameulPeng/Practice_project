#!/bin/bash

cd ./bin
sh docker.sh $1 $2
sh mysql.sh
sh redis.sh
sh rocketmq.sh
sh app.sh
sh nginx.sh
