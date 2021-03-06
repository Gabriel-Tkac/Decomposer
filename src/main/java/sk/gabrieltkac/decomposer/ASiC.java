package sk.gabrieltkac.decomposer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import sk.gabrieltkac.decomposer.model.Container;
import sk.gabrieltkac.decomposer.model.ContainerType;
import sk.gabrieltkac.decomposer.model.Document;
import sk.gabrieltkac.decomposer.model.DataObject;
import sk.gabrieltkac.decomposer.model.Signature;
import sk.gabrieltkac.decomposer.utils.Constants;
import sk.gabrieltkac.decomposer.utils.Utils;

public class ASiC {
	
	/**
	 * Creates Container instance for ASiCE document 
	 * @param is - input stream - ASiC content
	 * @return Container instance
	 * @throws Exception 
	 * @throws DocSplitterException
	 */
	public static Container getDocument(final InputStream is) throws Exception {
		Container container = new Container();
		List<Document> documents = new ArrayList<Document>();
		List<Signature> signatures = new ArrayList<Signature>();
		List<DataObject> sigs = new ArrayList<DataObject>();
		List<DataObject> docs = new ArrayList<DataObject>();
		DataObject dataObject;
		String asiceManifest = "";
		
		ZipInputStream zis = new ZipInputStream(is);
		ZipEntry zipEntry;
		try {
			zipEntry = zis.getNextEntry();
			byte[] isTemp = null;
			
			while (zipEntry != null) {
				
				if (!zipEntry.isDirectory()) {
					
					int count;
					byte data[] = new byte[Constants.BUFFER_SIZE];
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					
					String fileName = zipEntry.getName();
					BufferedOutputStream out = new BufferedOutputStream(outputStream, Constants.BUFFER_SIZE);
					while ((count = zis.read(data, 0, data.length)) != -1) {
						out.write(data, 0, count);
					}
					out.flush();
					isTemp = outputStream.toByteArray();
					out.close();
					int lastIndex = fileName.lastIndexOf(".");
					if (fileName.contains(Constants.FOLDER_FOR_DOCUMENTS) && fileName.contains(Constants.FOLDER_FOR_SIGNATURES)) {
						dataObject = new DataObject(null, isTemp, lastIndex == -1 ? "" : fileName.substring(lastIndex + 1));
						sigs.add(dataObject);
					}
					if (fileName.contains(Constants.FOLDER_FOR_DOCUMENTS) && (fileName.contains(Constants.FOLDER_FOR_ASIC_MANIFEST) || fileName.contains(Constants.FOLDER_FOR_ASIC_MANIFEST01))) {
						asiceManifest = new String(isTemp);
					}
					if (!fileName.contains(Constants.FOLDER_FOR_DOCUMENTS) && !fileName.contains(Constants.MIME_TYPE_FILE)) {
						dataObject = new DataObject(fileName, isTemp, lastIndex == -1 ? "" : fileName.substring(lastIndex + 1));
						docs.add(dataObject);
					}
				}
				zipEntry = zis.getNextEntry();
			}

			for (DataObject d : docs) {
				documents.add(getDocument(d.getData(), d.getFilename(), d.getExtension()));
			}

			for (DataObject s : sigs) {
				signatures.addAll(getSignatures(container, s.getData(), documents, s.getExtension()));
			}

			if (!asiceManifest.isEmpty()) {
				XAdES.setMimeType(documents, asiceManifest);
			}

		} 
		catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Error processing ASiC container.");
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

	/**
	 * Reads electronic documents form .zip structure
	 * @param decodedBytes - byte array (zip content)
	 * @param filename - will be used as document name
	 * @param extension - document extension
	 * @return docsplit.model.document.Document instance
	 * @throws Exception
	 */
	public static Document getDocument(byte[] decodedBytes, String filename, String extension) throws Exception {
		Document document = new Document();
		document.setId(filename);
		document.setName(filename);
		try {
			if(extension.length() > 0)
				document.setExtension(extension);
			document.setMIMEType(Utils.detectMime(filename));
		}
		catch (Exception e){
			Logging.logException(e, false, Constants.logFilepath);
		}
		
		String textBase64 = new String(decodedBytes);
		try {
			if (textBase64.contains(Constants.XML_CONTAINER)) {
				String xml = Utils.getValueByToken(Constants.DATA_FROM_DATA_CONTAINER, decodedBytes, true, null);
				document.setContent(Base64.encodeBase64String(xml.getBytes()));
				document.setOriginalContent(textBase64);
				document.setMIMEType(Constants.MIME_TYPE_FORM);
			} 
			else {
				document.setContent(Base64.encodeBase64String(decodedBytes));
			}
		} 
		catch (IOException e) {
			throw new Exception("Error processing ASiC documents.", e);
		}
		if (document.getMIMEType()==null && (extension.equals("asice") || extension.equals("sce")))
			document.setMIMEType(Constants.MIME_TYPE_ASICE);
		if (document.getMIMEType()==null && (extension.equals("asics") || extension.equals("scs")))
			document.setMIMEType(Constants.MIME_TYPE_ASICS);
		
		return document;
	}

	/**
	 * Creates Signatures from ASiC file
	 * @param decodedBytes - byte array (container content)
	 * @param documents - Documents array
	 * @param extension - archive extension
	 * @return Signatures list
	 * @throws Exception 
	 */
	private static List<Signature> getSignatures(Container container, byte[] decodedBytes, List<Document> documents, String extension) throws Exception {
		List<Signature> signatures = new ArrayList<Signature>();
		if ("xml".equals(extension)) {
			container.setContainerType(ContainerType.ASiCe_XAdES);
			DocumentBuilderFactory dbf;
			DocumentBuilder db;
			InputSource source;
			org.w3c.dom.Document domDocument = null;
			dbf = DocumentBuilderFactory.newInstance();
			try {
				db = dbf.newDocumentBuilder();
				source = new InputSource(new ByteArrayInputStream(decodedBytes));
				domDocument = db.parse(source);
				domDocument.getDocumentElement().normalize();
				NodeList signaturesList = domDocument.getDocumentElement().getElementsByTagName("ds:Signature");
				signatures = XAdES.getSignatures(signaturesList, documents, signatures);
			} 
			catch (Exception e) {
				throw new Exception("Error processing XAdES signatures", e);
				
			}
		}
		if ("p7s".equals(extension)) {
			container.setContainerType(ContainerType.ASiCe_CAdEs);;
			try {
				signatures = CAdES.getSignatures(decodedBytes, documents);
			} 
			catch (Exception e) {
				throw new Exception("Error processing CAdES signatures", e);
			}
		}
		return signatures;
	}

}
