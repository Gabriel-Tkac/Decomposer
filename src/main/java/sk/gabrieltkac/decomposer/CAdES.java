package sk.gabrieltkac.decomposer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Selector;
import org.bouncycastle.util.Store;

import sk.gabrieltkac.decomposer.model.Certificate;
import sk.gabrieltkac.decomposer.model.Container;
import sk.gabrieltkac.decomposer.model.ContainerType;
import sk.gabrieltkac.decomposer.model.DataObject;
import sk.gabrieltkac.decomposer.model.Document;
import sk.gabrieltkac.decomposer.model.Signature;
import sk.gabrieltkac.decomposer.utils.Constants;
import sk.gabrieltkac.decomposer.utils.Utils;

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
	
	/**
	 * Loads documents from input byte aray<br>
	 * @param is - input byte array
	 * @param filename - input filename
	 * @param extension - input extension
	 * @return document list
	 * @throws Exception
	 */
	private static List<Document> getDocuments(byte[] is, String filename, String extension) throws Exception {
		List<Document> documents = new ArrayList<Document>();
		if ("eml".equals(extension)) {
			MimeMessage message = new MimeMessage(null, new ByteArrayInputStream(is));
			if (message.getContent() instanceof Multipart) {
				Multipart mp = (Multipart) message.getContent();
				int count = mp.getCount();
				for (int i = 0; i < count; i++) {
					Document document = new Document();
					BodyPart bp = mp.getBodyPart(i);
					bp.getAllHeaders();
					String encoding = "text";
					if (bp.getHeader("Content-Transfer-Encoding") != null) {
						encoding = bp.getHeader("Content-Transfer-Encoding")[0];
					}
					String nazov = "";
					if (bp.getHeader("Content-Disposition") != null) {
						String pom = bp.getHeader("Content-Disposition")[0];
						nazov = Utils.checkFileNameFromEmail(pom.substring(pom.indexOf("\"") + 1, pom.lastIndexOf("\"")));
					}
					if (nazov.isEmpty()) {
						nazov = "";
					}
					if (message.getHeader("Content-Type") != null) {
						document.setExtension(Utils.parseExtension(message.getHeader("Content-Type")[0]));
						document.setMIMEType(message.getHeader("Content-Type")[0]);
					}
					int lastIndex = nazov.lastIndexOf(".");
					document.setId(java.util.UUID.randomUUID().toString());
					document.setName(lastIndex == -1 ? nazov : nazov.substring(0, lastIndex));
					if (encoding.equals("base64")) {
						byte[] mimeContentByte = new byte[bp.getSize()];
						((InputStream) bp.getContent()).read(mimeContentByte);
						document.setContent(new String(mimeContentByte));
					} 
					else {
						byte[] mimeContentByte = new byte[bp.getSize()];
						bp.getInputStream().read(mimeContentByte);
						document.setContent(java.util.Base64.getEncoder().encodeToString(mimeContentByte));
					}
					documents.add(document);
				}
			} 
			else {
				Document document = new Document();
				message.getAllHeaderLines();
				String encoding = message.getEncoding();
				String nazov = "";
				if (message.getHeader("Content-Disposition") != null) {
					String pom = message.getHeader("Content-Disposition")[0];
					nazov = Utils.checkFileNameFromEmail(pom.substring(pom.indexOf("\"") + 1, pom.lastIndexOf("\"")));
				}
				if (nazov.isEmpty()) {
					nazov = "";
				}
				if (message.getHeader("Content-Type") != null) {
					document.setExtension(Utils.parseExtension(message.getHeader("Content-Type")[0]));
					document.setMIMEType(message.getHeader("Content-Type")[0]);
				}
				int lastIndex = nazov.lastIndexOf(".");
				document.setId(java.util.UUID.randomUUID().toString());
				document.setName(lastIndex == -1 ? nazov : nazov.substring(0, lastIndex));
				byte[] mimeContentByte = new byte[message.getSize()];
				message.getRawInputStream().read(mimeContentByte);
				if (encoding.equals("base64")) {
					document.setContent(new String(mimeContentByte));
				} 
				else {
					document.setContent(java.util.Base64.getEncoder().encodeToString(mimeContentByte));
				}
				documents.add(document);
			}
		} 
		else {
			Document document = new Document();
			int lastIndex = filename.lastIndexOf(".");
			document.setId(java.util.UUID.randomUUID().toString());
			document.setName(lastIndex == -1 ? filename : filename.substring(0, lastIndex));
			try {
				document.setMIMEType(Utils.detectMime(filename));
				document.setExtension(Utils.parseExtension(extension));
			} 
			catch (Exception e){
				Logging.logException(e, false, Constants.logFilepath);
			}
			document.setContent(java.util.Base64.getEncoder().encodeToString(is));
			documents.add(document);
		}

		return documents;
	}
	
	static List<Signature> getSignatures(byte[] is, List<Document> documents) {
		List<Signature> signatures = new ArrayList<Signature>();
		try {
			signatures = getSignatures(is, documents);
		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
		}
		return signatures;
	}
	
	/**
	 * Creates instance of Container 
	 * @param is - input stream with sign container content
	 * @return docsplit.model.document.Container insance - sign container
	 */
	public static Container getDocument(final InputStream is) {
		Container container = new Container();
		container.setContainerType(ContainerType.CAdES);
		List<Document> documents = new ArrayList<Document>();
		List<Signature> signatures = new ArrayList<Signature>();
		List<DataObject> sigs = new ArrayList<DataObject>();
		List<DataObject> docs = new ArrayList<DataObject>();
		DataObject dataObject;
		ZipInputStream zis = new ZipInputStream(is);
		ZipEntry zipEntry;
		try {
			zipEntry = zis.getNextEntry();
			byte[] isTemp = null;
			while (zipEntry != null) {
				int count;
				byte data[] = new byte[Constants.BUFFER_SIZE];
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

				if (!zipEntry.isDirectory()) {
					String fileName = zipEntry.getName();
					BufferedOutputStream out = new BufferedOutputStream(outputStream, Constants.BUFFER_SIZE);
					while ((count = zis.read(data, 0, data.length)) != -1) {
						out.write(data, 0, count);
					}
					out.flush();
					isTemp = outputStream.toByteArray();
					out.close();
					int lastIndex = fileName.lastIndexOf(".");
					int lastIndexOfPath = 0;
					if (fileName.contains("/"))
						lastIndexOfPath = fileName.lastIndexOf("/") + 1;
					if (fileName.contains("\\"))
						lastIndexOfPath = fileName.lastIndexOf("\\") + 1;
					if (!fileName.contains(Constants.FOLDER_FOR_POLICY)
								&& !fileName.substring(lastIndexOfPath).toUpperCase().startsWith("S")) {
							dataObject = new DataObject(fileName, isTemp,
									lastIndex == -1 ? "" : fileName.substring(lastIndex + 1));
							docs.add(dataObject);
						} 
						else if (fileName.substring(lastIndexOfPath).toUpperCase().startsWith("S")) {
							dataObject = new DataObject(null, isTemp,
									lastIndex == -1 ? "" : fileName.substring(lastIndex + 1));
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
	
	
	
	/**
	 * Creates certificate instance from its content 
	 * @param certB64 - certificate content
	 * @return certificate instance
	 */
	public static Certificate getCertificateFromBase64(String certB64) {
		Certificate certificate = new Certificate();
		byte encodedCert[] = java.util.Base64.getDecoder().decode(certB64.getBytes());
		ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedCert);
		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) certFactory.generateCertificate(inputStream);
			certificate.setPublisher(cert.getIssuerDN().getName());
			certificate.setHolderId(cert.getSubjectDN().getName());
			certificate.setValidFrom(cert.getNotBefore());
			certificate.setValidTo(cert.getNotAfter());
			certificate.setSerialNumber(cert.getSerialNumber().toString(16));
			certificate.setContent(certB64);
		} 
		catch (CertificateException e) {
			Logging.logException(e, false, Constants.logFilepath);
		}
		return certificate;
	}
	
	/**
	 * Creates Timestamp certificate
	 * @param timestampString - byte array certificate content
	 * @return certificate instance
	 */
	public static Certificate createTimeStampFromString(String timestampString) {
		Certificate certificate = new Certificate();
		TimeStampToken tst;
		if (timestampString == null || "".equals(timestampString)) {
			return null;
		} 
		else {
			try {
				tst = new TimeStampToken(new CMSSignedData(java.util.Base64.getDecoder().decode(timestampString)));
				X509CertificateHolder ch = getTimeStampCert(tst);
				certificate.setPublisher(ch.getIssuer().toString());
				certificate.setSerialNumber(ch.getSerialNumber().toString());
				
				org.bouncycastle.asn1.x509.Certificate c = ch.toASN1Structure();
				byte[] pole = c.getEncoded();
				String encodedPole = java.util.Base64.getEncoder().encodeToString(pole);
				certificate.setContent(encodedPole);
			} 
			catch (Exception r) {
				Logging.logException(r, false, Constants.logFilepath);
			}
		}
		return certificate;
	}
	
	/**
	 * Creates org.bouncycastle.cert.X509CertificateHolder instance by serialnumber
	 * @param tsToken - TimeStampToken
	 * @return org.bouncycastle.cert.X509CertificateHolder instance
	 */
	public static X509CertificateHolder getTimeStampCert(TimeStampToken tsToken) {
		X509CertificateHolder signerCert = null;
		if (tsToken != null) {
			@SuppressWarnings("rawtypes")
			Store certsStore = tsToken.getCertificates();
			@SuppressWarnings({ "rawtypes", "unchecked" })
			ArrayList certs = new ArrayList(certsStore.getMatches((Selector) null));
			@SuppressWarnings("rawtypes")
			Iterator i = certs.iterator();
			while (i.hasNext()) {
				X509CertificateHolder cert = (X509CertificateHolder) i.next();
				String cerIssuerName = cert.getIssuer().toString();
				String signerIssuerName = tsToken.getSID().getIssuer().toString();
				if (cerIssuerName.equals(signerIssuerName)
						&& cert.getSerialNumber().equals(tsToken.getSID().getSerialNumber())) {
					signerCert = cert;
					break;
				}
			}
		}
		return signerCert;
	}
	
	/**
	 * Parses time value from TS certificate string
	 * @param timestampString - TS certificate string
	 * @return date
	 */
	public static Date getDateTimeStampFromString(String timestampString) {
		Date date = null;
		TimeStampToken tst = null;
		if (!timestampString.isEmpty()) {
			try {
				tst = new TimeStampToken(new CMSSignedData(java.util.Base64.getDecoder().decode(timestampString)));
				date = tst.getTimeStampInfo().getGenTime();
			} 
			catch (Exception r) {
				Logging.logException(r, false, Constants.logFilepath);
			}
		}
		return date;
	}
	
	/**
	 * Creates certificate instance
	 * @param X509Cert - java.security.cert.X509Certificate instance
	 * @return docsplit.model.document.Certificate instance
	 */
	public static Certificate getCertificate(X509Certificate X509Cert) {
		Certificate certificate = new Certificate();
		try {
			certificate.setPublisher(X509Cert.getIssuerDN().getName());
			certificate.setHolderId(X509Cert.getSubjectDN().getName());
			certificate.setValidFrom(X509Cert.getNotBefore());
			certificate.setValidTo(X509Cert.getNotAfter());
			certificate.setSerialNumber(X509Cert.getSerialNumber().toString(16));
			certificate.setContent(java.util.Base64.getEncoder().encodeToString(X509Cert.getEncoded()));
		} 
		catch (CertificateEncodingException e) {
			Logging.logException(e, false, Constants.logFilepath);
		}
		return certificate;
	}
	
	public static Collection<X509Certificate> getSignersCertificates(CMSSignedData previewSignerData) {
		Collection<X509Certificate> result = new HashSet<X509Certificate>();
		Store<?> certStore = previewSignerData.getCertificates();
		SignerInformationStore signers = previewSignerData.getSignerInfos();
		Iterator<?> it = signers.getSigners().iterator();
		while (it.hasNext()) {
			SignerInformation signer = (SignerInformation) it.next();
			@SuppressWarnings("unchecked")
			Collection<?> certCollection = certStore.getMatches(signer.getSID());
			Iterator<?> certIt = certCollection.iterator();
			X509CertificateHolder certificateHolder = (X509CertificateHolder) certIt.next();
			try {
				result.add(new JcaX509CertificateConverter().getCertificate(certificateHolder));
			} 
			catch (CertificateException error) {
			}
		}
		return result;
	}

}
