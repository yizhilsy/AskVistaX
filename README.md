## 问见未来 · 视听多模态AI面试系统解决方案

### AskVistaX · Video and Audio MultiModal AI Interview System Solution for the 19th Challenge Cup

[TOC]

### 后端设计

#### 数据库设计

##### 用户表 users

| <u>uid</u> | ==userAccount== | passwordHash | userName       | *roleType*     | avatar     |
| ---------- | --------------- | ------------ | -------------- | -------------- | ---------- |
| 用户标号   | 用户账号        | 哈希后的密码 | 用户名         | 身份类型       | 头像url    |
| **birth**  | **phone**       | **email**    | **createTime** | **updateTime** | **gender** |
| 出生年月   | 联系电话        | 用户邮箱     | 创建时间       | 修改时间       | 性别       |

***roleType*** 身份类型：其中0为系统管理员，1为面试官，2为应聘者



##### 应聘者表 candidates

| candId   | realName | education | university | major | applyType | deliPosition |
| -------- | -------- | --------- | ---------- | ----- | --------- | ------------ |
| 应聘编号 | 真实姓名 | 教育程度  | 毕业院校   | 专业  | 应聘类型  | 投递岗位     |



##### 面试官表 interviewers

| interId    | businessGroup | department | rank | position |
| ---------- | ------------- | ---------- | ---- | -------- |
| 面试官编号 | 事业群        | 部门       | 职级 | 职位     |





### 算法设计

### 前端设计





