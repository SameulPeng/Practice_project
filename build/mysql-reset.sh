#!/bin/bash

# 重置测试用数据
docker exec -i mysql mysql -uroot -p1234 < /opt/mysql/reset/reset.sql
