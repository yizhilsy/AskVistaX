create database IF NOT EXISTS askvistax;
use askvistax;

# 用户表
create table users
(
    uid             char(36) unique not null comment '用户标号uid',
    userAccount     varchar(30) primary key comment '用户账号',
    passwordHash    varchar(80) not null comment '用户密码（哈希）',
    userName        varchar(20) not null comment '用户名',
    roleType        tinyint unsigned not null comment '用户角色类型',
    avatar          varchar(300) comment '用户头像url',
    birth           DATE not null comment '用户出生年月',
    phone           varchar(20) not null comment '用户联系电话',
    email           varchar(40) not null comment '用户联系邮箱',
    createTime      datetime not null comment '用户账号创建时间',
    updateTime      datetime not null comment '最近用户账号修改时间',
    gender          tinyint unsigned not null comment '用户性别'
) comment '用户表';

# alter table users modify birth DATE not null comment '用户出生年月';

# 应聘者表
create table candidates
(
    candId          int unsigned auto_increment primary key comment '应聘者编号',
    realName        varchar(45) not null comment '真实姓名',
    education       tinyint unsigned not null comment '教育程度',
    university      varchar(45) not null comment '毕业院校',
    major           varchar(45) not null comment '专业',
    applyType       tinyint not null comment '应聘类型',
    userAccount     varchar(30) not null comment '关联的用户账号',
    foreign key (userAccount) references users(userAccount) on delete cascade on update cascade
) comment '应聘者表';

# alter table candidates modify delPosition varchar(45) not null comment '投递岗位';
# alter table candidates drop column delPosition;


create table interviewers
(
    interId int unsigned auto_increment primary key comment '面试官编号',
    realName varchar(45) not null comment '真实姓名',
    businessGroup varchar(30) not null comment '事业群',
    department varchar(30) not null comment '部门',
    rankLevel varchar(30) not null comment '职级',
    position varchar(30) not null comment '职位',
    userAccount varchar(30) not null comment '关联的用户账号',
    foreign key (userAccount) references users(userAccount) on delete cascade on update cascade
) comment '面试官表';

# 更新应聘者表或面试官表时同步更新用户表中updateTime字段的trigger
DELIMITER //
create trigger Synchronous_Update_Users_UpdateTime_After_Candidates_Update
    after update on candidates
    for each row
begin
    update users
    set updateTime = NOW()
    where users.userAccount = new.userAccount;
end //
DELIMITER ;

DELIMITER //
create trigger Synchronous_Update_Users_UpdateTime_After_Interviewers_Update
    after update on interviewers
    for each row
begin
    update users
    set updateTime = NOW()
    where users.userAccount = new.userAccount;
end //
DELIMITER ;

# 工作岗位表
create table posts
(
    postId int unsigned auto_increment primary key comment '工作岗位编号',
    postName varchar(45) not null comment '岗位名称',
    postDescription varchar(512) not null comment '岗位描述',
    postRequirement varchar(512) not null comment '岗位要求',
    postNote varchar(512) not null comment '加分项或注意事项',
    postLocation varchar(45) not null comment '工作地点',
    postBusinessGroup varchar(30) not null comment '招聘事业群'
) comment '工作岗位表';