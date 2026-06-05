SELECT DISTINCT
  p.`WORK_PLAN_ID`,
  p.JOB_MAN,
  p.`IS_PMS`,
  p.`IS_PIVOTAL`,
  p.`RISK_EVALUATE`,
  p.`MAINTAIN_GRADE`,
  p.`CWBT_CODE`,
  p.`PMS_CODE`,
  p.`EQUIP_NAME`,
  p.`EQUIP_NAME_EN`,
  p.`PLAN_DATE`,
  p.`COMPLETE_DATE`,
  p.`PRINCIPAL`,
  p.`COMPLETE_CONTENT`,
  p.`AUDITOR`,
  p.`AUDIT_DATE`,
  p.`AUDIT_VIEWS`,
  p.`ADVERT_MATTER`,
  p.`BEGIN_DATE`,
  p.`LAST_DATE`,
  p.`VESSEL_CODE`,
  p.`VESSEL_NAME`,
  p.`DEPT`,
  p.`DEPT_NAME`,
  p.`CYCLE_TYPE`,
  p.`STATUS`,
  p.`STATUS_NAME`,
  p.`MAKE_DATE`,
  p.`WORK_CONTENT_ID`,
  p.RUNTIME_AFTER_OVERHAUL,
  p.`CREATED_BY_USER`,
  p.`CREATED_OFFICE`,
  p.`CREATED_DTM_LOC`,
  p.`CREATED_TIME_ZONE`,
  p.`UPDATED_BY_USER`,
  p.`UPDATED_OFFICE`,
  p.`UPDATED_DTM_LOC`,
  p.`UPDATED_TIME_ZONE`,
  p.`COMPANY_CODE`,
  p.`RECORD_VERSION`,
  p.`PRINCIPAL_GROUP_CODE`,
  (SELECT d.display_value_cn FROM cdm_codedict d WHERE d.code_type = 'PMS_CYCLE_TYPE' AND d.code_value = p.cycle_type) AS cycle_type_name,
  CASE
    WHEN p.is_pms = 1 THEN
      '是'
    ELSE
      '否'
  END AS is_pms_name,
  CASE
    WHEN p.is_pms = 1 THEN
      'yes'
    ELSE
      'no'
  END AS is_pms_name_EN,
  CASE
    WHEN p.is_pivotal = 1 THEN
      '是'
    ELSE
      '否'
  END AS is_pivotal_name,
  CASE
    WHEN p.is_pivotal = 1 THEN
      'yes'
    ELSE
      'no'
  END AS is_pivotal_name_EN,
  CASE
    WHEN p.risk_evaluate = 1 THEN
      '是'
    ELSE
      '否'
  END AS risk_evaluate_name,
  CASE
    WHEN p.risk_evaluate = 1 THEN
      'yes'
    ELSE
      'no'
  END AS risk_evaluate_name_EN,
  COMPLETE_PERSON,
  p.ccs_code AS ccs_code,
  FN_GET_REAL_PLANCONTENT (p.vessel_equip_id, p.MAINTAIN_GRADES, p.`WORK_CONTENT`, p.maintain_grade) AS WORK_CONTENT
FROM
  (
    SELECT DISTINCT
      p.work_plan_id,
      (
        SELECT
          t.display_value_cn
        FROM
          CDM_CODEDICT t,
          vop_vessel_equip q,
          pms_work_content tt
        WHERE
          t.CODE_VALUE = q.responsible_person
          AND tt.work_content_id = p.work_content_id
          AND tt.vessel_equip_id = q.vessel_equip_id
          AND t.CODE_TYPE = 'PMS_VESSEL_JOB'
          LIMIT 1
      ) AS JOB_MAN,
      (SELECT q.is_pms FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS is_pms,
      (SELECT q.is_pivotal FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS is_pivotal,
      (SELECT t.risk_evaluate FROM pms_work_content t WHERE t.work_content_id = p.work_content_id) AS risk_evaluate,
      (SELECT t.maintain_grade FROM pms_work_content t WHERE t.work_content_id = p.work_content_id) AS maintain_grade,
      p.cwbt_code,
      (SELECT q.pms_code FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS pms_code,
      (SELECT c.equip_name FROM vop_vessel_equip c, pms_work_content t WHERE p.work_content_id = t.work_content_id AND t.vessel_equip_id = c.vessel_equip_id) AS equip_Name,
      (SELECT c.equip_name_en FROM vop_vessel_equip c, pms_work_content t WHERE p.work_content_id = t.work_content_id AND t.vessel_equip_id = c.vessel_equip_id) AS equip_Name_EN,
      p.plan_date,
      p.complete_date,
      p.PRINCIPAL,
      (
        SELECT
          cdm_emp_info.EMP_NAME
        FROM
          cdm_emp_info
        WHERE
          (cdm_emp_info.VESSEL_CODE = p.VESSEL_CODE)
          AND (cdm_emp_info.EMP_NO = p.PRINCIPAL)
          LIMIT 1
      ) AS COMPLETE_PERSON,
      (SELECT t.work_content FROM pms_work_content t WHERE t.work_content_id = p.work_content_id) AS work_content,
      p.complete_content,
      p.auditor,
      p.audit_date,
      p.audit_views || (
        SELECT
          CASE
            WHEN
              count(1) = 0
              OR p.audit_views IS NULL THEN
              ''
            ELSE
              '\r\n'
          END || group_concat(
            concat(date_format(r.check_date, '%Y-%m-%d'), ',', trim(r.check_people), ',', r.record) SEPARATOR '\r\n'
          )
        FROM
          PMS_CHECK_RECORD r
        WHERE
          r.work_plan_id = p.work_plan_id
      ) AS audit_views,
      (SELECT t.advert_matter FROM pms_work_content t WHERE t.work_content_id = p.work_content_id) AS advert_matter,
      p.begin_date,
      p.last_date,
      p.vessel_code,
      (SELECT v.vessel_name FROM vop_vessel v WHERE v.vessel_code = p.vessel_code) AS vessel_name,
      p.dept,
      (SELECT d.display_value_cn FROM cdm_codedict d WHERE d.code_type = 'PMS_VESSEL_DEPARTMENT' AND d.code_value = p.dept) AS dept_name,
      (SELECT q.cycle_type FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS cycle_type,
      (SELECT q.ccs_code FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS ccs_code,
      (SELECT q.Maintain_Grade FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS Maintain_GradeS,
      (SELECT q.vessel_equip_id FROM pms_work_content t, vop_vessel_equip q WHERE t.vessel_equip_id = q.vessel_equip_id AND t.work_content_id = p.work_content_id) AS vessel_equip_id,
      p.STATUS,
      (SELECT d.display_value_cn FROM cdm_codedict d WHERE d.code_type = 'PMS_WORK_PLAN_STATUS' AND d.code_value = p.dept) AS status_name,
      o.make_date,
      p.WORK_CONTENT_ID,
      p.RUNTIME_AFTER_OVERHAUL,
      p.created_by_user,
      p.created_office,
      p.created_dtm_loc,
      p.created_time_zone,
      p.updated_by_user,
      p.updated_office,
      p.updated_dtm_loc,
      p.updated_time_zone,
      p.company_code,
      p.record_version,
      p.principal_group_code
    FROM
      pms_work_plan p
      INNER JOIN pms_work_plan_order o ON p.work_plan_id = o.work_plan_id
  ) p