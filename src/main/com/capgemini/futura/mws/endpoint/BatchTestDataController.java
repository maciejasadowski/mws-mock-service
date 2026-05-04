package com.capgemini.futura.mws.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API for inspecting and resetting the mocked CoCo OUT-database (AUFTRAGSEINGANG).
 *
 * The batch adapter writes here after processing records from PLF_BATCHMESSAGEOBJECTS.
 * Use these endpoints to verify that jobs were correctly transferred to CoCo.
 *
 * Endpoints:
 *   GET    /batch/auftraege              - list all rows in AUFTRAGSEINGANG
 *   GET    /batch/auftraege/{auftragsid} - get single row including XMLDOKUMENT
 *   DELETE /batch/auftraege              - reset table between test runs
 *   GET    /batch/status                 - row count summary
 */
@RestController
@RequestMapping("/batch")
public class BatchTestDataController {

    private static final Logger logger = LoggerFactory.getLogger(BatchTestDataController.class);

    private final JdbcTemplate coco;

    public BatchTestDataController(@Qualifier("cocoJdbcTemplate") JdbcTemplate coco) {
        this.coco = coco;
    }

    /**
     * List all orders that the batch adapter has written to CoCo (latest first).
     */
    @GetMapping("/auftraege")
    public List<Map<String, Object>> listAuftraege(
            @RequestParam(defaultValue = "200") int limit) {
        return coco.queryForList(
            "SELECT AUFTRAGSID, AUFTRAGSSTATUS, AUFTRAGSDATUM, MANDANT, " +
            "       FUTURAINSTANZ, LETZTE_AENDERUNG, AUFTRAGGEBER, BUENDEL " +
            "FROM COCO_OUT.AUFTRAGSEINGANG " +
            "ORDER BY LETZTE_AENDERUNG DESC FETCH FIRST ? ROWS ONLY", limit);
    }

    /**
     * Get a single order by AUFTRAGSID (including full XMLDOKUMENT).
     */
    @GetMapping("/auftraege/{auftragsid}")
    public Map<String, Object> getAuftrag(@PathVariable String auftragsid) {
        List<Map<String, Object>> rows = coco.queryForList(
            "SELECT * FROM COCO_OUT.AUFTRAGSEINGANG WHERE AUFTRAGSID = ?", auftragsid);
        if (rows.isEmpty()) {
            return Map.of("error", "not found", "auftragsid", auftragsid);
        }
        return rows.get(0);
    }

    /**
     * Delete all rows from AUFTRAGSEINGANG (reset between test runs).
     */
    @DeleteMapping("/auftraege")
    public Map<String, Object> clearAuftraege() {
        int deleted = coco.update("DELETE FROM COCO_OUT.AUFTRAGSEINGANG");
        logger.info("CoCo mock: cleared {} rows from AUFTRAGSEINGANG", deleted);
        return Map.of("deleted", deleted);
    }

    /**
     * Row count summary broken down by AUFTRAGSSTATUS.
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        long total = count("SELECT COUNT(*) FROM COCO_OUT.AUFTRAGSEINGANG");
        long frei  = count("SELECT COUNT(*) FROM COCO_OUT.AUFTRAGSEINGANG WHERE AUFTRAGSSTATUS = 'F'");
        return Map.of("total", total, "status_F_frei", frei);
    }

    private long count(String sql) {
        return Objects.requireNonNullElse(coco.queryForObject(sql, Long.class), 0L);
    }
}
