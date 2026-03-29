package com.shipway.ordertracking.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code awb} from the {@code labels} table.
 * Expects columns: {@code id}, {@code account_code}, {@code order_id}, {@code awb}.
 */
@Repository
public class LabelAwbRepository {

    private static final int IN_CHUNK = 500;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<String> findLatestAwb(String accountCode, String normalizedOrderIdNoHash) {
        if (accountCode == null || accountCode.isBlank() || normalizedOrderIdNoHash == null
                || normalizedOrderIdNoHash.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT l.awb FROM `labels` l
                WHERE UPPER(TRIM(l.account_code)) = UPPER(TRIM(?))
                AND REPLACE(TRIM(l.order_id), '#', '') = ?
                ORDER BY l.id DESC
                LIMIT 1
                """;
        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), accountCode.trim(),
                normalizedOrderIdNoHash.trim());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(0));
    }

    /**
     * Latest {@code awb} per normalized order id for one account (by max {@code id}).
     */
    public Map<String, String> findLatestAwbsForAccountAndNormalizedOrderIds(String accountCode,
            List<String> normalizedOrderIdsNoHash) {
        Map<String, String> out = new LinkedHashMap<>();
        if (accountCode == null || accountCode.isBlank() || normalizedOrderIdsNoHash == null
                || normalizedOrderIdsNoHash.isEmpty()) {
            return out;
        }
        String acct = accountCode.trim();
        for (int i = 0; i < normalizedOrderIdsNoHash.size(); i += IN_CHUNK) {
            int end = Math.min(i + IN_CHUNK, normalizedOrderIdsNoHash.size());
            List<String> chunk = normalizedOrderIdsNoHash.subList(i, end);
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            String sql = """
                    SELECT REPLACE(TRIM(l.order_id), '#', '') AS norm_key, l.awb
                    FROM `labels` l
                    INNER JOIN (
                      SELECT REPLACE(TRIM(order_id), '#', '') AS norm_oid, MAX(id) AS mid
                      FROM `labels`
                      WHERE UPPER(TRIM(account_code)) = UPPER(TRIM(?))
                      AND REPLACE(TRIM(order_id), '#', '') IN (%s)
                      GROUP BY REPLACE(TRIM(order_id), '#', '')
                    ) z ON REPLACE(TRIM(l.order_id), '#', '') = z.norm_oid AND l.id = z.mid
                    """.formatted(placeholders);
            List<Object> args = new ArrayList<>();
            args.add(acct);
            args.addAll(chunk);
            jdbcTemplate.query(sql, rs -> {
                String k = rs.getString(1);
                String awb = rs.getString(2);
                if (k != null && awb != null && !awb.isBlank()) {
                    out.put(k.trim(), awb.trim());
                }
            }, args.toArray());
        }
        return out;
    }
}
