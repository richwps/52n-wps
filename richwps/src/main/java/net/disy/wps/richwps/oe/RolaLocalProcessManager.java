package net.disy.wps.richwps.oe;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observer;

import net.disy.wps.richwps.oe.processor.IWorkflowProcessor;
import net.disy.wps.richwps.oe.processor.WorkflowProcessor;
import net.disy.wps.richwps.response.TestingOutputs;
import net.opengis.wps.x100.ExecuteDocument;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.n52.wps.io.data.IData;
import org.n52.wps.server.ITransactionalAlgorithmRepository;
import org.n52.wps.transactional.deploy.AbstractProcessManager;
import org.n52.wps.transactional.request.DeployProcessRequest;
import org.n52.wps.transactional.request.UndeployProcessRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hsos.richwps.dsl.api.Reader;
import de.hsos.richwps.dsl.api.elements.ReferenceOutputMapping;
import de.hsos.richwps.dsl.api.elements.Workflow;

public class RolaLocalProcessManager extends AbstractProcessManager {

	private static Logger LOGGER = LoggerFactory.getLogger(RolaLocalProcessManager.class);
	private static String EXT = "dsl";

	public RolaLocalProcessManager(ITransactionalAlgorithmRepository parentRepository) {
		super(parentRepository);
	}

	@Override
	public boolean unDeployProcess(UndeployProcessRequest request) throws Exception {
		String processId = request.getProcessID();
		if (!request.getKeepExecutionUnit()) {
			deleteRola(processId);
		} else {
			toggleArchiveExecutionUnit(processId);
		}
		return true;
	}

	@Override
	public boolean containsProcess(String processID) throws Exception {
		URI rolaDirectory = getRolaDirectory();
		File directory = new File(rolaDirectory);
		Collection<File> files = FileUtils.listFiles(directory, new String[] { EXT }, false);
		for (File file : files) {
			String baseName = FilenameUtils.getBaseName(file.getAbsolutePath());
			if (baseName.equals(processID)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Collection<String> getAllProcesses() throws Exception {
		return getAllProcessesFromWdDirectory();
	}

	private Collection<String> getAllProcessesFromWdDirectory() {
		final Collection<String> processIds = new ArrayList<String>();
		URI rolaDirectory = getRolaDirectory();
		File directory = new File(rolaDirectory);
		Collection<File> files = FileUtils.listFiles(directory, new String[] { EXT }, false);
		for (File file : files) {
			processIds.add(FilenameUtils.getBaseName(file.getAbsolutePath()));
		}
		return processIds;
	}

	@Override
	public Map<String, IData> invoke(ExecuteDocument payload, String algorithmID) throws Exception {
		Workflow worksequence = getWorksequenceById(algorithmID);
		IWorkflowProcessor worksequenceProcessor = new WorkflowProcessor();
		return worksequenceProcessor.process(payload, worksequence);
	}

	@Override
	public TestingOutputs invokeTest(ExecuteDocument document, String algorithmID) throws Exception {
		Workflow workflow = getWorksequenceById(algorithmID);
		IWorkflowProcessor workflowProcessor = new WorkflowProcessor();
		Map<String, IData> outputData = workflowProcessor.process(document, workflow);
		Map<String, IData> variables = workflowProcessor.getProcessingContext().getVariables();
		Iterator<Entry<String, IData>> outputReferencesIterator = variables.entrySet().iterator();
		Map<String, IData> outputReferencesExtended = new HashMap<String, IData>();
		List<String> outputReferenceNames = new ArrayList<String>();

		while (outputReferencesIterator.hasNext()) {
			Entry<String, IData> currentEntry = outputReferencesIterator.next();

			outputReferenceNames.add(currentEntry.getKey());
			outputReferencesExtended.put("var." + currentEntry.getKey(), currentEntry.getValue());
		}

		Iterator<Entry<String, IData>> outputReferencesExtendedIterator = outputReferencesExtended
				.entrySet().iterator();

		while (outputReferencesExtendedIterator.hasNext()) {
			Entry<String, IData> currentEntry = outputReferencesExtendedIterator.next();
			outputData.put((String) currentEntry.getKey(), (IData) currentEntry.getValue());
		}

		List<ReferenceOutputMapping> referenceOutputMappings = workflow
				.getReferenceOutputMappings(outputReferenceNames);

		return new TestingOutputs(outputData, referenceOutputMappings);
	}

	@Override
	public Map<String, IData> invokeProfiling(ExecuteDocument payload, String algorithmID,
			List<Observer> observers) throws Exception {
		Workflow workflow = getWorksequenceById(algorithmID);
		IWorkflowProcessor workflowProcessor = new WorkflowProcessor(observers);
		Map<String, IData> outputs = workflowProcessor.profileProcess(payload, workflow);

		return outputs;
	}

	private Workflow getWorksequenceById(String algorithmID) throws Exception {
		URI fileUri = buildRolaFileUri(algorithmID);
		File wdFile = new File(fileUri);
		Reader dslReader = new Reader();
		dslReader.load(wdFile.getAbsolutePath());
		dslReader.inspect();
		return dslReader.getWorksequence();
	}

	@Override
	public boolean deployProcess(DeployProcessRequest request) throws Exception {
		saveWorksequenceDescription(request.getProcessId(), request.getExecutionUnit());
		return true;
	}

	private File saveWorksequenceDescription(String processId, String worksequenceDescription) {
		File wdFile = null;
		try {
			URI fileUri = buildRolaFileUri(processId);
			wdFile = new File(fileUri);
			BufferedWriter writer = new BufferedWriter(new FileWriter(wdFile));
			writer.write(worksequenceDescription);
			writer.close();
			return wdFile;
		} catch (IOException e) {
			throw new RuntimeException("Could not save rola file "
					+ (wdFile != null ? wdFile.getAbsolutePath() : ""));
		}
	}

	private void toggleArchiveExecutionUnit(String processId) {
		URI fileUri = buildRolaFileUri(processId);
		File file = new File(fileUri);
		URI afileUri = buildArchivedRolaFileUri(processId);
		File afile = new File(afileUri);
		if (file.exists()) {
			file.renameTo(afile);
		} else {
			if (afile.exists()) {
				file.renameTo(file);
			}
		}
	}

	private void deleteRola(String processId) {
		URI fileUri = buildRolaFileUri(processId);
		File file = new File(fileUri);
		if (file.exists()) {
			file.delete();
		}
	}

	private URI buildRolaFileUri(String processId) {
		try {
			return new URI(getRolaDirectory().toString() + processId + "." + EXT);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private URI buildArchivedRolaFileUri(String processId) {
		try {
			return new URI(getRolaDirectory().toString() + processId + ".arola");
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private URI getRolaDirectory() {
		String fullPath = getClass().getProtectionDomain().getCodeSource().getLocation().toString();
		int searchIndex = fullPath.indexOf("WEB-INF");
		String subPath = fullPath.substring(0, searchIndex);

		URI directoryUri;
		try {
			directoryUri = new URL(subPath + "WEB-INF/ExecutionUnits/").toURI();
			File directory = new File(directoryUri);
			if (!directory.exists()) {
				directory.mkdirs();
			}

			return directoryUri;

		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
