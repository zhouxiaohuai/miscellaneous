CREATE DEFINER=`root`@`%` FUNCTION `FN_BFM_GET_INVOICE_L`(
    i_QTY DECIMAL(20,4),
    i_DENSITY DECIMAL(20,4),
    i_SUPPLY_MOD VARCHAR(50)
) RETURNS decimal(20,4)
    DETERMINISTIC
BEGIN
    DECLARE result DECIMAL(20,4) DEFAULT 0;

    -- 异常处理：捕获所有 SQL 异常并返回 0
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        RETURN 0;
    END;

    -- 查询计算
    SELECT CASE T.AFTER_UNIT
               WHEN '16' THEN
                   ROUND(i_QTY * IFNULL(T.CONVERSION, 0) / i_DENSITY, 2)
               WHEN '49' THEN
                   ROUND(i_QTY * IFNULL(T.CONVERSION, 0), 2)
               ELSE
                   0
           END AS L
    INTO result
    FROM BFM_PACKAGE_FORMAT T
    WHERE T.PACKAGE_CODE = i_SUPPLY_MOD;

    RETURN result;
END