#!/bin/bash

docker start mysql
docker start redis
docker start rocketmq-nameserver
docker start rocketmq-broker
docker start app
docker start nginx
