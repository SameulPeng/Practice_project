#!bin/bash

# 拷贝配置文件
mkdir -p /opt/redis/conf
cp ../conf/redis.conf /opt/redis/conf/redis.conf
# 创建Redis单实例容器
docker create --expose=6379 --privileged=true \
-v /opt/redis/conf/redis.conf:/etc/redis/redis.conf \
-v /opt/redis/data:/data \
--network=red-packet --network-alias=redis \
--name=redis --restart=unless-stopped \
redis:7.2.3 \
redis-server /etc/redis/redis.conf
