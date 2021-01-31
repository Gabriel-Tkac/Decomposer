package sk.gabrieltkac.decomposer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Selector;
import org.bouncycastle.util.Store;

import sk.gabrieltkac.decomposer.model.Certificate;
import sk.gabrieltkac.decomposer.model.Container;
import sk.gabrieltkac.decomposer.model.ContainerType;
import sk.gabrieltkac.decomposer.model.Document;
import sk.gabrieltkac.decomposer.model.Signature;
import sk.gabrieltkac.decomposer.utils.Constants;
import sk.gabrieltkac.decomposer.utils.Utils;

public class PAdES {
	
	/**
	 * Using org.apache.pdfbox, reads PDF document properties<br>
	 * Processes electronic signatures and signing certificates and their properties
	 * @param data - byte array - PDF document content
	 * @return Container instance
	 * @throws Exception 
	 * @throws DocSplitterException
	 */
	public static Container getDocument(byte[] data) throws Exception {
		Container container = new Container();
		container.setContainerType(ContainerType.PAdES);
		Document document = null;
		Signature signature = null;
		PDDocument documentPDF = null;
		List<Document> documents = new ArrayList<Document>();
		List<Signature> signatures = new ArrayList<Signature>();

		try {
			documentPDF = getDocumentPDF(data);
			if (documentPDF != null) {
				List<PDSignature> pdSignatures;
				try {
					pdSignatures = documentPDF.getSignatureDictionaries();
					int i = 0;
					for (PDSignature pdSignature : pdSignatures) {
						i++;
						signature = makeSignature(pdSignature, data, i);
						if (signature != null) {
							signatures.add(signature);
						}
						container.setId(java.util.UUID.randomUUID().toString());
						container.setSignatures(signatures);
					}
					document = getDocumentFromPDF(documentPDF, 0);
					if (document != null) {
						documents.add(document);
						container.setDocuments(documents);
					}
				} 
				catch (Exception e) {
					e.printStackTrace();
					throw new Exception("Error processing PDF document.", e);
				}
			}
		}
		catch (Exception e) {
			throw new Exception("Error processing PDF document.", e);
		}

		finally {
			try {
				documentPDF.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		return container;
	}
	
	/**
	 * Removes electronic signatures from PDF document
	 * @param documentPDF - input PDF document
	 * @param signPDF - last electronic signature to remove
	 * @throws Exception 
	 */
	static void removeSignField(PDDocument documentPDF, PDSignature signPDF) throws Exception {
		if (signPDF == null) {
			throw new Exception("removeSignField: podpis je prazdny.");
		}
		Calendar signDate = signPDF.getSignDate();
		if (signDate != null) {
		}
		else {
			return;
		}
	    PDDocumentCatalog documentCatalog = documentPDF.getDocumentCatalog();
	    PDAcroForm acroForm = documentCatalog.getAcroForm();
	    if (acroForm != null) {
			List<PDSignatureField> PDSignFList = documentPDF.getSignatureFields();
			if (PDSignFList != null && PDSignFList.size() > 0) {
		        boolean remove = false;
		    	for (PDSignatureField PDSignF : PDSignFList) {
			        PDSignature PDSign = PDSignF.getSignature();
			        if (PDSign != null && !remove) {
		        		if (PDSign.getCOSObject().equals(signPDF.getCOSObject())) {
			        		remove = true;
			        	}
			        	else {
			        		Calendar signDateCur = PDSign.getSignDate();
			        		if (signDateCur != null) {
			        		}
			        		else if (signDateCur == null || signDateCur.after(signDate) || signDateCur.equals(signDate))
			        			remove = true;
			        	}
			        }
			        if (remove) {
			        	removeField(acroForm, PDSignF);
			        }
		    	}
		        documentCatalog.setAcroForm(acroForm);
			}
			else {
				System.out.println("removeSignField: No SignatureFields defined.");
			}
	    }
	    else {
	        System.out.println("removeSignField: No acroForm defined.");
	    }
	}
	
	/**
	 * Reads and creates Signature object from PDF signature
	 * @param pdSignature - input electronic signature from PDF document
	 * @param data - input byte array to evaluate
	 * @param orderNumber - ordering number of current signature 
	 * @return Signature instance
	 * @throws Exception
	 */
	private static Signature makeSignature(PDSignature pdSignature, byte[] data, int porCislo) throws Exception {
		Signature signature = new Signature();
		COSDictionary sigDict = pdSignature.getCOSObject();
		COSString contents = null;
		COSBase cb = sigDict.getDictionaryObject(COSName.CONTENTS);
		if (cb.getClass() != COSString.class) {
			return null;
		}
		contents = (COSString) cb;
		List<Certificate> certificates = new ArrayList<Certificate>();
		Certificate certificate = null;
		byte[] buf = pdSignature.getSignedContent(data);
		signature.setName("Signature " + porCislo);
		signature.setId("Signature" + porCislo);
		signature.setCertificateHolder(pdSignature.getName());
		System.out.println("Certificate holder name: " + signature.getCertificateHolder());
		Calendar signDate = pdSignature.getSignDate(); 
		if (signDate != null) {
			signature.setSignatureTime(Utils.dateToDateTime(Utils.dateToUTC(signDate)));
			signature.setSignatureTimeUTC(Utils.dateToUTCString(signDate));
			signature.setSignatureTimeHasBeehShifted(true);
		}
		signature.setAssignedPerson(pdSignature.getContactInfo());
		String subFilter = pdSignature.getSubFilter();
		if (subFilter != null) {
			switch (subFilter) {
				case "adbe.pkcs7.detached":
				case "ETSI.CAdES.detached":
					System.out.println("(1) adbe.pkcs7.detached or ETSI.CAdES.detached");
					getSignatureCertificates(buf, contents, signature);
					break;
				case "adbe.pkcs7.sha1": {
					System.out.println("(2) adbe.pkcs7.sha1");
					byte[] hash = MessageDigest.getInstance("SHA1").digest(buf);
					certificate = verifyPKCS7(hash, contents, pdSignature);
					signature.setSignatureCertificate(certificate);
					certificates.add(certificate);
					break;
				}
				case "adbe.x509.rsa_sha1": {
					System.out.println("(3) adbe.x509.rsa_sha1");
					COSString certString = (COSString) sigDict.getDictionaryObject("Cert");
					
					byte[] certData = certString.getBytes();
					CertificateFactory factory = CertificateFactory.getInstance("X.509");
					ByteArrayInputStream certStream = new ByteArrayInputStream(certData);
					Collection<? extends java.security.cert.Certificate> certs = factory
							.generateCertificates(certStream);
					for (java.security.cert.Certificate cert : certs) {
						certificate = CAdES.getCertificate((X509Certificate) cert);
					}
					signature.setSignatureCertificate(certificate);
					certificates.add(certificate);
					break;
				}
				case "ETSI.RFC3161":
					System.out.println("(4) ETSI.RFC3161");
				break;
				default:
					;
					break;
			}
			List<Document> documentsSigned = new ArrayList<Document>();
			PDDocument PDFtemp = getDocumentPDF(buf);
			if (PDFtemp != null) {
				removeSignField(PDFtemp, pdSignature);
				Document docSigned = getDocumentFromPDF(PDFtemp, porCislo);
				documentsSigned.add(docSigned);
				PDFtemp.close();
			}
			signature.setDocuments(documentsSigned);
		}
		return signature;
	}

	/**
	 * Creates PDF document from byte array
	 * @param data - input byte array
	 * @return PDDocument instance
	 * @throws Exception 
	 */
	private static PDDocument getDocumentPDF(byte[] data) throws Exception {
		PDDocument documentPDF = PDDocument.load(data);
		documentPDF.getClass();
		if (!documentPDF.isEncrypted()) {
			return documentPDF; 
		}
		else {
			throw new Exception("getDocumentPDF: Document is password protected!");
		}
	}
	
	/**
	 * Creates and initializes Document instance based on PDF data
	 * @param documentPDF - input PDF document
	 * @param orderNumber - document order number
	 * @return Document instance
	 * @throws IOException
	 */
	private static Document getDocumentFromPDF(PDDocument documentPDF, int orderNumber) throws IOException {
		Document document = new Document();
		String suffix = "";
		if (orderNumber > 0)
			suffix = " (revision " + orderNumber + ")";
		document.setId(java.util.UUID.randomUUID().toString());
		document.setProcessId(java.util.UUID.randomUUID().toString());
		document.setName("Document.pdf" + suffix);

		if (documentPDF.getDocumentInformation() != null) {
			if (documentPDF.getDocumentInformation().getTitle() != null) {
				if(!"".equals(documentPDF.getDocumentInformation().getTitle())) {
					document.setName(documentPDF.getDocumentInformation().getTitle() + suffix);
				}	
			}
			if (documentPDF.getDocumentInformation().getAuthor() != null) {
				document.setCreatedBy(documentPDF.getDocumentInformation().getAuthor());;
			}
			if (documentPDF.getDocumentInformation().getCreationDate() != null) {
				document.setDate(Utils.dateToDateTime(documentPDF.getDocumentInformation().getCreationDate().getTime()));
			}
		}
		document.setExtension("pdf");
		document.setMIMEType(Constants.MIME_TYPE_PDF);
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		documentPDF.save(byteArrayOutputStream);
		document.setContent(Base64.encodeBase64String(byteArrayOutputStream.toByteArray()));
		byteArrayOutputStream.close();
		return document;
	}

	/**
	 * Reads certificate properties from PDF document, if it is adbe.pkcs7.sha1 - algorithm certificate
	 * @param byteArray - byte array representing signed part of the PDF
	 * @param contents - entity carrying signature properties
	 * @param sig - signature instance
	 * @return - docsplit.model.document.Certificate instance
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Certificate verifyPKCS7(byte[] byteArray, COSString contents, PDSignature sig) throws Exception {
		Certificate certificate;
		CMSProcessable signedContent = new CMSProcessableByteArray(byteArray);
		CMSSignedData signedData = new CMSSignedData(signedContent, contents.getBytes());
		Store certificatesStore = signedData.getCertificates();
		Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
		SignerInformation signerInformation = signers.iterator().next();
		Collection matches = certificatesStore.getMatches(signerInformation.getSID());
		X509CertificateHolder certificateHolder = (X509CertificateHolder) matches.iterator().next();
		X509Certificate certFromSignedData = new JcaX509CertificateConverter().getCertificate(certificateHolder);
		certificate = CAdES.getCertificate(certFromSignedData);
		return certificate;
	}

	/**
	 * Reads signature certificate and timestamp certificate properties from PDF document
	 * @param byteArray - byte array representing signed part of the PDF
	 * @param contents - COSString instance - PDF Metadata
	 * @param signature - Signature instance to fill certificates
	 * @return Signature instance with certificates filled
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Signature getSignatureCertificates(byte[] byteArray, COSString contents, Signature signature)
			throws Exception {
		Certificate certifikatPodpisu;
		Certificate certifikatCasovejPeciatky;
		List<Certificate> certificates = new ArrayList<Certificate>();
		CMSProcessable signedContent = new CMSProcessableByteArray(byteArray);
		CMSSignedData signedData = new CMSSignedData(signedContent, contents.getBytes());
		Store certificatesStore = signedData.getCertificates();
		Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
		SignerInformation signerInformation = signers.iterator().next();
		Collection matches = certificatesStore.getMatches(signerInformation.getSID());
		X509CertificateHolder certificateHolder = (X509CertificateHolder) matches.iterator().next();
		X509Certificate certFromSignedData = new JcaX509CertificateConverter().getCertificate(certificateHolder);
		certifikatPodpisu = CAdES.getCertificate(certFromSignedData);
		signature.setSignatureCertificate(certifikatPodpisu);
		certificates.add(certifikatPodpisu);
		AttributeTable unSignedAttributes = signerInformation.getUnsignedAttributes();
		try {
			ASN1EncodableVector vector = unSignedAttributes
					.getAll(new ASN1ObjectIdentifier(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken.getId()));
			Attribute attributeTimeStamp = (Attribute) vector.get(0);
			if (attributeTimeStamp != null) {
				byte[] varTimeStamp = attributeTimeStamp.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded();
				TimeStampToken timeStampToken = new TimeStampToken(new CMSSignedData(varTimeStamp));
				signature.setTimestamp(Utils.dateToDateTime(timeStampToken.getTimeStampInfo().getGenTime()));
				signature.setTimestampUTC(Utils.dateToString(timeStampToken.getTimeStampInfo().getGenTime(), Utils.sdf0));
				Store certsStore = timeStampToken.getCertificates();
				ArrayList certs = new ArrayList(certsStore.getMatches((Selector) null));
				certificateHolder = (X509CertificateHolder) certs.get(0);
				certFromSignedData = new JcaX509CertificateConverter().getCertificate(certificateHolder);
				certifikatCasovejPeciatky = CAdES.getCertificate(certFromSignedData);
				certificates.add(certifikatCasovejPeciatky);
				signature.setTimestampCertificate(certifikatCasovejPeciatky);
			}
		} 
		catch (Exception ex) {
			Logging.logException(ex, false, Constants.logFilepath);
		}
		return signature;
	}

	/**
	 * Erases PDField (e. g. rule, signature) from PDF document widgets
	 * @param acroForm - acroform
	 * @param targetField - field t oremove
	 * @return true if removal was successful 
	 * @throws IOException
	 */
	static boolean removeField(PDAcroForm acroForm, PDField targetField) throws IOException {
	    if (targetField == null) {
	        System.out.println("removeField: Field is empty.");
	        return false;
	    }

	    PDNonTerminalField parentField = targetField.getParent();
	    if (parentField != null) {
	        List<PDField> childFields = parentField.getChildren();
	        boolean removed = false;
	        for (PDField field : childFields)
	        {
	            if (field.getCOSObject().equals(targetField.getCOSObject())) {
	                removed = childFields.remove(field);
	                parentField.setChildren(childFields);
	                break;
	            }
	        }
	        if (!removed)
	            System.out.println("removeField: Inconsistent form definition: Parent field does not reference the target field.");
	    } 
	    else {
	        List<PDField> rootFields = acroForm.getFields();
	        boolean removed = false;
	        for (PDField field : rootFields) {
	            if (field.getCOSObject().equals(targetField.getCOSObject())) {
	                removed = rootFields.remove(field);
	                break;
	            }
	        }
	        if (!removed)
	            System.out.println("removeField: Inconsistent form definition: Root fields do not include the target field.");
	    }
	    removeWidgets(targetField);
	    return true;
	}

	/**
	 * Erases PDField from PDF document widgets
	 * @param targetField - field to remove
	 * @throws IOException
	 */
	static void removeWidgets(PDField targetField) throws IOException {
	    if (targetField instanceof PDTerminalField) {
	        List<PDAnnotationWidget> widgets = ((PDTerminalField)targetField).getWidgets();
	        for (PDAnnotationWidget widget : widgets) {
	            PDPage page = widget.getPage();
	            if (page != null) {
	                List<PDAnnotation> annotations = page.getAnnotations();
	                boolean removed = false;
	                for (PDAnnotation annotation : annotations) {
	                    if (annotation.getCOSObject().equals(widget.getCOSObject()))
	                    {
	                        removed = annotations.remove(annotation);
	                        break;
	                    }
	                }
	                if (!removed)
	                    System.out.println("removeWidgets: Inconsistent annotation definition: Page annotations do not include the target widget.");
	            } else {
	                System.out.println("removeWidgets: Widget annotation does not have an associated page; cannot remove widget.");
	            }
	        }
	    } else if (targetField instanceof PDNonTerminalField) {
	        List<PDField> childFields = ((PDNonTerminalField)targetField).getChildren();
	        for (PDField field : childFields)
	            removeWidgets(field);
	    } else {
	        System.out.println("removeWidgets: Target field is neither terminal nor non-terminal; cannot remove widgets.");
	    }
	}

}
