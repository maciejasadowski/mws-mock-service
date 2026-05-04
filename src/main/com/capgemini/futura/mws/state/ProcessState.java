package com.capgemini.futura.mws.state;

/**
 * Represents the internal mock state of a single MWS process.
 */
public class ProcessState {

    public enum DocumentStatus {
        NOT_STARTED, IN_PROGRESS, DONE
    }

    public enum PrintArchiveStatus {
        NOT_STARTED, READY, IN_PROGRESS, DONE
    }

    public enum CloseStatus {
        OPEN, SAVED, COMPLETED_OK, COMPLETED_ERROR, DELETED
    }

    private final String processId;
    private DocumentStatus documentStatus = DocumentStatus.NOT_STARTED;
    private PrintArchiveStatus printArchiveStatus = PrintArchiveStatus.NOT_STARTED;
    private CloseStatus closeStatus = CloseStatus.OPEN;

    public ProcessState(String processId) {
        this.processId = processId;
    }

    public String getProcessId() { return processId; }

    public DocumentStatus getDocumentStatus() { return documentStatus; }
    public void setDocumentStatus(DocumentStatus documentStatus) { this.documentStatus = documentStatus; }

    public PrintArchiveStatus getPrintArchiveStatus() { return printArchiveStatus; }
    public void setPrintArchiveStatus(PrintArchiveStatus printArchiveStatus) { this.printArchiveStatus = printArchiveStatus; }

    public CloseStatus getCloseStatus() { return closeStatus; }
    public void setCloseStatus(CloseStatus closeStatus) { this.closeStatus = closeStatus; }

    @Override
    public String toString() {
        return "ProcessState{processId='" + processId + "', docStatus=" + documentStatus
                + ", printStatus=" + printArchiveStatus + ", closeStatus=" + closeStatus + "}";
    }
}

