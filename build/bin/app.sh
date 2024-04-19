#!/bin/bash

# 拷贝配置文件
mkdir -p /opt/app/config
cp ../conf/application.properties /opt/app/config/application.properties
cp ../conf/application-biz-test.properties /opt/app/config/application-biz-test.properties
cp ../conf/application-mysql-test.properties /opt/app/config/application-mysql-test.properties
cp ../conf/application-redis-test.properties /opt/app/config/application-redis-test.properties
cp ../conf/application-rocketmq-test.properties /opt/app/config/application-rocketmq-test.properties
cp ../conf/logging-test.cfg /opt/app/config/logging-test.cfg
cp ../conf/redisson-test.yml /opt/app/config/redisson-test.yml
# 拷贝jar包
cp ../jar/RedPacket.jar /opt/app/RedPacket.jar
# 创建Java应用单实例容器
docker create --expose=8080 --privileged=true \
-v /opt/app:/app \
--network=red-packet --network-alias=app \
--name=app --restart=unless-stopped \
-w /app \
openjdk:17 \
/usr/bin/java -jar /app/RedPacket.jar
