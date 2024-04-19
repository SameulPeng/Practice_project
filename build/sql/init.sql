# 创建测试用库表
create database practice;
use practice;
create table account
(
    user_id  varchar(50)     not null
        primary key,
    balance  bigint unsigned not null,
    username varchar(20)     not null,
    password varchar(60)     not null
);

# 创建测试用数据
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user001', 5000000, 'Aaron', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user002', 5000000, 'Bill', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user003', 5000000, 'Catherine', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user004', 5000000, 'Danny', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user005', 5000000, 'Ellie', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user006', 5000000, 'Fort', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user007', 5000000, 'Gauss', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user008', 5000000, 'Helen', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user009', 5000000, 'Ivy', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user010', 5000000, 'John', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user011', 5000000, 'Ken', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user012', 5000000, 'Lily', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user013', 5000000, 'Morse', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user014', 5000000, 'Nick', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user015', 5000000, 'Oscar', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user016', 5000000, 'Penny', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user017', 5000000, 'Quinn', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user018', 5000000, 'Rose', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user019', 5000000, 'Sam', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
INSERT INTO practice.account (user_id, balance, username, password) VALUES ('user020', 5000000, 'Tom', 'A6xnQhbz4Vx2HuGl4lXwZ5U2I8iziLRFnhP5eNfIRvQ=');
