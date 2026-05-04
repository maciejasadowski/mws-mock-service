-- ============================================================================
-- H2 schema simulating the CoCo OUT-database (ModusOne / AUFTRAGSEINGANG).
-- The batch adapter (MessageWorker) inserts processed jobs here.
-- Table name: MessageConstants.TABLE_AUFTRAG = "auftragseingang"
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS COCO_OUT;

CREATE TABLE IF NOT EXISTS COCO_OUT.AUFTRAGSEINGANG (
    AUFTRAGSID                  VARCHAR(40)     NOT NULL,
    AUFTRAGGEBER                VARCHAR(30),
    AUFTRAGGEBERPID             VARCHAR(30),
    AUFTRAGGEBERSACHGEBIET      VARCHAR(30),
    AUFTRAGSSTATUS              CHAR(1)         DEFAULT 'F',
    AUFTRAGSDATUM               TIMESTAMP,
    BUENDEL                     VARCHAR(350),
    KLAMMERID                   VARCHAR(64),
    KLAMMERSEQUENZ              BIGINT,
    KLAMMERBUENDELANZAHL        BIGINT,
    VERSANDWEG                  VARCHAR(30),
    DEZENTRALERDRUCKER          VARCHAR(30),
    DRUCKERGRUPPE               VARCHAR(30),
    PRIORITAET                  BIGINT,
    FUTURAINSTANZ               VARCHAR(5),
    MANDANT                     VARCHAR(20),
    LETZTE_AENDERUNG            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    XMLDOKUMENT                 CLOB,
    DRUCKKURZFASSUNG_FLAG       INTEGER,
    CONSTRAINT PK_AUFTRAGSEINGANG PRIMARY KEY (AUFTRAGSID)
);

CREATE INDEX IF NOT EXISTS IX_ATE_STATUS_MANDANT
    ON COCO_OUT.AUFTRAGSEINGANG (AUFTRAGSSTATUS, MANDANT);
