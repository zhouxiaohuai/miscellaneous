-- auto-generated definition
create table PMS_TIMEGROUP_CONFIGURATION
(
    PMS_TIMEGROUP_CONFIGURATION_ID VARCHAR2(255 char) not null
        primary key,
    COMPANY_CODE                   VARCHAR2(255 char),
    CREATED_BY_USER                VARCHAR2(255 char),
    CREATED_DTM_LOC                TIMESTAMP(6),
    CREATED_OFFICE                 VARCHAR2(255 char),
    CREATED_TIME_ZONE              VARCHAR2(255 char),
    PRINCIPAL_GROUP_CODE           VARCHAR2(255 char),
    RECORD_VERSION                 NUMBER(10),
    UPDATED_BY_USER                VARCHAR2(255 char),
    UPDATED_DTM_LOC                TIMESTAMP(6),
    UPDATED_OFFICE                 VARCHAR2(255 char),
    UPDATED_TIME_ZONE              VARCHAR2(255 char),
    EQUIP_NO                       VARCHAR2(255 char),
    LAST_RUN_TIME_START_DATE       TIMESTAMP(6),
    NOW_RUN_TIME_START_DATE        TIMESTAMP(6),
    READ_WAY                       NUMBER(19, 2),
    REMARK                         VARCHAR2(255 char),
    TIME_GROUP                     VARCHAR2(255 char),
    TIME_GROUP_NO                  VARCHAR2(255 char),
    VESSEL_CODE                    VARCHAR2(255 char),
    VESSEL_EQUIP_ID                VARCHAR2(255 char),
    IS_DELETE                      VARCHAR2(1)
)
/

comment on column PMS_TIMEGROUP_CONFIGURATION.IS_DELETE is '删除状态'
/

