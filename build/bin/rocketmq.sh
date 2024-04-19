#!/bin/bash

# 拷贝配置文件
mkdir -p /opt/rocketmq/broker/conf
cp ../conf/broker.conf /opt/rocketmq/broker/conf/broker.conf
# 创建RocketMQ NameServer单实例容器
docker create --expose=9876 --privileged=true \
-v /opt/rocketmq/nameserver/logs:/home/rocketmq/logs \
--network=red-packet --network-alias=rocketmq-nameserver \
--name=rocketmq-nameserver --restart=unless-stopped \
apache/rocketmq:5.1.4 \
sh mqnamesrv
# 创建RocketMQ Broker单实例容器
docker create --expose=10911 --expose=10909 --privileged=true \
-v /opt/rocketmq/broker/logs:/root/logs \
-v /opt/rocketmq/broker/store:/root/store \
-v /opt/rocketmq/broker/conf/broker.conf:/home/rocketmq/broker.conf \
--network=red-packet --network-alias=rocketmq-broker \
--name=rocketmq-broker --restart=unless-stopped \
apache/rocketmq:5.1.4 \
sh mqbroker -c /home/rocketmq/broker.conf
