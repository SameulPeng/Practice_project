#!/bin/bash

# 拷贝配置文件
mkdir -p /opt/mysql/conf
cp ../conf/my.cnf /opt/mysql/conf/my.cnf
# 拷贝初始化sql脚本
mkdir -p /opt/mysql/init
cp ../sql/init.sql /opt/mysql/init/init.sql
# 拷贝重置数据sql脚本
mkdir -p /opt/mysql/reset
cp ../sql/reset.sql /opt/mysql/reset/reset.sql
# 创建MySQL单实例容器
docker create --expose=3306 --privileged=true \
-v /opt/mysql/conf:/etc/mysql/conf.d \
-v /opt/mysql/logs:/var/log/mysql \
-v /opt/mysql/data:/var/lib/mysql \
-v /opt/mysql/init:/docker-entrypoint-initdb.d \
-e MYSQL_ROOT_PASSWORD=1234 \
--network=red-packet --network-alias=mysql \
--name=mysql --restart=unless-stopped \
mysql:8.0.31
