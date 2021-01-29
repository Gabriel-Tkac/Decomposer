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
	 * Nacita pomocou kniznice org.apache.pdfbox vlastnosti PDF dokumentu
	 * a ulozi ich do zodpovedajucej instancie docsplit.model.document.Document
	 * Elektronicke podpisy dokumentu spracuje pomocou triedy org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature,
	 * precita z nich certifikat podpisu a casovej peciatky a vyplni a prepoji prislusne instancie podpisov a certifikatov
	 * @param data - vstupne pole bytov s obsahom podpisaneho PDF dokumentu (objektu PAdES)
	 * @return instancia docsplit.model.document.container reprezentujuca podpisovy kontajner PAdES
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
					throw new Exception("Nastal problem pri spracovani pdf suboru.", e);
				}
			}
		}
		catch (Exception e) {
			throw new Exception("Nastal problem pri spracovani dokumentu pades.", e);
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
	 * Odstrani z PDF dokumentu podpis signPDF a všetky neskoršie podpisy
	 * @param documentPDF - vstupny PDF dokument
	 * @param signPDF - posledny z podpisov, ktore sa odstrania
	 * @throws Exception 
	 */
	static void removeSignField(PDDocument documentPDF, PDSignature signPDF) throws Exception {
		if (signPDF == null) {
			throw new Exception("removeSignField: podpis je prazdny.");
		}
		Calendar signDate = signPDF.getSignDate();
		if (signDate != null) {
			//System.out.println("removeSignField: datum podpisu: " + Helper.dateToUTCString(signDate));
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
	 * Nacita z PDF dokumentu podpis a vytvori objekt typu Signature pre ulozenie do vystupneho kontajneru
	 * @param pdSignature - vstupny podpis z PDF dokumentu
	 * @param data - vstupne pole bytov pre nacitanie podpisaneho obsahu
	 * @param porCislo - poradove cislo podpisu v zozname podpisov dokumentu
	 * @return objekt triedy Signature
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
		System.out.println("Meno drzitela certifikatu: " + signature.getCertificateHolder());
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
					byte[] certData = contents.getBytes();
					CertificateFactory factory = CertificateFactory.getInstance("X.509");
					ByteArrayInputStream certStream = new ByteArrayInputStream(certData);
					Collection<? extends java.security.cert.Certificate> certs = factory
							.generateCertificates(certStream);
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
					TimeStampToken timeStampToken = new TimeStampToken(
							new CMSSignedData(contents.getBytes()));
					CertificateFactory factory = CertificateFactory.getInstance("X.509");
					ByteArrayInputStream certStream = new ByteArrayInputStream(contents.getBytes());
					Collection<? extends java.security.cert.Certificate> certs = factory
							.generateCertificates(certStream);
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
	 * Sformuje PDF document z pola bytov
	 * @param data - vstupne pole bytov
	 * @return dokument typu PDDocument
	 * @throws Exception 
	 */
	private static PDDocument getDocumentPDF(byte[] data) throws Exception {
		PDDocument documentPDF = PDDocument.load(data);
		documentPDF.getClass();
		if (!documentPDF.isEncrypted()) {
			return documentPDF; 
		}
		else {
			throw new Exception("getDocumentPDF: Dokument je chraneny heslom.");
		}
	}

	/**
	 * Vytvori kontajner typu Document a vyplnio ho udajmi z PDF dokumentu
	 * @param documentPDF - vstupny PDF dokument
	 * @param porCislo - vstupne poradove cislo dokumentu v zozname podpisanych dokumentov; hlavny dokument ma poradove cislo 0
	 * @return dokument typu Document
	 * @throws IOException
	 */
	private static Document getDocumentFromPDF(PDDocument documentPDF, int porCislo) throws IOException {
		Document document = new Document();
		String suffix = "";
		if (porCislo > 0)
			suffix = " (revizia " + porCislo + ")"; 
		// setting Document
		document.setId(java.util.UUID.randomUUID().toString());
		document.setProcessId(java.util.UUID.randomUUID().toString());
		document.setName("Dokument.pdf" + suffix);

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
	 * Nacita udaje certifikatu z podpisu v PDF dokumente, ak ide o certifikat sifrovany algoritmom adbe.pkcs7.sha1
	 * @param byteArray - pole bytov reprezentujuce obsah podpisanej casti PDF dokumentu
	 * @param contents - instancia nesuca info o podpise v PDF dokumente
	 * @param sig - instancia podpisu reprezentovana objektom kniznice org.apache.pdfbox
	 * @return - instancia docsplit.model.document.Certificate
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
	 * Nacita z podpisanej casti obsahu PDF dokumentu certifikat podpisu a casovej peciatky a nasetuje ich do instancie podpisu
	 * @param byteArray - pole bytov reprezentujuce obsah podpisanej casti PDF dokumentu
	 * @param contents - instancia nesuca info o podpise v PDF dokumente
	 * @param signature - instancia podpisu, ktorej sa vyplnia certifikaty
	 * @return instancia podpisu s vyplnenymi certifikatmi
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
	 * Odstrani pole typu PDField (ako pravidlo, podpis) z PDF dokumentu
	 * @param acroForm - acroforma
	 * @param targetField - pole pre odstranenie
	 * @return true ak sa podarilo, inak false 
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
	 * Vymaze pole typu PDField z widgetov PDF dokumentu
	 * @param targetField - vstupne pole, ktore sa odstranuje
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
