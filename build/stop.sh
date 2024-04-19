#!/bin/bash

docker stop nginx
docker stop app
docker stop rocketmq-broker
docker stop rocketmq-nameserver
docker stop redis
docker stop mysql
