package sk.gabrieltkac.decomposer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import sk.gabrieltkac.decomposer.model.Container;
import sk.gabrieltkac.decomposer.model.ContainerType;
import sk.gabrieltkac.decomposer.model.DataObject;
import sk.gabrieltkac.decomposer.model.Document;
import sk.gabrieltkac.decomposer.model.Signature;
import sk.gabrieltkac.decomposer.utils.Constants;

public class CAdES {
	
	public static Container getCAdESContainer(FileInputStream fis) {
		Container container = new Container();
		container.setContainerType(ContainerType.CAdES);
		
		List<Document> documents = new ArrayList<>();
		List<Signature> signatures = new ArrayList<>();
		
		List<DataObject> sigs = new ArrayList<>();
		List<DataObject> docs = new ArrayList<>();
		DataObject dataObject;
		
		ZipInputStream zis = new ZipInputStream(fis);
		ZipEntry zipEntry;
		
		try {
			zipEntry = zis.getNextEntry();
			byte[] byteArr = null;
			while (zipEntry != null) {
				int count;
				byte[] data = new byte[Constants.BUFFER_SIZE];
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

				if (!zipEntry.isDirectory()) {
					String fileName = zipEntry.getName();
					BufferedOutputStream out = new BufferedOutputStream(outputStream, Constants.BUFFER_SIZE);
					while ((count = zis.read(data, 0, data.length)) != -1) {
						out.write(data, 0, count);
					}
					out.flush();
					byteArr = outputStream.toByteArray();
					out.close();
					int lastIndex = fileName.lastIndexOf(".");
					int lastIndexOfSlash = 0;
					if (fileName.contains("/"))
						lastIndexOfSlash = fileName.lastIndexOf("/") + 1;
					if (fileName.contains("\\"))
						lastIndexOfSlash = fileName.lastIndexOf("\\") + 1;
					
						if (!fileName.contains(Constants.FOLDER_FOR_POLICY) &&
							!fileName.substring(lastIndexOfSlash).toUpperCase().startsWith("S")) {
							
							String extension = lastIndex == -1 ? "" : fileName.substring(lastIndex + 1); 
							dataObject = new DataObject(fileName, byteArr, extension);
							docs.add(dataObject);
						} 
						else if (fileName.substring(lastIndexOfSlash).toUpperCase().startsWith("S")) {
							String extension = lastIndex == -1 ? "" : fileName.substring(lastIndex + 1); 
							dataObject = new DataObject(null, byteArr, extension);
							sigs.add(dataObject);
						}
						zipEntry = zis.getNextEntry();
				}
			}
			for (DataObject d : docs) {
				documents.addAll(getDocuments(d.getData(), d.getFilename(), d.getExtension()));
			}

			for (DataObject s : sigs) {
				signatures.addAll(getSignatures(s.getData(), documents));
			}

		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
		} 
		finally {
			try {
				zis.closeEntry();
				zis.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		container.setId(java.util.UUID.randomUUID().toString());
		container.setSignatures(signatures);
		container.setDocuments(documents);
		
		return container;
	}
	
	private List<Signature> getSignatures(byte[] is, List<Document> documents) {
		List<Signature> signatures = new ArrayList<Signature>();
		try {
			signatures = CadesHelper.getSignatures(is, documents);
		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
		}
		return signatures;
	}

}
