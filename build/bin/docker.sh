#!/bin/bash

if [ $1 != "--skip-docker-install" ] || [ $2 != "true" ]
then
  # 准备Docker环境
  yum -y install gcc
  yum -y install gcc-c++
  yum -y install yum-utils
  yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
  yum makecache fast
  # 安装Docker
  yum -y install docker-ce docker-ce-cli containerd.io
  systemctl start docker
  docker version
  systemctl enable docker
fi
# 拉取镜像
docker pull openjdk:17
docker pull redis:7.2.3
docker pull apache/rocketmq:5.1.4
docker pull mysql:8.0.31
docker pull nginx:1.24.0
# 创建网络模式
docker network create red-packet
