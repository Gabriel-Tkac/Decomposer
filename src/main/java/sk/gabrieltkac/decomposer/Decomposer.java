package sk.gabrieltkac.decomposer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.codec.binary.Base64;

import sk.gabrieltkac.decomposer.model.Container;
import sk.gabrieltkac.decomposer.model.ContainerType;
import sk.gabrieltkac.decomposer.utils.Utils;

public class Decomposer {
	
	public static void main(String[] args) {
		Container container;	
		try {
			
			container = getDocument(" --- FILEPATH GOES HERE --- ");
					
			
			
			if (container.getSignatures() != null)
				for (int i = 0; i < container.getSignatures().size(); i++) {
					for (int j = 0; j < container.getSignatures().get(i).getDocuments().size(); j++) {
						System.out.println("---------- Document No. " + j + " -----------");
						System.out.println("dokument typ: \t\t" + container.getContainerType().name());
						System.out.println("dokument: \t\t" + container.getSignatures().get(i).getDocuments().get(j).getContent().substring(0, 100));
						System.out.println("mimeType: \t\t" + container.getSignatures().get(i).getDocuments().get(j).getMIMEType());
						System.out.println("pripona: \t\t" + container.getSignatures().get(i).getDocuments().get(j).getExtension());
						System.out.println("datumPodpisu: \t\t" + container.getSignatures().get(i).getSignatureTimeUTC());
						System.out.println("datumPodpisuString: \t" + container.getSignatures().get(i).getSignatureTime());
						
					}
					System.out.println("datum CP: \t\t" + container.getSignatures().get(i).getTimestamp());
					System.out.println("datum CPString: \t" + container.getSignatures().get(i).getSignatureTimeUTC());
					System.out.println("certifikat CP: \t\t" + container.getSignatures().get(i).getTimestampCertificate().getHolderId());
					System.out.println("certifikat podpisu: \t" + container.getSignatures().get(i).getSignatureCertificate().getHolderId());
					
				}
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Creates Container instance
	 * @param base64 - Base64 String document content
	 * @param extension - file extension
	 * @return Container instance
	 * @throws Exception
	 */
	public static Container getDocument(String base64, String extension) throws Exception {
		byte[] decodedBytes = Base64.decodeBase64(new String(base64.getBytes(), "UTF-8"));
		InputStream is = new ByteArrayInputStream(decodedBytes);
		return getDocumentByType(is, extension, false);
	}
	
	/**
	 * Creates Container instance based on file path
	 * @param fileName - file path
	 * @return Container instance
	 * @throws Exception
	 */
	public static Container getDocument(String fileName) throws Exception {
		File file = new File(fileName);
		InputStream is = new FileInputStream(file);
		return getDocumentByType(is, "", false);
	}
	
	/**
	 * Creates Container instance<br>
	 * If extension is given, uses it to decide on container type<br>
	 * Otherwise tries to resolve type by content<br>
	 * If no signatures are present, returns null
	 * @param is - input stream with container content
	 * @param extension - input file extension
	 * @param withoutTimeStamp - if true, deletes timestamps from container
	 * @return Container instance
	 * @throws Exception if file is not a sign container (e. g.  PAdES, XAdES, CAdES, ASICx) 
	 */
	private static Container getDocumentByType(InputStream is, String extension, boolean withoutTimeStamp) throws Exception {
		Container container = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		try {
			while ((len = is.read(buffer)) > -1) {
				baos.write(buffer, 0, len);
			}
			baos.flush();

			if (!extension.isEmpty()) {
				switch (extension.toLowerCase()) {
				case "pdf":
					container = PAdES.getDocument(baos.toByteArray());
					break;
				case "xzep":
				case "zepx":
					container = XAdES.getXAdESContainer(baos.toByteArray());
					break;
				case "zep":
				case "p7s":
				case "p7t":
					container = CAdES.getDocument(new ByteArrayInputStream(baos.toByteArray()));
					break;
				case "asice":
				case "asics":
				case "sce":
				case "scs":
					container = ASiC.getDocument(new ByteArrayInputStream(baos.toByteArray()));
					break;
				default:
					throw new Exception("Unsupported file type.");
				}
			} 
			else {
				ContainerType type = Utils.getContainerType(baos.toByteArray());
				System.out.println(type);
				switch (type) {
				case PAdES:
					container = PAdES.getDocument(baos.toByteArray());
					break;
				case XAdES:
					container = XAdES.getXAdESContainer(baos.toByteArray());
					break;
				case CAdES:
					container = CAdES.getDocument(new ByteArrayInputStream(baos.toByteArray()));
					break;
				case ASiCe_XAdES:
					container = ASiC.getDocument(new ByteArrayInputStream(baos.toByteArray()));
					break;
				default:
					throw new Exception("Nepodporovany typ suboru.");
				}
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e.getMessage());						
		} 
		finally {
			if (container != null)
				if (container.getSignatures() == null || container.getSignatures().isEmpty())
					container = null;
			try {
				baos.close();
				is.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		if(withoutTimeStamp && container != null)
			container.deleteTimeStamps();
		return container;
	}

}
