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
	 * Vytvori a vrati instanciu Container, reprezentujucu podpisovy kontajner typu ASiCE 
	 * @param is - vstupny prud s obsahom podpisoveho kontajnera
	 * @return instancia Container, reprezentujuca ASiCE kontajner
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
			throw new Exception("Nastala chyba pri rozoberani dokumentu Asice.");
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
	 * Z poloziek ZIP archivu (ASiCE suboru) vytvori instancie typu Document a vyplni im ID, nazov, MIME Type a obsah
	 * Ak ide o XML dokument, pouzije jeho priamu strukturu, inak obsah zakoduje ako Base64 
	 * @param decodedBytes - obsah ZIP polozky v podobe pola bytov
	 * @param filename - nazov polozky, ktory sa pouzije ako nazov dokumentu
	 * @param extension - pripona dokumentu
	 * @return instancia docsplit.model.document.Document
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
			throw new Exception("Nastala chyba pri spracovani dokumentov.", e);
		}
		if (document.getMIMEType()==null && (extension.equals("asice") || extension.equals("sce")))
			document.setMIMEType(Constants.MIME_TYPE_ASICE);
		if (document.getMIMEType()==null && (extension.equals("asics") || extension.equals("scs")))
			document.setMIMEType(Constants.MIME_TYPE_ASICS);
		
		return document;
	}

	/**
	 * Vytvori a vrati podpisy z polozky ZIP archivu (ASiCE suboru) s priponou xml/p7s
	 * Ak je pripona xml, ide o kontajner typu ASICe-XAdES, inak ASICe-CAdES
	 * @param decodedBytes - obsah ZIP polozky s podpismi v podobe pola bytov
	 * @param documents - vstupne pole dokumentov, nacitanych z podpisoveho kontajnera
	 * @param extension - pripona polozky archivu s podpismi
	 * @return zoznam podpisov
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
				throw new Exception("Nastala chyba pri spracovani podpisov xades.", e);
				
			}
		}
		if ("p7s".equals(extension)) {
			container.setContainerType(ContainerType.ASiCe_CAdEs);;
			try {
				signatures = CAdES.getSignatures(decodedBytes, documents);
			} 
			catch (Exception e) {
				throw new Exception("Nastala chyba pri spracovani podpisov cades.", e);
			}
		}
		return signatures;
	}

}
