package net.disy.wps.richwps.response;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import net.disy.wps.richwps.request.TestProcessRequest;
import net.opengis.ows.x11.DomainMetadataType;
import net.opengis.ows.x11.LanguageStringType;
import net.opengis.wps.x100.DataInputsType;
import net.opengis.wps.x100.DocumentOutputDefinitionType;
import net.opengis.wps.x100.ExecuteResponseDocument;
import net.opengis.wps.x100.ExecuteResponseDocument.ExecuteResponse;
import net.opengis.wps.x100.OutputDataType;
import net.opengis.wps.x100.OutputDefinitionType;
import net.opengis.wps.x100.OutputDescriptionType;
import net.opengis.wps.x100.ProcessDescriptionType;
import net.opengis.wps.x100.StatusType;
import net.opengis.wps.x100.TestProcessResponseDocument;
import net.opengis.wps.x100.TestProcessResponseDocument.TestProcessResponse;

import org.apache.xmlbeans.XmlCursor;
import org.n52.wps.io.data.IBBOXData;
import org.n52.wps.io.data.IData;
import org.n52.wps.server.CapabilitiesConfiguration;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.RepositoryManager;
import org.n52.wps.server.WebProcessingService;
import org.n52.wps.server.database.DatabaseFactory;
import org.n52.wps.server.request.Request;
import org.n52.wps.server.response.OutputDataItem;
import org.n52.wps.server.response.RawData;
import org.n52.wps.util.XMLBeansHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hsos.richwps.dsl.api.elements.ReferenceOutputMapping;

/**
 * This implementation provides functionality for building the Response on a
 * TestProcessRequest. It holds, gathers and generates all necessary data to
 * create the Response.
 * 
 * 
 * @author faltin
 *
 */
public class TestProcessResponseBuilder {
	private static Logger LOGGER = LoggerFactory.getLogger(TestProcessResponseBuilder.class);

	private String identifier;
	private DataInputsType dataInputs;
	protected TestProcessResponseDocument testProcessResponseDocument;
	private ExecuteResponseDocument executeResponseDocument;
	private TestProcessRequest request;
	private RawData rawDataElement;
	private ProcessDescriptionType description;
	private Calendar creationTime;
	private List<OutputReferenceDescription> outputReferenceDescs;

	/**
	 * Constructs a new TestProcessResponseBuilder
	 * 
	 * @param request
	 *            the TestProcessRequest
	 */
	public TestProcessResponseBuilder(TestProcessRequest request) {
		this.request = request;
		description = this.request.getProcessDescription();
		identifier = description.getIdentifier().getStringValue().trim();
		if (description == null) {
			throw new RuntimeException("Error while accessing the process description for "
					+ identifier);
		}
		testProcessResponseDocument = TestProcessResponseDocument.Factory.newInstance();
		testProcessResponseDocument.addNewTestProcessResponse();
		XmlCursor c = testProcessResponseDocument.newCursor();
		c.toFirstChild();
		c.toLastAttribute();
		c.setAttributeText(
				new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation"),
				"./wpsTestProcess_response.xsd");
		TestProcessResponse testProcessResponse = testProcessResponseDocument
				.getTestProcessResponse();
		testProcessResponse.setServiceInstance(CapabilitiesConfiguration.WPS_ENDPOINT_URL
				+ "?REQUEST=GetCapabilities&SERVICE=WPS");
		testProcessResponse.setLang(WebProcessingService.DEFAULT_LANGUAGE);
		testProcessResponse.setService("WPS");
		testProcessResponse.setVersion(Request.SUPPORTED_VERSION);

		testProcessResponse.addNewProcess();
		testProcessResponse.getProcess().addNewIdentifier().setStringValue(identifier);
		testProcessResponse.getProcess().setProcessVersion(description.getProcessVersion());
		testProcessResponse.getProcess().setTitle(description.getTitle());
		initializeExecuteResponseDocument();
		creationTime = Calendar.getInstance();
	}

	private void initializeExecuteResponseDocument() {

		executeResponseDocument = ExecuteResponseDocument.Factory.newInstance();
		executeResponseDocument.addNewExecuteResponse();
		XmlCursor c = executeResponseDocument.newCursor();
		c.toFirstChild();
		c.toLastAttribute();
		c.setAttributeText(
				new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation"),
				"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsExecute_response.xsd");
		executeResponseDocument.getExecuteResponse()
				.setServiceInstance(
						CapabilitiesConfiguration.WPS_ENDPOINT_URL
								+ "?REQUEST=GetCapabilities&SERVICE=WPS");
		executeResponseDocument.getExecuteResponse().setLang(WebProcessingService.DEFAULT_LANGUAGE);
		executeResponseDocument.getExecuteResponse().setService("WPS");
		executeResponseDocument.getExecuteResponse().setVersion(Request.SUPPORTED_VERSION);

		ExecuteResponse responseElem = executeResponseDocument.getExecuteResponse();
		responseElem.addNewProcess().addNewIdentifier().setStringValue(identifier);
		responseElem.getProcess().setTitle(description.getTitle());
		responseElem.getProcess().setProcessVersion(description.getProcessVersion());
	}

	/**
	 * Returns the Response.
	 * 
	 * @return the Response
	 * @throws ExceptionReport
	 */
	public InputStream getAsStream() throws ExceptionReport {
		if (request.isRawData() && rawDataElement != null) {
			return rawDataElement.getAsStream();
		}
		if (request.isStoreResponse()) {
			String id = request.getUniqueId().toString();
			String statusLocation = DatabaseFactory.getDatabase().generateRetrieveResultURL(id);
			testProcessResponseDocument.getTestProcessResponse().setStatusLocation(statusLocation);
		}
		try {
			return testProcessResponseDocument.newInputStream(XMLBeansHelper.getXmlOptions());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Updates the status of the processing. If status is success the outputs
	 * are parsed. If Response has to be stored the location of the response is
	 * set.
	 * 
	 * @throws ExceptionReport
	 */
	public void update() throws ExceptionReport {

		net.opengis.wps.x100.TestProcessResponseDocument.TestProcessResponse testProcessResponseElem = testProcessResponseDocument
				.getTestProcessResponse();
		ExecuteResponse executeResponseElem = executeResponseDocument.getExecuteResponse();

		if (testProcessResponseElem.getStatus().isSetProcessSucceeded()) {
			dataInputs = request.getTestProcess().getDataInputs();
			testProcessResponseElem.setDataInputs(dataInputs);
			executeResponseElem.setDataInputs(dataInputs);
			testProcessResponseElem.addNewProcessOutputs();
			executeResponseElem.addNewProcessOutputs();

			outputReferenceDescs = new ArrayList<OutputReferenceDescription>();
			List<ReferenceOutputMapping> outputReferenceMappings = request
					.getOutputReferenceMappings();
			OutputDescriptionType[] outputDescs = description.getProcessOutputs().getOutputArray();
			if (request.getTestProcess().isSetResponseForm()) {

				for (int i = 0; i < outputReferenceMappings.size(); i++) {
					String outputIdentifier = outputReferenceMappings.get(i).getOutputIdentifier();

					OutputDescriptionType[] descs = RepositoryManager
							.getInstance()
							.getProcessDescription(
									outputReferenceMappings.get(i).getProcessIdentifier())
							.getProcessOutputs().getOutputArray();
					for (OutputDescriptionType desc : descs) {
						if (desc.getIdentifier().getStringValue().equals(outputIdentifier)) {
							outputReferenceDescs.add(new OutputReferenceDescription(
									outputReferenceMappings.get(i), desc));
						}
					}
				}

				if (request.isRawData()) {
					OutputDefinitionType rawDataOutput = request.getTestProcess().getResponseForm()
							.getRawDataOutput();
					String definedOutputId = rawDataOutput.getIdentifier().getStringValue();
					OutputDescriptionType desc = getOutputDescription(outputDescs, definedOutputId);
					if (desc.isSetComplexOutput()) {
						String encoding = TestProcessResponseBuilder.getEncoding(desc,
								rawDataOutput);
						String schema = TestProcessResponseBuilder.getSchema(desc, rawDataOutput);
						String responseMimeType = getMimeType(rawDataOutput, null);

						generateComplexDataOutput(definedOutputId, false, true, schema,
								responseMimeType, encoding, null);
					} else if (desc.isSetLiteralOutput()) {
						String mimeType = null;
						String schema = null;
						String encoding = null;
						DomainMetadataType dataType = desc.getLiteralOutput().getDataType();
						String reference = dataType != null ? dataType.getReference() : null;
						generateLiteralDataOutput(definedOutputId, true, reference, schema,
								mimeType, encoding, desc.getTitle());
					} else if (desc.isSetBoundingBoxOutput()) {
						generateBBOXOutput(definedOutputId, true, desc.getTitle());
					}
					return;
				}
				for (int i = 0; i < request.getTestProcess().getResponseForm()
						.getResponseDocument().getOutputArray().length; i++) {
					OutputDefinitionType definition = request.getTestProcess().getResponseForm()
							.getResponseDocument().getOutputArray(i);
					String definedOutputId = definition.getIdentifier().getStringValue();
					OutputReferenceDescription varOutDescription = getVarOutDescriptionOfDefinedOutput(definedOutputId);
					OutputDescriptionType desc = null;
					if (varOutDescription != null) {
						desc = getDescOfVariable(definedOutputId);
					} else {
						desc = XMLBeansHelper.findOutputByID(definedOutputId, outputDescs);
					}
					if (desc == null) {
						throw new ExceptionReport(
								"Could not find the output id " + definedOutputId,
								ExceptionReport.INVALID_PARAMETER_VALUE);
					}
					if (desc.isSetComplexOutput()) {

						String mimeType = getMimeType(definition, varOutDescription);
						String schema = TestProcessResponseBuilder.getSchema(desc, definition);
						String encoding = TestProcessResponseBuilder.getEncoding(desc, definition);

						generateComplexDataOutput(definedOutputId,
								((DocumentOutputDefinitionType) definition).getAsReference(),
								false, schema, mimeType, encoding, desc.getTitle());
					} else if (desc.isSetLiteralOutput()) {
						String mimeType = null;
						String schema = null;
						String encoding = null;
						DomainMetadataType dataType = desc.getLiteralOutput().getDataType();
						String reference = dataType != null ? dataType.getReference() : null;
						generateLiteralDataOutput(definedOutputId, false, reference, schema,
								mimeType, encoding, desc.getTitle());
					} else if (desc.isSetBoundingBoxOutput()) {
						generateBBOXOutput(definedOutputId, false, desc.getTitle());
					} else {
						throw new ExceptionReport("Requested type not supported: BBOX",
								ExceptionReport.INVALID_PARAMETER_VALUE);
					}
				}
			} else {
				LOGGER.info("OutputDefinitions are not stated explicitly in request");
				for (int i = 0; i < outputReferenceMappings.size(); i++) {
					String currentProcessId = outputReferenceMappings.get(i).getProcessIdentifier();
					OutputDescriptionType[] descs = RepositoryManager.getInstance()
							.getProcessDescription(currentProcessId).getProcessOutputs()
							.getOutputArray();
					for (OutputDescriptionType desc : descs) {
						if (desc.getIdentifier().getStringValue()
								.equals(outputReferenceMappings.get(i).getOutputIdentifier())) {
							OutputReferenceDescription varOutDesc = new OutputReferenceDescription(
									outputReferenceMappings.get(i), desc);
							if (!varOutDescsContains(varOutDesc)) {
								outputReferenceDescs.add(varOutDesc);
							}
						}
					}
				}

				if (description == null) {
					throw new RuntimeException("Error while accessing the process description for "
							+ request.getTestProcess().getProcessDescription().getIdentifier()
									.getStringValue());
				}
				for (int i = 0; i < outputDescs.length; i++) {
					if (outputDescs[i].isSetComplexOutput()) {
						String schema = outputDescs[i].getComplexOutput().getDefault().getFormat()
								.getSchema();
						String encoding = outputDescs[i].getComplexOutput().getDefault()
								.getFormat().getEncoding();
						String mimeType = outputDescs[i].getComplexOutput().getDefault()
								.getFormat().getMimeType();
						generateComplexDataOutput(outputDescs[i].getIdentifier().getStringValue(),
								false, false, schema, mimeType, encoding, outputDescs[i].getTitle());
					} else if (outputDescs[i].isSetLiteralOutput()) {
						generateLiteralDataOutput(outputDescs[i].getIdentifier().getStringValue(),
								false, outputDescs[i].getLiteralOutput().getDataType()
										.getReference(), null, null, null,
								outputDescs[i].getTitle());
					}
				}
				for (int i = 0; i < outputReferenceDescs.size(); i++) {
					if (outputReferenceDescs.get(i).getDescription().isSetComplexOutput()) {
						String schema = outputReferenceDescs.get(i).getDescription()
								.getComplexOutput().getDefault().getFormat().getSchema();
						String encoding = outputReferenceDescs.get(i).getDescription()
								.getComplexOutput().getDefault().getFormat().getEncoding();
						String mimeType = outputReferenceDescs.get(i).getDescription()
								.getComplexOutput().getDefault().getFormat().getMimeType();
						generateComplexDataOutput(outputReferenceDescs.get(i)
								.getReferenceOutputMapping().getOutputReference(), false, false,
								schema, mimeType, encoding, outputReferenceDescs.get(i)
										.getDescription().getTitle());
					} else if (outputReferenceDescs.get(i).getDescription().isSetLiteralOutput()) {
						generateLiteralDataOutput(outputReferenceDescs.get(i)
								.getReferenceOutputMapping().getOutputReference(), false,
								outputReferenceDescs.get(i).getDescription().getLiteralOutput()
										.getDataType().getReference(), null, null, null,
								outputReferenceDescs.get(i).getDescription().getTitle());
					}

				}
			}
		} else if (request.isStoreResponse()) {
			testProcessResponseElem.setStatusLocation(DatabaseFactory.getDatabase()
					.generateRetrieveResultURL((request.getUniqueId()).toString()));
		}
	}

	private OutputDescriptionType getOutputDescription(OutputDescriptionType[] outputDescs,
			String definedOutputId) {
		OutputDescriptionType desc = XMLBeansHelper.findOutputByID(definedOutputId, outputDescs);
		if (desc == null) {
			desc = getDescOfVariable(definedOutputId);
		}
		return desc;
	}

	private boolean varOutDescsContains(OutputReferenceDescription varOutDesc) {
		for (int i = 0; i < outputReferenceDescs.size(); i++) {
			if (outputReferenceDescs.get(i).getReferenceOutputMapping().getOutputReference()
					.equals(varOutDesc.getReferenceOutputMapping().getOutputReference())) {
				return true;
			}
		}
		return false;
	}

	private OutputDescriptionType getDescOfVariable(String definedOutputId) {
		OutputDescriptionType outputDescription = null;
		for (OutputReferenceDescription varOutDescription : outputReferenceDescs) {
			if (varOutDescription.getReferenceOutputMapping().getOutputReference()
					.equals(definedOutputId)) {
				outputDescription = varOutDescription.getDescription();
			}
		}
		return outputDescription;
	}

	private OutputReferenceDescription getVarOutDescriptionOfDefinedOutput(String varReferenceId) {
		OutputReferenceDescription varOutDescription = null;
		for (int i = 0; i < outputReferenceDescs.size(); i++) {
			if (outputReferenceDescs.get(i).getReferenceOutputMapping().getOutputReference()
					.equals(varReferenceId)) {
				varOutDescription = outputReferenceDescs.get(i);
			}
		}
		return varOutDescription;
	}

	/**
	 * Sets the status.
	 * 
	 * @param status
	 *            status to be set.
	 */
	public void setStatus(StatusType status) {
		status.setCreationTime(creationTime);
		testProcessResponseDocument.getTestProcessResponse().setStatus(status);
	}

	private static String getSchema(OutputDescriptionType desc, OutputDefinitionType def) {
		String schema = null;
		if (def != null) {
			schema = def.getSchema();
		}

		return schema;
	}

	private static String getEncoding(OutputDescriptionType desc, OutputDefinitionType def) {
		String encoding = null;
		if (def != null) {
			encoding = def.getEncoding();
		}
		return encoding;
	}

	/**
	 * Returns the mime-type.
	 * 
	 * @return the mime-type.
	 */
	public String getMimeType() {
		return getMimeType(null, null);
	}

	/**
	 * Returns the mime-type.
	 * 
	 * @param def
	 *            the definition of the output.
	 * @param outputReferenceDescription
	 *            the description of
	 * @return the mime-type
	 */
	public String getMimeType(OutputDefinitionType def,
			OutputReferenceDescription outputReferenceDescription) {

		String mimeType = "";
		OutputDescriptionType[] outputDescs = description.getProcessOutputs().getOutputArray();
		boolean isSetResponseForm = request.getTestProcess().isSetResponseForm();

		String definedOutputId = "";

		if (def != null) {
			definedOutputId = def.getIdentifier().getStringValue();
		} else if (isSetResponseForm) {

			if (request.getTestProcess().getResponseForm().isSetRawDataOutput()) {
				definedOutputId = request.getTestProcess().getResponseForm().getRawDataOutput()
						.getIdentifier().getStringValue();
			} else if (request.getTestProcess().getResponseForm().isSetResponseDocument()) {
				definedOutputId = request.getTestProcess().getResponseForm().getResponseDocument()
						.getOutputArray(0).getIdentifier().getStringValue();
			}
		}

		OutputDescriptionType outputDesc = null;
		if (outputReferenceDescription != null) {
			outputDesc = outputReferenceDescription.getDescription();
		} else {
			for (OutputDescriptionType tmpOutputDes : outputDescs) {
				if (definedOutputId.equalsIgnoreCase(tmpOutputDes.getIdentifier().getStringValue())) {
					outputDesc = tmpOutputDes;
					break;
				}
			}
		}

		if (isSetResponseForm) {
			if (request.isRawData()) {
				mimeType = request.getTestProcess().getResponseForm().getRawDataOutput()
						.getMimeType();
			} else {
				if (outputDesc.isSetLiteralOutput()) {
					mimeType = "text/plain";
				} else if (outputDesc.isSetBoundingBoxOutput()) {
					mimeType = "text/xml";
				} else {
					if (def != null) {
						mimeType = def.getMimeType();
					} else {
						if (outputDesc.isSetComplexOutput()) {
							mimeType = outputDesc.getComplexOutput().getDefault().getFormat()
									.getMimeType();
							LOGGER.warn("Using default mime type: " + mimeType + " for input: "
									+ definedOutputId);
						}
					}
				}
			}
		}
		if (mimeType == null) {
			if (outputDesc.isSetLiteralOutput()) {
				mimeType = "text/plain";
			} else if (outputDesc.isSetBoundingBoxOutput()) {
				mimeType = "text/xml";
			} else if (outputDesc.isSetComplexOutput()) {
				mimeType = outputDesc.getComplexOutput().getDefault().getFormat().getMimeType();
				LOGGER.warn("Using default mime type: " + mimeType + " for input: "
						+ definedOutputId);
			}
		}

		return mimeType;
	}

	private void generateComplexDataOutput(String definedOutputId, boolean asReference,
			boolean rawData, String schema, String mimeType, String encoding,
			LanguageStringType title) throws ExceptionReport {
		IData obj = request.getAttachedResult().get(definedOutputId);
		if (rawData) {
			rawDataElement = new RawData(obj, getOutputIdOfDefinedOutput(definedOutputId), schema,
					encoding, mimeType, getProcessIdOfRelatedOutput(definedOutputId),
					getProcessDescriptionOfRelatedOutput(definedOutputId));
		} else {
			OutputDataItem outputDataItem = new OutputDataItem(obj,
					getOutputIdOfDefinedOutput(definedOutputId), definedOutputId, schema, encoding,
					mimeType, title, getProcessIdOfRelatedOutput(definedOutputId),
					getProcessDescriptionOfRelatedOutput(definedOutputId));
			if (asReference) {
				outputDataItem.updateResponseAsReference(executeResponseDocument,
						(request.getUniqueId()).toString(), mimeType);
				updateTestProcessResponse(definedOutputId);
			} else {
				outputDataItem.updateResponseForInlineComplexData(executeResponseDocument);
				updateTestProcessResponse(definedOutputId);
			}
		}

	}

	private String getOutputIdOfDefinedOutput(String definedOutputId) {
		String outputIdentifier = null;
		for (OutputReferenceDescription varOutDescription : outputReferenceDescs) {
			if (varOutDescription.getReferenceOutputMapping().getOutputReference()
					.equals(definedOutputId)) {
				outputIdentifier = varOutDescription.getReferenceOutputMapping()
						.getOutputIdentifier();
			}
		}
		if (outputIdentifier == null) {
			outputIdentifier = definedOutputId;
		}
		return outputIdentifier;
	}

	private String getProcessIdOfRelatedOutput(String definedOutputId) {
		String processId = null;
		for (OutputReferenceDescription varOutDescription : outputReferenceDescs) {
			if (varOutDescription.getReferenceOutputMapping().getOutputReference()
					.equals(definedOutputId)) {
				processId = varOutDescription.getReferenceOutputMapping().getProcessIdentifier();
			}
		}
		if (processId == null) {
			processId = identifier;
		}
		return processId;
	}

	private ProcessDescriptionType getProcessDescriptionOfRelatedOutput(String definedOutputId) {
		ProcessDescriptionType processDescription = null;
		for (OutputReferenceDescription varOutDescription : outputReferenceDescs) {
			if (varOutDescription.getReferenceOutputMapping().getOutputReference()
					.equals(definedOutputId)) {
				String processId = varOutDescription.getReferenceOutputMapping()
						.getProcessIdentifier();
				processDescription = RepositoryManager.getInstance().getProcessDescription(
						processId);
			}
		}
		if (processDescription == null) {
			processDescription = description;
		}
		return processDescription;
	}

	private void updateTestProcessResponse(String responseID) {
		OutputDataType output = testProcessResponseDocument.getTestProcessResponse()
				.getProcessOutputs().addNewOutput();
		OutputDataType[] outputs = executeResponseDocument.getExecuteResponse().getProcessOutputs()
				.getOutputArray();
		for (OutputDataType currentOutput : outputs) {
			if (currentOutput.getIdentifier().getStringValue().equals(responseID)) {
				output.set(currentOutput);
			}
		}
	}

	private void generateLiteralDataOutput(String definedOutputId, boolean rawData,
			String dataTypeReference, String schema, String mimeType, String encoding,
			LanguageStringType title) throws ExceptionReport {
		IData obj = request.getAttachedResult().get(definedOutputId);
		if (rawData) {
			rawDataElement = new RawData(obj, getOutputIdOfDefinedOutput(definedOutputId), schema,
					encoding, mimeType, getProcessIdOfRelatedOutput(definedOutputId),
					getProcessDescriptionOfRelatedOutput(definedOutputId));
		} else {
			OutputDataItem handler = new OutputDataItem(obj,
					getOutputIdOfDefinedOutput(definedOutputId), definedOutputId, schema, encoding,
					mimeType, title, getProcessIdOfRelatedOutput(definedOutputId),
					getProcessDescriptionOfRelatedOutput(definedOutputId));
			handler.updateResponseForLiteralData(executeResponseDocument, dataTypeReference);
			updateTestProcessResponse(definedOutputId);
		}
	}

	private void generateBBOXOutput(String definedOutputId, boolean rawData,
			LanguageStringType title) throws ExceptionReport {
		IBBOXData obj = (IBBOXData) request.getAttachedResult().get(definedOutputId);
		if (rawData) {
			rawDataElement = new RawData(obj, getOutputIdOfDefinedOutput(definedOutputId), null,
					null, null, getProcessIdOfRelatedOutput(definedOutputId),
					getProcessDescriptionOfRelatedOutput(definedOutputId));
		} else {
			OutputDataItem handler = new OutputDataItem(obj,
					getOutputIdOfDefinedOutput(definedOutputId), definedOutputId, null, null, null,
					title, getProcessIdOfRelatedOutput(definedOutputId),
					getProcessDescriptionOfRelatedOutput(definedOutputId));
			handler.updateResponseForBBOXData(executeResponseDocument, obj);
			updateTestProcessResponse(definedOutputId);
		}

	}

}
