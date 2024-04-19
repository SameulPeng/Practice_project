#!/bin/bash

# 拷贝配置文件
mkdir -p /opt/nginx/conf
cp ../conf/nginx.conf /opt/nginx/conf/nginx.conf
# 创建Nginx单实例容器
docker create -p 80:80 --privileged=true \
-v /opt/nginx/conf/nginx.conf:/etc/nginx/nginx.conf \
-v /opt/nginx/conf/conf.d:/etc/nginx/conf.d \
-v /opt/nginx/logs:/var/log/nginx \
-v /opt/nginx/html:/usr/share/nginx/html \
--network=red-packet --network-alias=nginx \
--name=nginx --restart=unless-stopped \
nginx:1.24.0
