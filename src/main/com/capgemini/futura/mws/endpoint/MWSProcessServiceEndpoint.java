package com.capgemini.futura.mws.endpoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import com.capgemini.futura.mws.generated.*;
import com.capgemini.futura.mws.state.MockStateStore;
import com.capgemini.futura.mws.state.ProcessState;

@Endpoint
public class MWSProcessServiceEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(MWSProcessServiceEndpoint.class);
    private static final String NAMESPACE_URI = "http://tempuri.org/";

    // closeProcess status constants - from ComposerWsRepository javadoc:
    // 0 = Vorgang speichern (can be reopened)
    // 1 = in Bearbeitung (used internally, should not be passed)
    // 2 = erfolgreich abgeschlossen (MWS_Assistant auto-deletes these)
    // 3 = fehlerhaft beendet (not auto-deleted)
    // other = Vorgang beendet
    private static final int STATUS_SPEICHERN = 0;
    private static final int STATUS_ERFOLGREICH_ABGESCHLOSSEN = 2;
    private static final int STATUS_FEHLERHAFT_BEENDET = 3;

    // Process_Start / Process_GetInfo constants (IComposerWsConstants)
    // CMD_CREATE_DOCUMENTS = 2, CMD_PRINT_AND_ARCHIVE = 3 (sent as numeric strings by ComposerProcess)
    // INFO_CREATE_DOCUMENTS = 2, INFO_PRINT_AND_ARCHIVE = 3
    private static final String CMD_ID_CREATE_DOCUMENTS  = "2";
    private static final String CMD_ID_PRINT_AND_ARCHIVE = "3";
    private static final String CMD_NAME_CREATE_DOCUMENTS  = "CREATE_DOCUMENTS";
    private static final String CMD_NAME_PRINT_AND_ARCHIVE = "PRINT_AND_ARCHIVE";

    @Value("${coco.username}")
    private String expectedUsername;

    @Value("${coco.password}")
    private String expectedPassword;

    @Autowired
    private MockStateStore stateStore;

    // ============ Authentication Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Login")
    @ResponsePayload
    public LoginResponse login(@RequestPayload Login request) {
        String userName = request.getUserName() != null ? request.getUserName().getValue() : null;
        String password = request.getPassword() != null ? request.getPassword().getValue() : null;

        logger.info("Login request received for user: {}", userName);

        ObjectFactory of = new ObjectFactory();
        LoginResponse response = new LoginResponse();

        if (expectedUsername.equalsIgnoreCase(userName) && expectedPassword.equals(password)) {
            String sessionId = UUID.randomUUID().toString();
            String xmlResult = loadLoginSuccessXml(userName);

            response.setLoginResult(0);
            response.setSessionID(of.createLoginResponseSessionID(sessionId));
            response.setXmlResult(of.createLoginResponseXmlResult(xmlResult));

            logger.info("Login successful for user: {}, sessionID: {}", userName, sessionId);
        } else {
            response.setLoginResult(-1);
            response.setSessionID(of.createLoginResponseSessionID(""));
            response.setXmlResult(of.createLoginResponseXmlResult(""));

            logger.warn("Login failed for user: {}", userName);
        }

        return response;
    }

    private String loadLoginSuccessXml(String userId) {
        try (InputStream is = new ClassPathResource("mock-responses/login-success.xml").getInputStream()) {
            byte[] bytes = is.readAllBytes();
            // Strip UTF-16 BOM (FE FF or FF FE) if present, then decode properly
            String template;
            if (bytes.length >= 2 && bytes[0] == (byte)0xFE && bytes[1] == (byte)0xFF) {
                template = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            } else if (bytes.length >= 2 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE) {
                template = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            } else {
                template = new String(bytes, StandardCharsets.UTF_8);
            }
            // Remove null bytes that may appear from encoding issues
            template = template.replace("\u0000", "");
            return template.replace("${userId}", userId != null ? userId : "unknown");
        } catch (IOException e) {
            logger.error("Failed to load login-success.xml template", e);
            return "<error>Failed to load response template</error>";
        }
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Logout")
    @ResponsePayload
    public LogoutResponse logout(@RequestPayload Logout request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        logger.info("Logout request received for sessionId: {}", sessionId);

        ObjectFactory of = new ObjectFactory();
        LogoutResponse response = new LogoutResponse();
        response.setLogoutResult(0);
        response.setXmlResult(of.createLogoutResponseXmlResult(""));

        logger.info("Logout successful for sessionId: {}", sessionId);
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Ping")
    @ResponsePayload
    public PingResponse ping(@RequestPayload Ping request) {
        logger.info("Ping request received");

        ObjectFactory of = new ObjectFactory(); // Ensure ObjectFactory is initialized

        PingResponse response = of.createPingResponse();
        response.setPingResult(of.createPingResponsePingResult("PONG")); // Example for PingResponse, adjust as needed

        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "ClearCache")
    @ResponsePayload
    public Object clearCache(@RequestPayload Object request) {
        logger.info("ClearCache request received");
        return createMockResponse("ClearCacheResponse", true);
    }

    // ============ Process Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_Create")
    @ResponsePayload
    public ProcessCreateResponse processCreate(@RequestPayload ProcessCreate request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;
        String mType    = request.getMType()    != null ? request.getMType().getValue()    : null;
        String mTitle   = request.getMTitle()   != null ? request.getMTitle().getValue()   : null;

        // Use provided processId (auftragsId) or generate new one
        String newProcessId = (processId != null && !processId.isEmpty()) ? processId : UUID.randomUUID().toString();

        ProcessState state = stateStore.createProcess(newProcessId);
        // After create, documents are not yet started; print/archive not started
        state.setDocumentStatus(ProcessState.DocumentStatus.NOT_STARTED);
        state.setPrintArchiveStatus(ProcessState.PrintArchiveStatus.NOT_STARTED);

        logger.info("Process_Create: sessionId={}, auftragsId/processId={}, mType={}, mTitle={}",
                sessionId, newProcessId, mType, mTitle);

        ObjectFactory of = new ObjectFactory();
        ProcessCreateResponse response = of.createProcessCreateResponse();
        response.setProcessCreateResult(0);
        response.setProcessId(of.createProcessCreateResponseProcessId(newProcessId));
        response.setXmlResult(of.createProcessCreateResponseXmlResult(""));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_Start")
    @ResponsePayload
    public ProcessStartResponse processStart(@RequestPayload ProcessStart request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;
        String command   = request.getCommand()   != null ? request.getCommand().getValue()   : null;

        logger.info("Process_Start: sessionId={}, processId={}, command={}", sessionId, processId, command);

        ProcessState state = stateStore.getOrCreate(processId);

        if (isCreateDocuments(command)) {
            state.setDocumentStatus(ProcessState.DocumentStatus.DONE);
            state.setPrintArchiveStatus(ProcessState.PrintArchiveStatus.READY);
            logger.info("Process_Start CREATE_DOCUMENTS({}): processId={} -> docs=DONE, print=READY(1)", command, processId);

        } else if (isPrintAndArchive(command)) {
            state.setPrintArchiveStatus(ProcessState.PrintArchiveStatus.DONE);
            logger.info("Process_Start PRINT_AND_ARCHIVE({}): processId={} -> print=DONE(3)", command, processId);

        } else {
            // Known ignored commands: SET_PARAMETERS (0), SELECT_DATA (0), COMPLETE_STRUCTURE (1)
            // These set pool variables or trigger data selection - mock accepts and ignores them
            logger.info("Process_Start: command='{}' accepted (no state change) for processId={}", command, processId);
        }

        ObjectFactory of = new ObjectFactory();
        ProcessStartResponse response = of.createProcessStartResponse();
        response.setProcessStartResult(0);
        response.setXmlResult(of.createProcessStartResponseXmlResult(""));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_GetInfo")
    @ResponsePayload
    public ProcessGetInfoResponse processGetInfo(@RequestPayload ProcessGetInfo request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;
        String infoType  = request.getInfoType()  != null ? request.getInfoType().getValue()  : null;

        logger.info("Process_GetInfo: sessionId={}, processId={}, infoType={}", sessionId, processId, infoType);

        // Simulate transient FKAT_RESOURCENFEHLER_MWS to exercise retry logic
        if (stateStore.shouldSimulateError(processId)) {
            logger.warn("Process_GetInfo: simulating transient resource error for processId={}", processId);
            ObjectFactory of = new ObjectFactory();
            ProcessGetInfoResponse errResp = of.createProcessGetInfoResponse();
            // Result code -1 = error; adapter should retry up to 3 times
            errResp.setProcessGetInfoResult(-1);
            errResp.setXmlResult(of.createProcessGetInfoResponseXmlResult(
                    buildErrorXml(processId, "FKAT_RESOURCENFEHLER_MWS", "Simulated transient resource error")));
            return errResp;
        }

        ProcessState state = stateStore.getOrCreate(processId);
        String xmlResult = buildProcessInfoXml(infoType, processId, state);

        ObjectFactory of = new ObjectFactory();
        ProcessGetInfoResponse response = of.createProcessGetInfoResponse();
        response.setProcessGetInfoResult(0);
        response.setXmlResult(of.createProcessGetInfoResponseXmlResult(xmlResult));
        return response;
    }

    /**
     * Builds the PROCESSINFO XML based on actual mock state.
     *
     * Constants from IComposerWsConstants:
     *   ATTR_ITEMNAME   = "name"
     *   ATTR_OBJSTATE   = "state"
     *   ELEMENT_MWSACTION = "mwsaction"
     *   VALUE_INFO_CREATEDOCUMENTS = "CREATEDOCUMENTS"
     *   VALUE_INFO_PRINTANDARCHIVE = "PRINTANDARCHIVE"
     *   PRINT_DOCUMENTS_SERVER_STATE_OK = "3"
     *
     * checkXml() looks for: <mwsaction name="CREATEDOCUMENTS" state="3" />
     * getProcessState() looks for: <mwsaction name="CREATEDOCUMENTS|PRINTANDARCHIVE" state="N" />
     */
    private String buildProcessInfoXml(String infoType, String processId, ProcessState state) {

        if (infoType != null && infoType.toUpperCase().contains("PROCESSINFO")) {
            logger.info("Process_GetInfo PROCESSINFO: processId={} -> returning full process info", processId);
            return buildFullProcessInfoXml(processId, state);
        }

        if (isCreateDocuments(infoType)) {
            int stateVal = (state.getDocumentStatus() == ProcessState.DocumentStatus.DONE) ? 3 : 1;
            logger.info("Process_GetInfo CREATE_DOCUMENTS({}): processId={}, docStatus={} -> state={}", infoType, processId, state.getDocumentStatus(), stateVal);
            return buildMwsActionXml(processId, "CREATEDOCUMENTS", stateVal);
        }

        if (isPrintAndArchive(infoType)) {
            int stateVal;
            switch (state.getPrintArchiveStatus()) {
                case DONE:        stateVal = 3; break; // STATE_DONE  = 3
                case READY:       stateVal = 1; break; // STATE_READY = 1
                case IN_PROGRESS: stateVal = 2; break; // STATE_BUSY  = 2
                default:          stateVal = 0; break; // STATE_NONE  = 0
            }
            logger.info("Process_GetInfo PRINT_AND_ARCHIVE({}): processId={}, printStatus={} -> state={}", infoType, processId, state.getPrintArchiveStatus(), stateVal);
            return buildMwsActionXml(processId, "PRINTANDARCHIVE", stateVal);
        }

        logger.warn("Process_GetInfo: unknown infoType '{}', returning generic OK", infoType);
        return buildFullProcessInfoXml(processId, state);
    }

    /**
     * Builds XML with single mwsaction element.
     * checkXml() uses StAX parser looking for: element.getAttributeByName(new QName("name")) = "CREATEDOCUMENTS"
     *                                      and: element.getAttributeByName(new QName("state")) = "3"
     */
    private String buildMwsActionXml(String processId, String itemName, int stateVal) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
               "<mws type=\"PROCESSINFO\" version=\"2.0\">" +
               "<process id=\"" + processId + "\">" +
               "<mwsaction name=\"" + itemName + "\" state=\"" + stateVal + "\" />" +
               "</process>" +
               "</mws>";
    }

    private String buildFullProcessInfoXml(String processId, ProcessState state) {
        int docState = (state.getDocumentStatus() == ProcessState.DocumentStatus.DONE) ? 3 : 1;
        int printState;
        switch (state.getPrintArchiveStatus()) {
            case DONE:        printState = 3; break; // STATE_DONE  = 3
            case READY:       printState = 1; break; // STATE_READY = 1 - ready to print
            case IN_PROGRESS: printState = 2; break; // STATE_BUSY  = 2
            default:          printState = 0; break; // STATE_NONE  = 0
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
               "<mws type=\"PROCESSINFO\" version=\"2.0\">" +
               "<process id=\"" + processId + "\">" +
               "<mwsaction name=\"CREATEDOCUMENTS\" state=\"" + docState   + "\" />" +
               "<mwsaction name=\"PRINTANDARCHIVE\" state=\"" + printState + "\" />" +
               "</process>" +
               "</mws>";
    }

    private String buildErrorXml(String processId, String errorCode, String errorMessage) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
               "<mws type=\"PROCESSSTATUS\" version=\"2.0\">" +
               "<process id=\"" + processId + "\" status=\"STATE_ERROR\" result=\"-1\">" +
               "<error code=\"" + errorCode + "\" message=\"" + errorMessage + "\" />" +
               "</process>" +
               "</mws>";
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_SetData")
    @ResponsePayload
    public ProcessSetDataResponse processSetData(@RequestPayload ProcessSetData request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;
        String dataType  = request.getDataType()  != null ? request.getDataType().getValue()  : null;
        String data      = request.getData()      != null ? request.getData().getValue()      : null;

        logger.info("Process_SetData: sessionId={}, processId={}, dataType={}, data={}",
                sessionId, processId, dataType, data);

        // setZusatzVars and setProcessID both map here ? just persist the call, no state change needed
        stateStore.getOrCreate(processId);

        ObjectFactory of = new ObjectFactory();
        ProcessSetDataResponse response = of.createProcessSetDataResponse();
        response.setProcessSetDataResult(0);
        response.setXmlResult(of.createProcessSetDataResponseXmlResult(""));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_Open")
    @ResponsePayload
    public ProcessOpenResponse processOpen(@RequestPayload ProcessOpen request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;

        logger.info("Process_Open: sessionId={}, processId={}", sessionId, processId);

        ProcessState state = stateStore.getOrCreate(processId);
        state.setCloseStatus(ProcessState.CloseStatus.OPEN);

        ObjectFactory of = new ObjectFactory();
        ProcessOpenResponse response = of.createProcessOpenResponse();
        response.setProcessOpenResult(0);
        response.setXmlResult(of.createProcessOpenResponseXmlResult(""));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_Close")
    @ResponsePayload
    public ProcessCloseResponse processClose(@RequestPayload ProcessClose request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;
        // status parameter: 0=SPEICHERN, 1=ERFOLGREICH_ABGESCHLOSSEN, 2=FEHLERHAFT_BEENDET
        int statusCode = request.getStatus() != null ? request.getStatus() : STATUS_SPEICHERN;

        logger.info("Process_Close: sessionId={}, processId={}, statusCode={}", sessionId, processId, statusCode);

        ProcessState state = stateStore.getOrCreate(processId);
        switch (statusCode) {
            case STATUS_SPEICHERN:
                state.setCloseStatus(ProcessState.CloseStatus.SAVED);
                logger.info("Process_Close STATUS_SPEICHERN (0): processId={} saved (can be reopened)", processId);
                break;
            case STATUS_ERFOLGREICH_ABGESCHLOSSEN:
                state.setCloseStatus(ProcessState.CloseStatus.COMPLETED_OK);
                logger.info("Process_Close STATUS_ERFOLGREICH_ABGESCHLOSSEN (2): processId={} completed OK", processId);
                break;
            case STATUS_FEHLERHAFT_BEENDET:
                state.setCloseStatus(ProcessState.CloseStatus.COMPLETED_ERROR);
                logger.warn("Process_Close STATUS_FEHLERHAFT_BEENDET (3): processId={} completed with error", processId);
                break;
            default:
                state.setCloseStatus(ProcessState.CloseStatus.COMPLETED_OK);
                logger.info("Process_Close statusCode={} (treating as completed): processId={}", statusCode, processId);
        }

        ObjectFactory of = new ObjectFactory();
        ProcessCloseResponse response = of.createProcessCloseResponse();
        response.setProcessCloseResult(0);
        response.setXmlResult(of.createProcessCloseResponseXmlResult(""));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_Delete")
    @ResponsePayload
    public ProcessDeleteResponse processDelete(@RequestPayload ProcessDelete request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId().getValue() : null;
        String processId = request.getProcessId() != null ? request.getProcessId().getValue() : null;

        logger.info("Process_Delete: sessionId={}, processId={}", sessionId, processId);

        stateStore.remove(processId);

        ObjectFactory of = new ObjectFactory();
        ProcessDeleteResponse response = of.createProcessDeleteResponse();
        response.setProcessDeleteResult(0);
        response.setXmlResult(of.createProcessDeleteResponseXmlResult(""));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_GetList")
    @ResponsePayload
    public Object processGetList(@RequestPayload Object request) {
        logger.info("Process_GetList request received");
        return createMockResponse("Process_GetListResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_GetLastError")
    @ResponsePayload
    public Object processGetLastError(@RequestPayload Object request) {
        logger.info("Process_GetLastError request received");
        return createMockResponse("Process_GetLastErrorResponse", "");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_GetTransDocsList")
    @ResponsePayload
    public Object processGetTransDocsList(@RequestPayload Object request) {
        logger.info("Process_GetTransDocsList request received");
        return createMockResponse("Process_GetTransDocsListResponse", "[]");
    }


    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Process_Forward")
    @ResponsePayload
    public Object processForward(@RequestPayload Object request) {
        logger.info("Process_Forward request received");
        return createMockResponse("Process_ForwardResponse", true);
    }

    // ============ Object Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Obj_GetStructure")
    @ResponsePayload
    public Object objGetStructure(@RequestPayload Object request) {
        logger.info("Obj_GetStructure request received");
        return createMockResponse("Obj_GetStructureResponse", "{}");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Obj_Toggle")
    @ResponsePayload
    public Object objToggle(@RequestPayload Object request) {
        logger.info("Obj_Toggle request received");
        return createMockResponse("Obj_ToggleResponse", true);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Obj_SetStructure")
    @ResponsePayload
    public Object objSetStructure(@RequestPayload Object request) {
        logger.info("Obj_SetStructure request received");
        return createMockResponse("Obj_SetStructureResponse", true);
    }

    // ============ Document Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Doc_GetFile_Mime")
    @ResponsePayload
    public Object docGetFileMime(@RequestPayload Object request) {
        logger.info("Doc_GetFile_Mime request received");
        return createMockResponse("Doc_GetFile_MimeResponse", new byte[0]);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Doc_SetFile_Mime")
    @ResponsePayload
    public Object docSetFileMime(@RequestPayload Object request) {
        logger.info("Doc_SetFile_Mime request received");
        return createMockResponse("Doc_SetFile_MimeResponse", true);
    }

    // ============ Repository Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetSystemList")
    @ResponsePayload
    public Object repGetSystemList(@RequestPayload Object request) {
        logger.info("Rep_GetSystemList request received");
        return createMockResponse("Rep_GetSystemListResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetFolderContent")
    @ResponsePayload
    public Object repGetFolderContent(@RequestPayload Object request) {
        logger.info("Rep_GetFolderContent request received");
        return createMockResponse("Rep_GetFolderContentResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetNavigList")
    @ResponsePayload
    public Object repGetNavigList(@RequestPayload Object request) {
        logger.info("Rep_GetNavigList request received");
        return createMockResponse("Rep_GetNavigListResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetNavigTree")
    @ResponsePayload
    public Object repGetNavigTree(@RequestPayload Object request) {
        logger.info("Rep_GetNavigTree request received");
        return createMockResponse("Rep_GetNavigTreeResponse", "{}");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetPrinters")
    @ResponsePayload
    public Object repGetPrinters(@RequestPayload Object request) {
        logger.info("Rep_GetPrinters request received");
        return createMockResponse("Rep_GetPrintersResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetForms")
    @ResponsePayload
    public Object repGetForms(@RequestPayload Object request) {
        logger.info("Rep_GetForms request received");
        return createMockResponse("Rep_GetFormsResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetSystem")
    @ResponsePayload
    public Object repGetSystem(@RequestPayload Object request) {
        logger.info("Rep_GetSystem request received");
        return createMockResponse("Rep_GetSystemResponse", "{}");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetBinFile_Mime")
    @ResponsePayload
    public Object repGetBinFileMime(@RequestPayload Object request) {
        logger.info("Rep_GetBinFile_Mime request received");
        return createMockResponse("Rep_GetBinFile_MimeResponse", new byte[0]);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetLookupObjects")
    @ResponsePayload
    public Object repGetLookupObjects(@RequestPayload Object request) {
        logger.info("Rep_GetLookupObjects request received");
        return createMockResponse("Rep_GetLookupObjectsResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetLookupValue")
    @ResponsePayload
    public Object repGetLookupValue(@RequestPayload Object request) {
        logger.info("Rep_GetLookupValue request received");
        return createMockResponse("Rep_GetLookupValueResponse", "");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetValueHelpDefinition")
    @ResponsePayload
    public Object repGetValueHelpDefinition(@RequestPayload Object request) {
        logger.info("Rep_GetValueHelpDefinition request received");
        return createMockResponse("Rep_GetValueHelpDefinitionResponse", "{}");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetDataProviderDefinition")
    @ResponsePayload
    public Object repGetDataProviderDefinition(@RequestPayload Object request) {
        logger.info("Rep_GetDataProviderDefinition request received");
        return createMockResponse("Rep_GetDataProviderDefinitionResponse", "{}");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetItemInfo")
    @ResponsePayload
    public Object repGetItemInfo(@RequestPayload Object request) {
        logger.info("Rep_GetItemInfo request received");
        return createMockResponse("Rep_GetItemInfoResponse", "{}");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetItemDescription")
    @ResponsePayload
    public Object repGetItemDescription(@RequestPayload Object request) {
        logger.info("Rep_GetItemDescription request received");
        return createMockResponse("Rep_GetItemDescriptionResponse", "");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_CreateItem")
    @ResponsePayload
    public Object repCreateItem(@RequestPayload Object request) {
        logger.info("Rep_CreateItem request received");
        return createMockResponse("Rep_CreateItemResponse", 1);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_LockItem")
    @ResponsePayload
    public Object repLockItem(@RequestPayload Object request) {
        logger.info("Rep_LockItem request received");
        return createMockResponse("Rep_LockItemResponse", true);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_UnLockItem")
    @ResponsePayload
    public Object repUnLockItem(@RequestPayload Object request) {
        logger.info("Rep_UnLockItem request received");
        return createMockResponse("Rep_UnLockItemResponse", true);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_UpdateItemBlob")
    @ResponsePayload
    public Object repUpdateItemBlob(@RequestPayload Object request) {
        logger.info("Rep_UpdateItemBlob request received");
        return createMockResponse("Rep_UpdateItemBlobResponse", true);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_ReadItemBlob")
    @ResponsePayload
    public Object repReadItemBlob(@RequestPayload Object request) {
        logger.info("Rep_ReadItemBlob request received");
        return createMockResponse("Rep_ReadItemBlobResponse", new byte[0]);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_CreateVersion")
    @ResponsePayload
    public Object repCreateVersion(@RequestPayload Object request) {
        logger.info("Rep_CreateVersion request received");
        return createMockResponse("Rep_CreateVersionResponse", true);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Rep_GetWordTemplate")
    @ResponsePayload
    public Object repGetWordTemplate(@RequestPayload Object request) {
        logger.info("Rep_GetWordTemplate request received");
        return createMockResponse("Rep_GetWordTemplateResponse", new byte[0]);
    }

    // ============ Service Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Srv_GetInfo")
    @ResponsePayload
    public Object srvGetInfo(@RequestPayload Object request) {
        logger.info("Srv_GetInfo request received");
        return createMockResponse("Srv_GetInfoResponse", "{}");
    }

    // ============ User Operations ============

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Usr_GetRoles")
    @ResponsePayload
    public Object usrGetRoles(@RequestPayload Object request) {
        logger.info("Usr_GetRoles request received");
        return createMockResponse("Usr_GetRolesResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Usr_GetUsersOfRole")
    @ResponsePayload
    public Object usrGetUsersOfRole(@RequestPayload Object request) {
        logger.info("Usr_GetUsersOfRole request received");
        return createMockResponse("Usr_GetUsersOfRoleResponse", "[]");
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "Usr_GetUsers")
    @ResponsePayload
    public Object usrGetUsers(@RequestPayload Object request) {
        logger.info("Usr_GetUsers request received");
        return createMockResponse("Usr_GetUsersResponse", "[]");
    }

    // ============ Helper Method ============

    /** Matches CMD_CREATE_DOCUMENTS: numeric "2" or string containing "CREATE_DOCUMENTS" */
    private boolean isCreateDocuments(String value) {
        if (value == null) return false;
        return CMD_ID_CREATE_DOCUMENTS.equals(value.trim()) || value.toUpperCase().contains(CMD_NAME_CREATE_DOCUMENTS);
    }

    /** Matches CMD_PRINT_AND_ARCHIVE: numeric "3" or string containing "PRINT_AND_ARCHIVE" */
    private boolean isPrintAndArchive(String value) {
        if (value == null) return false;
        return CMD_ID_PRINT_AND_ARCHIVE.equals(value.trim()) || value.toUpperCase().contains(CMD_NAME_PRINT_AND_ARCHIVE);
    }

    private Object createMockResponse(String operationName, Object result) {
        // TODO: Replace with actual JAXB-generated response class
        // For now, this logs the operation and returns a placeholder

        logger.info("Mock response for: {}, Result: {}", operationName, result);
        return null;
    }
}