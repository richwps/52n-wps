package net.disy.wps.richwps.oe;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Observer;

import net.disy.wps.richwps.dtm.DataTypeManager;
import net.opengis.wps.x100.ExecuteDocument;
import net.opengis.wps.x100.InputDescriptionType;
import net.opengis.wps.x100.OutputDescriptionType;
import net.opengis.wps.x100.ProcessDescriptionType;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.n52.wps.io.data.IData;
import org.n52.wps.server.AbstractTransactionalAlgorithm;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.transactional.algorithm.GenericTransactionalAlgorithm;
import org.n52.wps.transactional.deploy.IProcessManager;
import org.n52.wps.transactional.service.TransactionalHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hsos.richwps.dsl.api.elements.ReferenceOutputMapping;

public class RolaAlgorithm extends AbstractTransactionalAlgorithm {

	private static Logger LOGGER = LoggerFactory.getLogger(RolaAlgorithm.class);

	DataTypeManager dtm = DataTypeManager.getInstance();
	private IProcessManager deployManager;
	private ProcessDescriptionType processDescription;

	public RolaAlgorithm(String algorithmID) {
		super(algorithmID);
		processDescription = initializeDescription();
	}

	@Override
	public Map<String, IData> run(Map<String, List<IData>> inputData)
			throws ExceptionReport {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getErrors() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getWellKnownName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ProcessDescriptionType getDescription() {
		return processDescription;
	}

	@Override
	public boolean processDescriptionIsValid() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Class<?> getInputDataType(String id) {
		InputDescriptionType[] inputs = processDescription.getDataInputs()
				.getInputArray();
		for (InputDescriptionType input : inputs) {
			if (input.getIdentifier().getStringValue().equals(id)) {
				return dtm.getBindingForInputType(input);
			}
		}
		throw new RuntimeException("Could not determie internal inputDataType");
	}

	@Override
	public Class<?> getOutputDataType(String id) {
		OutputDescriptionType[] outputs = processDescription
				.getProcessOutputs().getOutputArray();
		for (OutputDescriptionType output : outputs) {
			if (output.getIdentifier().getStringValue().equals(id)) {
				return dtm.getBindingForOutputType(output);
			}
		}
		throw new RuntimeException("Could not determie internal inputDataType");
	}

	@Override
	public Map<String, IData> run(ExecuteDocument document) {
		try {
			// FIXME switch to app-wide constant.
			IProcessManager deployManager = TransactionalHelper
					.getProcessManagerForSchema("rola");
			return deployManager.invoke(document, getAlgorithmID());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.n52.wps.server.AbstractTransactionalAlgorithm#runTest(net.opengis
	 * .wps.x100.ExecuteDocument)
	 */
	@Override
	public Map<String, IData> runTest(ExecuteDocument document) {
		try {
			// FIXME switch to app-wide constant.
			deployManager = TransactionalHelper
					.getProcessManagerForSchema("rola");
			return deployManager.invokeTest(document, getAlgorithmID());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.n52.wps.server.AbstractTransactionalAlgorithm#getReferenceOutputMappings
	 * ()
	 */
	@Override
	public List<ReferenceOutputMapping> getReferenceOutputMappings() {
		return (List<ReferenceOutputMapping>) deployManager
				.getReferenceOutputMappings();
	}

	public ProcessDescriptionType initializeDescription() {
		// TODO use generate method from transactionalrequesthandler
		String fullPath = GenericTransactionalAlgorithm.class
				.getProtectionDomain().getCodeSource().getLocation().toString();
		int searchIndex = fullPath.indexOf("WEB-INF");
		String subPath = fullPath.substring(0, searchIndex);
		String processID = getAlgorithmID();
		// sanitize processID: strip version number and namespace if passed in
		if (processID.contains("-")) {
			processID = processID.split("-")[0];
		}
		if (processID.contains("}")) {
			processID = processID.split("}")[1];
		}
		try {
			URI fileUri = new URL(subPath + "WEB-INF" + File.separator
					+ "ProcessDescriptions" + File.separator + processID
					+ ".xml").toURI();
			File xmlDesc = new File(fileUri);
			XmlOptions options = new XmlOptions();
			options.setLoadTrimTextBuffer();
			ProcessDescriptionType doc = ProcessDescriptionType.Factory.parse(
					xmlDesc, options);
			// ProcessDescriptionsDocument doc =
			// ProcessDescriptionsDocument.Factory.parse(xmlDesc, option);
			// if
			// (doc.getProcessDescriptions().getProcessDescriptionArray().length
			// == 0) {
			// LOGGER.warn("ProcessDescription does not contain any description");
			// return null;
			// }
			// doc.getIdentifier().setStringValue(processID);

			return doc;

		} catch (IOException e) {
			LOGGER.warn("Could not initialize algorithm, parsing error: "
					+ getAlgorithmID(), e);
		} catch (XmlException e) {
			LOGGER.warn("Could not initialize algorithm, parsing error: "
					+ getAlgorithmID(), e);
		} catch (URISyntaxException e) {
			LOGGER.warn("Could not initialize algorithm, parsing error: "
					+ getAlgorithmID(), e);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.n52.wps.server.AbstractTransactionalAlgorithm#runProfiling(net.opengis
	 * .wps.x100.ExecuteDocument, java.util.List)
	 */
	@Override
	public Map<String, IData> runProfiling(ExecuteDocument document,
			List<Observer> observers) {
		try {
			// FIXME switch to app-wide constant.
			deployManager = TransactionalHelper
					.getProcessManagerForSchema("rola");
			return deployManager.invokeProfiling(document, getAlgorithmID(),
					observers);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
