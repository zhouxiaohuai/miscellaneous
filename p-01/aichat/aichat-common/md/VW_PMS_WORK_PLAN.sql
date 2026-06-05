create view VW_PMS_WORK_PLAN as
select distinct p."WORK_PLAN_ID",p.JOB_MAN,p."IS_PMS",p."IS_PIVOTAL",p."RISK_EVALUATE",p."MAINTAIN_GRADE",p."CWBT_CODE",p."PMS_CODE",p."EQUIP_NAME",p."EQUIP_NAME_EN",p."PLAN_DATE",p."COMPLETE_DATE",p."PRINCIPAL",p."COMPLETE_CONTENT",p."AUDITOR",p."AUDIT_DATE",p."AUDIT_VIEWS",p."ADVERT_MATTER",p."BEGIN_DATE",p."LAST_DATE",p."VESSEL_CODE",p."VESSEL_NAME",p."DEPT",p."DEPT_NAME",p."CYCLE_TYPE",p."STATUS",p."STATUS_NAME",
 p."MAKE_DATE",
p."WORK_CONTENT_ID",p.RUNTIME_AFTER_OVERHAUL , p."CREATED_BY_USER",p."CREATED_OFFICE",p."CREATED_DTM_LOC",p."CREATED_TIME_ZONE",p."UPDATED_BY_USER",p."UPDATED_OFFICE",p."UPDATED_DTM_LOC",p."UPDATED_TIME_ZONE",p."COMPANY_CODE",p."RECORD_VERSION",p."PRINCIPAL_GROUP_CODE",
       (select d.display_value_cn from cdm_codedict d where d.code_type = 'PMS_CYCLE_TYPE' and d.code_value = p.cycle_type) as cycle_type_name,
       decode(p.is_pms,1,'是','否') as is_pms_name,
       decode(p.is_pms,1,'yes','no') as is_pms_name_EN,
       decode(p.is_pivotal,1,'是','否') as is_pivotal_name,
       decode(p.is_pivotal,1,'yes','no') as is_pivotal_name_EN,
       decode(p.risk_evaluate,1,'是','否') as risk_evaluate_name,
       decode(p.risk_evaluate,1,'yes','no') as risk_evaluate_name_EN,
       COMPLETE_PERSON,
       p.ccs_code as ccs_code,
       FN_GET_REAL_PLANCONTENT(p.vessel_equip_id,p.MAINTAIN_GRADES,p."WORK_CONTENT",p.maintain_grade) as WORK_CONTENT
  from (
  select distinct p.work_plan_id,
         (select t.display_value_cn from CDM_CODEDICT t,vop_vessel_equip q,pms_work_content tt where t.CODE_VALUE = q.responsible_person and tt.work_content_id = p.work_content_id and tt.vessel_equip_id=q.vessel_equip_id and   t.CODE_TYPE = 'PMS_VESSEL_JOB' and rownum=1) as JOB_MAN,
         (select q.is_pms from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as is_pms,
         (select q.is_pivotal from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as is_pivotal,
         (select t.risk_evaluate from pms_work_content t where t.work_content_id = p.work_content_id) as risk_evaluate,
         (select t.maintain_grade from pms_work_content t where t.work_content_id = p.work_content_id) as maintain_grade,
         p.cwbt_code,
         (select q.pms_code from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as pms_code,
         --(select c.equip_class_name from vop_equip_class c where c.equip_class_no = p.cwbt_code) as equip_Name,
         (select c.equip_name from vop_vessel_equip c,pms_work_content t where p.work_content_id = t.work_content_id and t.vessel_equip_id = c.vessel_equip_id) as equip_Name,
         (select c.equip_name_en from vop_vessel_equip c,pms_work_content t where p.work_content_id = t.work_content_id and t.vessel_equip_id = c.vessel_equip_id) as equip_Name_EN,
         p.plan_date,p.complete_date,
        -- (select q.responsible_person from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as PRINCIPAL,
         p.PRINCIPAL,--responsiblePerson

          (select
                cdm_emp_info.EMP_NAME
            from
                cdm_emp_info
            where
                (cdm_emp_info.VESSEL_CODE = p.VESSEL_CODE)
                    and (cdm_emp_info.EMP_NO = p.PRINCIPAL)
            and rownum =1 ) AS COMPLETE_PERSON,

         (select t.work_content from pms_work_content t where t.work_content_id = p.work_content_id) as work_content, -- 工作实绩、计划工作内容
         p.complete_content, -- 补充工作内容
         p.auditor,
         /*(select
                nvl(sys_user.USR_NAME,
                            sys_user.USR_NAME_CN)
            from
                sys_user
            where
                (sys_user.USR_CODE = p.AUDITOR)
            and rownum =1 ) AS auditor,*/

         p.audit_date,p.audit_views ||
         (select case when count(1)=0 or p.audit_views is null then '' else chr(13)||chr(10) end ||
                 fn_connstr(ty_utl_table_var2(to_char(r.check_date,
                   'yyyy-mm-dd')||','||trim(r.check_people)||','||r.record,chr(13)||chr(10),'Y',r.check_date))
            from PMS_CHECK_RECORD r where r.work_plan_id = p.work_plan_id) as audit_views,
         (select t.advert_matter from pms_work_content t where t.work_content_id = p.work_content_id) as advert_matter, -- 注意事项
         p.begin_date,p.last_date,
         p.vessel_code,
         (select v.vessel_name from vop_vessel v where v.vessel_code = p.vessel_code) as vessel_name,
         p.dept,
         (select d.display_value_cn from cdm_codedict d where d.code_type = 'PMS_VESSEL_DEPARTMENT' and d.code_value = p.dept) as dept_name,
         (select q.cycle_type from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as cycle_type,
         (select q.ccs_code from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as ccs_code,
         (select q.Maintain_Grade from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as Maintain_GradeS,
         (select q.vessel_equip_id from pms_work_content t,vop_vessel_equip q where t.vessel_equip_id = q.vessel_equip_id and t.work_content_id = p.work_content_id) as vessel_equip_id,
         p.status,
         (select d.display_value_cn from cdm_codedict d where d.code_type = 'PMS_WORK_PLAN_STATUS' and d.code_value = p.dept) as status_name,
         /*(select max(o.make_date) from pms_work_plan_order o where o.work_plan_id = p.work_plan_id) as max_make_date,*/
         -- (select max(o.make_date) from pms_work_plan_order o where o.work_plan_id = p.work_plan_id) as make_date,
         o.make_date,
         p.WORK_CONTENT_ID,
         p.RUNTIME_AFTER_OVERHAUL ,
         p.created_by_user,p.created_office,p.created_dtm_loc,p.created_time_zone,p.updated_by_user,
         p.updated_office,p.updated_dtm_loc,p.updated_time_zone,p.company_code,p.record_version,p.principal_group_code
    from pms_work_plan p inner join pms_work_plan_order o on p.work_plan_id = o.work_plan_id
  ) p
/

