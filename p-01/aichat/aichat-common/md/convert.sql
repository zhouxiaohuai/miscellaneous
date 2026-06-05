-- auto-generated definition
create table PMS_COUNTER_GROUP
(
    COUNTERGROUP_ID      VARCHAR2(50) not null
        constraint PK_PMS_COUNTER_GROUP
            primary key,
    COUNTERGROUP_CODE    VARCHAR2(20) not null,
    VESSEL_CODE          VARCHAR2(50) not null,
    VESSEL_EQUIP_ID      VARCHAR2(50),
    EQUIP_NO             VARCHAR2(50),
    COUNTERGROUP_NAME    VARCHAR2(200),
    LAST_UPDATE          DATE,
    TOTAL_VALUE          NUMBER(12, 2),
    CREATED_BY_USER      VARCHAR2(50),
    CREATED_OFFICE       VARCHAR2(20),
    CREATED_DTM_LOC      DATE,
    CREATED_TIME_ZONE    VARCHAR2(10),
    UPDATED_BY_USER      VARCHAR2(50),
    UPDATED_OFFICE       VARCHAR2(20),
    UPDATED_DTM_LOC      DATE,
    UPDATED_TIME_ZONE    VARCHAR2(10),
    RECORD_VERSION       NUMBER(10),
    PRINCIPAL_GROUP_CODE VARCHAR2(50),
    COMPANY_CODE         VARCHAR2(50),
    IS_DELETE            VARCHAR2(1),
    REMARK               VARCHAR2(200),
    LAST_NUM             NUMBER(10, 2),
    COUNTERGROUP_NAME_EN VARCHAR2(200)
)
/

comment on table PMS_COUNTER_GROUP is '存储设备计时器组
对应到航标系统中就是定时组号'
/

comment on column PMS_COUNTER_GROUP.COUNTERGROUP_ID is '定时组号ID'
/

comment on column PMS_COUNTER_GROUP.COUNTERGROUP_CODE is '定时组号编号,自动生成:船舶ID+D+xxx'
/

comment on column PMS_COUNTER_GROUP.VESSEL_CODE is '船舶ID'
/

comment on column PMS_COUNTER_GROUP.VESSEL_EQUIP_ID is '设备ID'
/

comment on column PMS_COUNTER_GROUP.EQUIP_NO is 'cwbt码'
/

comment on column PMS_COUNTER_GROUP.COUNTERGROUP_NAME is '名称'
/

comment on column PMS_COUNTER_GROUP.LAST_UPDATE is '最近一次更新日期'
/

comment on column PMS_COUNTER_GROUP.TOTAL_VALUE is '总运行时间VOP_VESSEL_EQUIP .totalRuntime
涉及单独设备计时器的数据
不能更新到这里，这里只更
新定时组号的数据。例如主
机、副机等
'
/

comment on column PMS_COUNTER_GROUP.CREATED_BY_USER is 'CREATED_BY_USER'
/

comment on column PMS_COUNTER_GROUP.CREATED_OFFICE is 'CREATED_OFFICE'
/

comment on column PMS_COUNTER_GROUP.CREATED_DTM_LOC is 'CREATED_DTM_LOC'
/

comment on column PMS_COUNTER_GROUP.CREATED_TIME_ZONE is 'CREATED_TIME_ZONE'
/

comment on column PMS_COUNTER_GROUP.UPDATED_BY_USER is 'UPDATED_BY_USER'
/

comment on column PMS_COUNTER_GROUP.UPDATED_OFFICE is 'UPDATED_OFFICE'
/

comment on column PMS_COUNTER_GROUP.UPDATED_DTM_LOC is 'UPDATED_DTM_LOC'
/

comment on column PMS_COUNTER_GROUP.UPDATED_TIME_ZONE is 'UPDATED_TIME_ZONE'
/

comment on column PMS_COUNTER_GROUP.RECORD_VERSION is 'RECORD_VERSION'
/

comment on column PMS_COUNTER_GROUP.PRINCIPAL_GROUP_CODE is 'PRINCIPAL_GROUP_CODE'
/

comment on column PMS_COUNTER_GROUP.COMPANY_CODE is 'COMPANY_CODE'
/

comment on column PMS_COUNTER_GROUP.IS_DELETE is '删除标志'
/

comment on column PMS_COUNTER_GROUP.REMARK is '备注'
/

comment on column PMS_COUNTER_GROUP.LAST_NUM is '上次读数值'
/

comment on column PMS_COUNTER_GROUP.COUNTERGROUP_NAME_EN is '英文名称'
/

