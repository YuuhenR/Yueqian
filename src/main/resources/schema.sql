create database if not exists ticket_assistant default character set utf8mb4 collate utf8mb4_unicode_ci;
use ticket_assistant;

create table if not exists chat_session (
    id bigint primary key auto_increment,
    user_id varchar(64) not null,
    title varchar(120) not null,
    pinned tinyint default 0,
    deleted tinyint default 0,
    create_time datetime not null,
    update_time datetime not null
) engine=InnoDB default charset=utf8mb4;

create table if not exists app_user (
    id bigint primary key auto_increment,
    username varchar(64) not null unique,
    display_name varchar(80) not null,
    password_hash varchar(128) not null,
    password_salt varchar(64) not null,
    role varchar(20) not null,
    enabled tinyint default 1,
    create_time datetime not null,
    index idx_app_user_role (role)
) engine=InnoDB default charset=utf8mb4;

create table if not exists passenger_profile (
    id bigint primary key auto_increment,
    user_id varchar(64) not null unique,
    passenger_name varchar(80) not null,
    id_card varchar(192) not null,
    create_time datetime not null,
    update_time datetime not null,
    index idx_passenger_profile_user (user_id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists pending_ticket_order (
    id bigint primary key auto_increment,
    user_id varchar(64) not null,
    session_id bigint,
    passenger_name varchar(80) not null,
    id_card varchar(192) not null,
    train_no varchar(32) not null,
    departure_station varchar(80),
    arrival_station varchar(80),
    travel_date date not null,
    seat_type varchar(20) not null,
    ticket_count int default 1,
    estimated_price decimal(10, 2) not null,
    status varchar(20) not null,
    create_time datetime not null,
    index idx_pending_user_status (user_id, status)
) engine=InnoDB default charset=utf8mb4;

create table if not exists chat_message (
    id bigint primary key auto_increment,
    session_id bigint not null,
    user_id varchar(64) not null,
    role varchar(24) not null,
    content text not null,
    tool_name varchar(80),
    create_time datetime not null,
    index idx_message_session_user (session_id, user_id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists ticket_order (
    id bigint primary key auto_increment,
    order_no varchar(40) not null unique,
    user_id varchar(64) not null,
    session_id bigint,
    passenger_name varchar(80) not null,
    id_card varchar(192) not null,
    train_no varchar(32) not null,
    departure_station varchar(80),
    arrival_station varchar(80),
    travel_date date not null,
    seat_type varchar(20) not null,
    seat_no varchar(20) not null,
    ticket_count int default 1,
    price decimal(10, 2) not null,
    status varchar(20) not null,
    booking_time datetime not null,
    refund_fee decimal(10, 2),
    refund_time datetime,
    create_time datetime not null,
    update_time datetime not null,
    deleted tinyint default 0,
    index idx_order_user (user_id),
    index idx_order_passenger (passenger_name, id_card)
) engine=InnoDB default charset=utf8mb4;

create table if not exists audit_log (
    id bigint primary key auto_increment,
    user_id varchar(64),
    action varchar(80) not null,
    detail varchar(1000),
    ip varchar(64),
    create_time datetime not null,
    index idx_audit_user_time (user_id, create_time)
) engine=InnoDB default charset=utf8mb4;

create table if not exists destination_recommendation (
    id bigint primary key auto_increment,
    station varchar(80) not null,
    tag varchar(80) not null,
    train_no varchar(32) not null,
    reason varchar(240) not null,
    popularity int default 0,
    create_time datetime not null,
    unique key uk_destination_station (station)
) engine=InnoDB default charset=utf8mb4;

alter table pending_ticket_order modify column id_card varchar(192) not null;
alter table ticket_order modify column id_card varchar(192) not null;

insert into chat_session(user_id, title, pinned, deleted, create_time, update_time)
select 'demo-user', '行程咨询', 1, 0, now(), now()
where not exists (select 1 from chat_session where user_id = 'demo-user' and title = '行程咨询');

insert into app_user(username, display_name, password_hash, password_salt, role, enabled, create_time)
select 'demo-user', '普通用户', sha2(concat('demo-pass', 'rail-demo'), 256), 'rail-demo', 'USER', 1, now()
where not exists (select 1 from app_user where username = 'demo-user');

insert into app_user(username, display_name, password_hash, password_salt, role, enabled, create_time)
select 'admin', '管理员', sha2(concat('admin-pass', 'rail-admin'), 256), 'rail-admin', 'ADMIN', 1, now()
where not exists (select 1 from app_user where username = 'admin');

insert into destination_recommendation(station, tag, train_no, reason, popularity, create_time)
select '上海虹桥', '商务高铁', 'G1', '高频车次，适合当日往返。', 98, now()
where not exists (select 1 from destination_recommendation where station = '上海虹桥');
insert into destination_recommendation(station, tag, train_no, reason, popularity, create_time)
select '杭州东', '周末短途', 'G31', '车程短，城市休闲资源集中。', 92, now()
where not exists (select 1 from destination_recommendation where station = '杭州东');
insert into destination_recommendation(station, tag, train_no, reason, popularity, create_time)
select '南京南', '人文路线', 'G5', '历史景点密集，适合两日行程。', 86, now()
where not exists (select 1 from destination_recommendation where station = '南京南');
insert into destination_recommendation(station, tag, train_no, reason, popularity, create_time)
select '广州南', '美食路线', 'G79', '餐饮选择丰富，夜间返程方便。', 84, now()
where not exists (select 1 from destination_recommendation where station = '广州南');
insert into destination_recommendation(station, tag, train_no, reason, popularity, create_time)
select '成都东', '慢旅行', 'G87', '城市节奏舒缓，适合休闲安排。', 82, now()
where not exists (select 1 from destination_recommendation where station = '成都东');
insert into destination_recommendation(station, tag, train_no, reason, popularity, create_time)
select '西安北', '古都路线', 'G55', '文化资源集中，适合假期出行。', 79, now()
where not exists (select 1 from destination_recommendation where station = '西安北');

insert into ticket_order(order_no, user_id, session_id, passenger_name, id_card, train_no, departure_station, arrival_station, travel_date, seat_type, seat_no, ticket_count, price, status, booking_time, refund_fee, refund_time, create_time, update_time, deleted)
select
    concat('SIM', lpad(a.n * 1000 + b.n, 8, '0')),
    if((a.n * 1000 + b.n) % 13 = 0, 'admin', 'demo-user'),
    null,
    concat('乘客', a.n * 1000 + b.n),
    concat('110101199001', lpad((a.n * 1000 + b.n) % 1000000, 6, '0')),
    concat('G', 1 + ((a.n * 1000 + b.n) % 900)),
    case (a.n * 1000 + b.n) % 4 when 0 then '北京' when 1 then '上海' when 2 then '广州' else '成都' end,
    case (a.n * 1000 + b.n) % 4 when 0 then '上海' when 1 then '杭州' when 2 then '深圳' else '西安' end,
    date_add('2026-07-01', interval ((a.n * 1000 + b.n) % 60) day),
    case (a.n * 1000 + b.n) % 4 when 0 then '硬座' when 1 then '软座' when 2 then '硬卧' else '软卧' end,
    concat(1 + ((a.n * 1000 + b.n) % 12), '车', 1 + ((a.n * 1000 + b.n) % 20), 'A座'),
    1,
    80 + ((a.n * 1000 + b.n) % 420),
    if((a.n * 1000 + b.n) % 5 = 0, '已退票', '已出票'),
    date_sub(now(), interval ((a.n * 1000 + b.n) % 90) day),
    if((a.n * 1000 + b.n) % 5 = 0, 12.80, null),
    if((a.n * 1000 + b.n) % 5 = 0, now(), null),
    now(),
    now(),
    0
from
    (select d0.n + d1.n * 10 as n from
        (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) d0
        cross join
        (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) d1
    ) a
    cross join
    (select d0.n + d1.n * 10 + d2.n * 100 as n from
        (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) d0
        cross join
        (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) d1
        cross join
        (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) d2
    ) b
where not exists (select 1 from ticket_order where order_no = 'SIM00000000')
limit 100000;

update ticket_order
set user_id = 'analytics-seed'
where order_no like 'SIM%';

insert into ticket_order(order_no, user_id, session_id, passenger_name, id_card, train_no, departure_station, arrival_station, travel_date, seat_type, seat_no, ticket_count, price, status, booking_time, refund_fee, refund_time, create_time, update_time, deleted)
select 'USERDEMO0001', 'demo-user', null, '张三', '110101199001011234', 'G88', '广州南', '贵阳北', '2026-08-01', '二等座', '3车12A座', 1, 128, '已出票', now(), null, null, now(), now(), 0
where not exists (select 1 from ticket_order where order_no = 'USERDEMO0001');

insert into ticket_order(order_no, user_id, session_id, passenger_name, id_card, train_no, departure_station, arrival_station, travel_date, seat_type, seat_no, ticket_count, price, status, booking_time, refund_fee, refund_time, create_time, update_time, deleted)
select 'USERDEMO0002', 'demo-user', null, '李四', '110101199002022345', 'G31', '北京南', '杭州东', '2026-08-03', '一等座', '5车08C座', 1, 268, '已出票', now(), null, null, now(), now(), 0
where not exists (select 1 from ticket_order where order_no = 'USERDEMO0002');

insert into ticket_order(order_no, user_id, session_id, passenger_name, id_card, train_no, departure_station, arrival_station, travel_date, seat_type, seat_no, ticket_count, price, status, booking_time, refund_fee, refund_time, create_time, update_time, deleted)
select 'USERDEMO0003', 'demo-user', null, '王五', '110101199003033456', 'G55', '西安北', '成都东', '2026-08-05', '二等座', '7车16D座', 1, 128, '已退票', date_sub(now(), interval 1 day), 12.80, now(), now(), now(), 0
where not exists (select 1 from ticket_order where order_no = 'USERDEMO0003');
