-- auto-generated definition
create table PMS_CHECK_RECORD
(
    CHECK_RECORD_ID      VARCHAR2(50) not null
        constraint PK_PMS_CHECK_RECORD
            primary key,
    WORK_PLAN_ID         VARCHAR2(50)
        constraint REFPMS_WORK_PLAN81
            references PMS_WORK_PLAN,
    VESSEL_CODE          VARCHAR2(50),
    RECORD               VARCHAR2(4000),
    CHECK_DATE           DATE,
    CHECK_PEOPLE         VARCHAR2(200),
    CREATED_BY_USER      VARCHAR2(50),
    CREATED_OFFICE       VARCHAR2(20),
    CREATED_DTM_LOC      DATE,
    CREATED_TIME_ZONE    VARCHAR2(10),
    UPDATED_BY_USER      VARCHAR2(50),
    UPDATED_OFFICE       VARCHAR2(20),
    UPDATED_DTM_LOC      DATE,
    UPDATED_TIME_ZONE    VARCHAR2(10),
    RECORD_VERSION       NUMBER(10)   not null,
    PRINCIPAL_GROUP_CODE VARCHAR2(50) not null,
    IS_DELETE            VARCHAR2(1),
    COMPANY_CODE         VARCHAR2(50) not null,
    CHECK_RECORD_NO      VARCHAR2(50),
    RECTIFY_FLAG         VARCHAR2(50),
    CHECK_FLAG           VARCHAR2(50),
    REMARK               VARCHAR2(4000),
    LOG_SEND_FLAG        VARCHAR2(1),
    CHECK_TYPE           VARCHAR2(10)
)
/

comment on table PMS_CHECK_RECORD is '抽查记录表'
/

comment on column PMS_CHECK_RECORD.CHECK_RECORD_ID is 'Phisical Primary Key'
/

comment on column PMS_CHECK_RECORD.WORK_PLAN_ID is 'hhw audit可为空20190108'
/

comment on column PMS_CHECK_RECORD.VESSEL_CODE is '船舶代码hhw audit不可为空长度改为50 20190108 '
/

comment on column PMS_CHECK_RECORD.RECORD is '抽查人员审评意见 hhw audit 长度改为4000'
/

comment on column PMS_CHECK_RECORD.CHECK_DATE is '抽查日期'
/

comment on column PMS_CHECK_RECORD.CHECK_PEOPLE is '抽查人'
/

comment on column PMS_CHECK_RECORD.CREATED_BY_USER is 'CREATED_BY_USER'
/

comment on column PMS_CHECK_RECORD.CREATED_OFFICE is 'CREATED_OFFICE'
/

comment on column PMS_CHECK_RECORD.CREATED_DTM_LOC is 'CREATED_DTM_LOC'
/

comment on column PMS_CHECK_RECORD.CREATED_TIME_ZONE is 'CREATED_TIME_ZONE'
/

comment on column PMS_CHECK_RECORD.UPDATED_BY_USER is 'UPDATED_BY_USER'
/

comment on column PMS_CHECK_RECORD.UPDATED_OFFICE is 'UPDATED_OFFICE'
/

comment on column PMS_CHECK_RECORD.UPDATED_DTM_LOC is 'UPDATED_DTM_LOC'
/

comment on column PMS_CHECK_RECORD.UPDATED_TIME_ZONE is 'UPDATED_TIME_ZONE'
/

comment on column PMS_CHECK_RECORD.RECORD_VERSION is 'RECORD TIME STAMP'
/

comment on column PMS_CHECK_RECORD.COMPANY_CODE is 'COMPANY_CODE'
/

comment on column PMS_CHECK_RECORD.CHECK_RECORD_NO is '抽查单号V/C+vessel_code+yyyy+xxxx   hhw add可为空20190108'
/

comment on column PMS_CHECK_RECORD.RECTIFY_FLAG is '整改结果PMS_CHECK_RESULT自定义码=2'
/

comment on column PMS_CHECK_RECORD.CHECK_FLAG is '抽查结论PMS_CHECK_RESULT自定义码=1'
/

comment on column PMS_CHECK_RECORD.REMARK is '备注hhw add可为空20190108'
/

comment on column PMS_CHECK_RECORD.LOG_SEND_FLAG is 'hhw add可为空20190108'
/

comment on column PMS_CHECK_RECORD.CHECK_TYPE is 'hhw add 检查类型 未使用'
/

