-- auto-generated definition
create table PMS_WORK_PLAN_ORDER
(
    WORK_PLAN_ORDER_ID   VARCHAR2(50) not null
        constraint PK_PMS_WORK_PLAN_1
            primary key,
    WORK_PLAN_ID         VARCHAR2(50)
        constraint REFPMS_WORK_PLAN86
            references PMS_WORK_PLAN,
    VESSEL_CODE          VARCHAR2(10) not null,
    MAKE_DATE            DATE,
    ORDER_TYPE           CHAR         not null,
    COMPANY_CODE         VARCHAR2(50),
    CREATED_BY_USER      VARCHAR2(50),
    CREATED_OFFICE       VARCHAR2(20),
    CREATED_DTM_LOC      DATE,
    CREATED_TIME_ZONE    VARCHAR2(10),
    UPDATED_BY_USER      VARCHAR2(50),
    UPDATED_OFFICE       VARCHAR2(20),
    UPDATED_DTM_LOC      DATE,
    UPDATED_TIME_ZONE    VARCHAR2(10),
    RECORD_VERSION       NUMBER(10)   not null,
    PRINCIPAL_GROUP_CODE VARCHAR2(50) not null
)
/

comment on table PMS_WORK_PLAN_ORDER is '工作指令表'
/

comment on column PMS_WORK_PLAN_ORDER.WORK_PLAN_ORDER_ID is 'Phisical Primary Key'
/

comment on column PMS_WORK_PLAN_ORDER.WORK_PLAN_ID is 'Phisical Primary Key'
/

comment on column PMS_WORK_PLAN_ORDER.VESSEL_CODE is '船舶代码'
/

comment on column PMS_WORK_PLAN_ORDER.MAKE_DATE is '生成日期'
/

comment on column PMS_WORK_PLAN_ORDER.ORDER_TYPE is '指令类型'
/

comment on column PMS_WORK_PLAN_ORDER.COMPANY_CODE is '公司代码'
/

comment on column PMS_WORK_PLAN_ORDER.CREATED_BY_USER is 'CREATED_BY_USER'
/

comment on column PMS_WORK_PLAN_ORDER.CREATED_OFFICE is 'CREATED_OFFICE'
/

comment on column PMS_WORK_PLAN_ORDER.CREATED_DTM_LOC is 'CREATED_DTM_LOC'
/

comment on column PMS_WORK_PLAN_ORDER.CREATED_TIME_ZONE is 'CREATED_TIME_ZONE'
/

comment on column PMS_WORK_PLAN_ORDER.UPDATED_BY_USER is 'UPDATED_BY_USER'
/

comment on column PMS_WORK_PLAN_ORDER.UPDATED_OFFICE is 'UPDATED_OFFICE'
/

comment on column PMS_WORK_PLAN_ORDER.UPDATED_DTM_LOC is 'UPDATED_DTM_LOC'
/

comment on column PMS_WORK_PLAN_ORDER.UPDATED_TIME_ZONE is 'UPDATED_TIME_ZONE'
/

comment on column PMS_WORK_PLAN_ORDER.RECORD_VERSION is 'RECORD TIME STAMP'
/

create index INDEX_VESSEL_CODE
    on PMS_WORK_PLAN_ORDER (VESSEL_CODE, MAKE_DATE)
/

create index IDX_ORDER_PID_IDXPMS_WORK_PLAN
    on PMS_WORK_PLAN_ORDER (WORK_PLAN_ID)
/

create index INDEX_ORDER_VCODE_MDATE
    on PMS_WORK_PLAN_ORDER ('VESSEL_CODE', 'MAKE_DATE')
/

create index ORDER_PID_IDX
    on PMS_WORK_PLAN_ORDER ('WORK_PLAN_ID')
/

