package sk.gabrieltkac.decomposer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.codec.digest.DigestUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;

import sk.gabrieltkac.decomposer.model.Certificate;
import sk.gabrieltkac.decomposer.model.Container;
import sk.gabrieltkac.decomposer.model.ContainerType;
import sk.gabrieltkac.decomposer.model.Document;
import sk.gabrieltkac.decomposer.model.Signature;
import sk.gabrieltkac.decomposer.utils.Constants;
import sk.gabrieltkac.decomposer.utils.Utils;



public class XAdES {
	
	static Container getXAdESContainer(byte[] data) {
		Container container = new Container();
		container.setContainerType(ContainerType.XAdES);
		
		List<Document> documents = new ArrayList<Document>();
		List<Signature> signatures = new ArrayList<Signature>();
		DocumentBuilderFactory dbf;
		DocumentBuilder db;
		org.w3c.dom.Document domDocument = null;

		dbf = DocumentBuilderFactory.newInstance();
		
		try {
			db = dbf.newDocumentBuilder();
			domDocument = db.parse(Utils.createTempFile(data));
			domDocument.getDocumentElement().normalize();
		} 
		catch (ParserConfigurationException | SAXException | IOException e) {
			Logging.logException(e, false, Constants.logFilepath);
		}

		// documents
		NodeList objectsList = domDocument.getDocumentElement().getElementsByTagName("ds:Object");
		try {
			documents = getDocuments(objectsList, documents);
		} 
		catch (TransformerException e) {
			Logging.logException(e, false, Constants.logFilepath);
		}

		// signatures
		NodeList signaturesList = domDocument.getDocumentElement().getElementsByTagName("ds:Signature");
		try {
			signatures = getSignatures(signaturesList, documents, signatures);
		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
		}

		container.setId(java.util.UUID.randomUUID().toString());
		container.setDocuments(documents);
		container.setSignatures(signatures);

		return container;
	}
	
	/**
	 * Reads document contents from sign container 
	 * @param objectsList - XML Node list
	 * @param documents - read documents
	 * @return document list
	 * @throws TransformerException
	 */
	static List<Document> getDocuments(NodeList objectsList, List<Document> documents) throws TransformerException {
		Document document = null;
		String idPrev = "";
		for (int i = 0; i < objectsList.getLength(); i++) {
			Node obj = objectsList.item(i);
			if (obj.getNodeType() == Node.ELEMENT_NODE) {
				Element objElement = (Element) obj;
				if (!objElement.getParentNode().getNodeName().equals("xzep:DataEnvelope") &&
					!objElement.getParentNode().getNodeName().equals("xzepds:DataSignatures")) {
					continue;
				}
				if ((i == 0 || !objElement.getAttribute("Id").contains(idPrev)) &&
					!objElement.getAttribute("Id").toLowerCase().contains("verification")) {
					
					document = new Document();
					document.setId(objElement.getAttribute("Id"));
					
					if (objElement.hasAttribute("Encoding") &&
						objElement.getAttribute("Encoding").contains("base64")) {
						document.setContent(Utils.getTextNode(objElement));
					} 
					else {
						document.setContent(Base64.getEncoder().encodeToString(Utils.getTextNode(objElement).getBytes()));
					}
					documents.add(document);
				}
				idPrev = objElement.getAttribute("Id");
			}
		}
		return documents;
	}
	
	/**
	 * Parses Signatures properties from container structure
	 * @param signaturesList - XML Nodes to parse
	 * @param documents - input documents list
	 * @param signatures - output documents list
	 * @return signatures list
	 * @throws Exception
	 */
	static List<Signature> getSignatures(NodeList signaturesList, List<Document> documents,
			List<Signature> signatures) throws Exception {
		List<Document> docsForSignature = new ArrayList<Document>();
		Certificate certificate = null;
		List<Certificate> certificates = new ArrayList<Certificate>();
		Signature signature = null;
		String xmlXades = null;
		Certificate timeStamp = null;
		for (int i = 0; i < signaturesList.getLength(); i++) {
			certificate = new Certificate();
			Node sig = signaturesList.item(i);
			if (sig.getNodeType() == Node.ELEMENT_NODE) {
				Element sigElement = (Element) sig;
				if (sigElement.hasAttribute("Id")) {
					signature = new Signature();
					signature.setName(sigElement.getAttribute("Id"));
					signature.setId(sigElement.getAttribute("Id"));
					NodeList childReferene = sig.getChildNodes();
					
					signature.setBPLevel(resolveBPLevel(childReferene));
					for (int j = 0; j < childReferene.getLength(); j++) {
						Node sigs = childReferene.item(j);
						if (sigs.getNodeType() == Node.ELEMENT_NODE) {
							if ("ds:SignedInfo".equals(sigs.getNodeName())) {
								NodeList refObjects = sigs.getChildNodes();
								for (int k = 0; k < refObjects.getLength(); k++) {
									Node ref = refObjects.item(k);
									if (ref.getNodeType() == Node.ELEMENT_NODE) {
										Element refElement = (Element) ref;
										if ("ds:Reference".equals(refElement.getNodeName())) {
											if (documents != null) {
												for (Document doc : documents) {
													if (refElement.getAttribute("URI").substring(1).equals(doc.getId())
															| refElement.getAttribute("URI").equals(doc.getId())
															| URLDecoder.decode(refElement.getAttribute("URI"), "UTF-8").equals(doc.getId())
															| isSameDigestValue(refElement, doc)) {
														doc.setProcessId(refElement.getAttribute("Id"));
														docsForSignature.add(doc);
													}
												}
											}
										}
									}
								}
							}
							if ("ds:KeyInfo".equals(sigs.getNodeName())) {
								NodeList keyInfoObjects = sigs.getChildNodes();
								for (int k = 0; k < keyInfoObjects.getLength(); k++) {
									Node key = keyInfoObjects.item(k);
									if (key.getNodeType() == Node.ELEMENT_NODE) {
										NodeList x509DataObjects = key.getChildNodes();
										for (int l = 0; l < x509DataObjects.getLength(); l++) {
											Node x509 = x509DataObjects.item(l);
											if (x509.getNodeType() == Node.ELEMENT_NODE) {
												if ("ds:X509Certificate".equals(x509.getNodeName())) {
													if (certificate.getPublisher() == null)
														certificate = CAdES.getCertificateFromBase64(x509.getTextContent());
												}
											}
										}
									}
								}
							}

							if ("ds:Object".equals(sigs.getNodeName())) {
								NodeList xadesAndSigPropObjects = sigs.getChildNodes();
								for (int k = 0; k < xadesAndSigPropObjects.getLength(); k++) {
									Node xades = xadesAndSigPropObjects.item(k);
									if (xades.getNodeType() == Node.ELEMENT_NODE) {
										if ("xades:QualifyingProperties".equals(xades.getNodeName())) {
											xmlXades = Utils.getTextNode(xades.getParentNode());
											String dateStr = Utils.getValueByToken(Constants.XADESSIGNTIME, xmlXades.getBytes(), true, null);
											Date dateOsetrenyNaTZ = Utils.dateToUTC(dateStr);
											signature.setSignatureTimeUTC(Utils.dateToUTCString(dateStr));
											if (dateOsetrenyNaTZ != null) {
												signature.setSignatureTime(dateOsetrenyNaTZ.toInstant()
													      .atZone(ZoneId.systemDefault())
													      .toLocalDateTime());
												signature.setSignatureTimeHasBeehShifted(true);
											} 
											else
												signature.setSignatureTime(Utils.stringToDate(dateStr).toInstant()
													      .atZone(ZoneId.systemDefault())
													      .toLocalDateTime());

											if (signature.getSignatureTime() == null) {
												signature.setSignatureTime(Utils.stringToDate(Utils.getValueByToken(
														Constants.XADES_SIGNTIME, xmlXades.getBytes(), true, null)).toInstant()
													      .atZone(ZoneId.systemDefault())
													      .toLocalDateTime());
												signature.setSignatureTimeUTC(Utils.dateToUTCString(Utils.getValueByToken(
														Constants.XADES_SIGNTIME, xmlXades.getBytes(), true, null)));
											}
											String ts = Utils.getValueByToken(Constants.XADESTIMESTAMP, xmlXades.getBytes(), true, null);
											if (CAdES.getDateTimeStampFromString(ts) != null) {
												signature.setTimestamp(Utils.dateToDateTime(CAdES.getDateTimeStampFromString(ts)));
												signature.setTimestampUTC(Utils.dateToString(CAdES.getDateTimeStampFromString(ts), Utils.sdf0));
											}
											timeStamp = CAdES.createTimeStampFromString(ts);
										}

										if ("ds:Manifest".equals(xades.getNodeName())) {
											NodeList refObjects = xades.getChildNodes();
											for (int k1 = 0; k1 < refObjects.getLength(); k1++) {
												Node ref = refObjects.item(k1);
												if (ref.getNodeType() == Node.ELEMENT_NODE) {
													Element refElement = (Element) ref;
													if ("ds:Reference".equals(refElement.getNodeName())) {
														if (documents != null) {
															for (Document doc : documents) {
																if (refElement.getAttribute("URI").substring(1).equals(doc.getId())
																		| refElement.getAttribute("URI").equals(doc.getId())
																		| URLDecoder.decode(refElement.getAttribute("URI"), "UTF-8").equals(doc.getId())
																		| Utils.isSameDigestValue(refElement, doc)) {
																	docsForSignature.add(doc);
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}

					if (xmlXades != null && !docsForSignature.isEmpty())
						setNameAndMimeType(docsForSignature, xmlXades);

				}
			}
			signature.setDocuments(checkUnique(docsForSignature));
			signature.setTimestampCertificate(timeStamp);
			certificates.add(certificate);
			signature.setSignatureCertificate(certificate);
			signatures.add(signature);
		}

		return signatures;
	}
	
	/**
	 * Makes document list unique
	 * @param docsForSignature - incoming document list
	 * @return unique document list
	 */
	static List<Document> checkUnique(List<Document> docsForSignature) {
		Set<Document> hs = new LinkedHashSet<>();
		hs.addAll(docsForSignature);
		docsForSignature.clear();
		docsForSignature.addAll(hs);
		return docsForSignature;
	}
	
	/**
	 * Resolve XAdES BP level by presence of elements in XML structure:<br>
	 * - XadesBPLevelB, if UnsignedProperties is absent<br>
	 * - XadesBPLevelLTA, if ArchiveTimeStamp is present<br>
	 * - XadesBPLevelLT, if not either of the previous and CertificatesValues or RevocationValues is present<br>
	 * - level T - otherwise<br>
	 * @param nodeList - XML Node list
	 * @return level type
	 */
	static String resolveBPLevel(NodeList childReferene) {
		String bpLevel = "XadesBPLevel";

		boolean hasUnsignedProperties = false; // Level B
		boolean hasCertificatesValuesORevocationValues = false; // Level LT
		boolean hasArchiveTimeStamp = false; // Level LTA

		List<Node> zoznam = new ArrayList<Node>();
		for (int j = 0; j < childReferene.getLength(); j++) {
			Node sigs = childReferene.item(j);
			zoznam = getNodeChildrenList(sigs, 0, zoznam);
		}
		
		for (Node n : zoznam) {
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				if (n.getNodeName().indexOf(Constants.LEVEL_LTA) >= 0)
					hasArchiveTimeStamp = true;
				if (n.getNodeName().indexOf(Constants.LEVEL_B) >= 0)
					hasUnsignedProperties = true;
				if (n.getNodeName().indexOf(Constants.LEVEL_LT[0]) >= 0 || n.getNodeName().indexOf(Constants.LEVEL_LT[1]) >= 0)
					hasCertificatesValuesORevocationValues = true;
			}
		}

		if (!hasUnsignedProperties)
			return bpLevel + "B";
		if (hasArchiveTimeStamp)
			return bpLevel + "LTA";
		if (hasCertificatesValuesORevocationValues)
			return bpLevel + "LT";
		else
			bpLevel += "T";

		return bpLevel;
	}
	
	/**
	 * Recursively get all nested nodes
	 * @param node - node to evaluate
	 * @param level - current node nested level
	 * @param zoznam - current node list
	 * @return zoznam - resulting node list
	 */
	static List<Node> getNodeChildrenList(Node node, int level, List<Node> zoznam) {
		NodeList list = node.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node childNode = list.item(i);
			zoznam.add(childNode);
			getNodeChildrenList(childNode, level + 1, zoznam);
		}

		return zoznam;
	}
	
	/**
	 * Fills MIME Type to the documents according to container manifest
	 * @param docsForSignature - documents to fill MIME Type
	 * @param xmlAsiceManifest - manifest content
	 * @return documents with MIME Type set
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	static List<Document> setMimeType(List<Document> docsForSignature, String xmlAsiceManifest)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbf;
		DocumentBuilder db;
		InputSource source;
		org.w3c.dom.Document domDocument = null;

		dbf = DocumentBuilderFactory.newInstance();
		db = dbf.newDocumentBuilder();
		source = new InputSource(new ByteArrayInputStream(xmlAsiceManifest.getBytes()));
		domDocument = db.parse(source);
		domDocument.getDocumentElement().normalize();

		NodeList dataObjectFormatList = domDocument.getDocumentElement().getElementsByTagName("asic:DataObjectReference");
		if (dataObjectFormatList.getLength() > 0) {
			for (int i = 0; i < dataObjectFormatList.getLength(); i++) {
				Node obj = dataObjectFormatList.item(i);
				if (obj.getNodeType() == Node.ELEMENT_NODE) {
					Element objElement = (Element) obj;
					for (Document document : docsForSignature) {
						if (document.getName() != null) {
							if (document.getName().equals(objElement.getAttribute("URI")) || document.getId().equals(objElement.getAttribute("URI"))) {
								if (document.getMIMEType() == null)
									document.setMIMEType(objElement.getAttribute("MimeType"));
							}
						}
					}
				}
			}
		} 
		else {
			dataObjectFormatList = domDocument.getDocumentElement().getElementsByTagName("manifest:file-entry");
			for (int i = 0; i < dataObjectFormatList.getLength(); i++) {
				Node obj = dataObjectFormatList.item(i);
				if (obj.getNodeType() == Node.ELEMENT_NODE) {
					Element objElement = (Element) obj;
					for (Document document : docsForSignature) {
						if (document.getName() != null) {
							if (document.getName().equals(objElement.getAttribute("manifest:full-path")) || document.getId().equals(objElement.getAttribute("manifest:full-path"))) {
								if (objElement.getAttribute("manifest:media-type") != null)
									document.setMIMEType(objElement.getAttribute("manifest:media-type"));
							}
						}
					}
				}
			}
		}
		return docsForSignature;
	}
	
	/**
	 * Fills in name, extension and MIME Type to documents
	 * @param docsForSignature - documents to set properties
	 * @param xmlXades - XML XAdES
	 * @return document list
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	static List<Document> setNameAndMimeType(List<Document> docsForSignature, String xmlXades)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbf;
		DocumentBuilder db;
		InputSource source;
		org.w3c.dom.Document domDocument = null;

		dbf = DocumentBuilderFactory.newInstance();
		db = dbf.newDocumentBuilder();
		source = new InputSource(new ByteArrayInputStream(xmlXades.getBytes()));
		domDocument = db.parse(source);
		domDocument.getDocumentElement().normalize();

		NodeList dataObjectFormatList = domDocument.getDocumentElement().getElementsByTagName("xades:DataObjectFormat");
		for (int i = 0; i < dataObjectFormatList.getLength(); i++) {
			Node obj = dataObjectFormatList.item(i);
			if (obj.getNodeType() == Node.ELEMENT_NODE) {
				Element objElement = (Element) obj;
				NodeList o = objElement.getChildNodes();
				for (int j = 0; j < o.getLength(); j++) {
					Node ja = o.item(j);
					if (ja.getNodeType() == Node.ELEMENT_NODE) {
						if ("xades:Description".equals(ja.getNodeName())) {
							for (Document document : docsForSignature) {
								if (document.getProcessId() != null) {
									if (objElement.getAttribute("ObjectReference").contains(document.getProcessId())) {
										document.setDescription(ja.getTextContent());
									}
								}
								if (document.getServiceId() != null) {
									if (objElement.getAttribute("ObjectReference").contains(document.getServiceId())) {
										document.setDescription(ja.getTextContent());
									}
								}
							}
						}
						if ("xades:MimeType".equals(ja.getNodeName())) {
							for (Document document : docsForSignature) {
								if (document.getProcessId() != null) {
									if (objElement.getAttribute("ObjectReference").contains(document.getProcessId())) {
										if (document.getExtension() == null)
											document.setExtension(Utils.parseExtension(ja.getTextContent()));
										if (document.getMIMEType() == null)
											document.setMIMEType(ja.getTextContent());
									}
								}
								if (document.getServiceId() != null) {
									if (objElement.getAttribute("ObjectReference").contains(document.getServiceId())) {
										if (document.getExtension() == null)
											document.setExtension(Utils.parseExtension(ja.getTextContent()));
										if (document.getMIMEType() == null)
											document.setMIMEType(ja.getTextContent());
									}
								}
							}
						}
					}
				}
			}
		}
		return docsForSignature;
	}
	
	static boolean isSameDigestValue(Node refElement, Document doc) {
		byte[] digest;
		if (doc.getContent().isEmpty())
			return false;
		String algorithm = "";
		String digestValue = "";
		String transform = "";
		String obsah = doc.getContent();
		try {
			NodeList refs = refElement.getChildNodes();
			for (int k = 0; k < refs.getLength(); k++) {
				Node ref = refs.item(k);
				if (ref.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) ref;
					if ("ds:DigestMethod".equals(e.getNodeName())) {
						algorithm = e.getAttribute("Algorithm");
					}
					if ("ds:DigestValue".equals(e.getNodeName())) {
						digestValue = Utils.getTextNode(e);
					}
					if ("ds:Transforms".equals(e.getNodeName())) {
						NodeList rs = e.getChildNodes();
						for (int l = 0; l < rs.getLength(); l++) {
							Node r = rs.item(l);
							if (r.getNodeType() == Node.ELEMENT_NODE) {
								Element re = (Element) r;
								if ("ds:Transform".equals(r.getNodeName())) {
									transform = re.getAttribute("Algorithm");
								}
							}
						}
					}
				}
			}

			String docMimeType = doc.getMIMEType();
			if (!transform.isEmpty() && docMimeType != null && docMimeType.equals(Constants.MIME_TYPE_FORM)) {
				obsah = Utils.canonicalize(Utils.removeBOM(doc.getOriginalContent()), transform);
				obsah = Base64.getEncoder().encodeToString(obsah.getBytes()); 
			}

			switch (algorithm) {
			case Constants.DIGES_MD5:
				digest = DigestUtils.md5(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA1:
				digest = DigestUtils.sha1(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA256:
				digest = DigestUtils.sha256(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA384:
				digest = DigestUtils.sha384(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA512:
				digest = DigestUtils.sha512(Base64.getDecoder().decode(obsah));
				break;
			default:
				digest = null;
			}
			if (Base64.getEncoder().encodeToString(digest).equals(digestValue))
				return true;
			else
				return false;
		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
			return false;
		}
	}

	static String getActualDigestValue(Node refElement, Document doc) {
		byte[] digest;
		if (doc.getContent().isEmpty())
			return null;
		String algorithm = "";
		String transform = "";
		String obsah = doc.getContent();
		String actualDigetstValue = "";
		try {
			NodeList refs = refElement.getChildNodes();
			for (int k = 0; k < refs.getLength(); k++) {
				Node ref = refs.item(k);
				if (ref.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) ref;
					if ("ds:DigestMethod".equals(e.getNodeName())) {
						algorithm = e.getAttribute("Algorithm");
					}
					if ("ds:Transforms".equals(e.getNodeName())) {
						NodeList rs = e.getChildNodes();
						for (int l = 0; l < rs.getLength(); l++) {
							Node r = rs.item(l);
							if (r.getNodeType() == Node.ELEMENT_NODE) {
								Element re = (Element) r;
								if ("ds:Transform".equals(r.getNodeName())) {
									transform = re.getAttribute("Algorithm");
								}
							}
						}
					}
				}
			}

			if ((!transform.isEmpty() || !"".equals(transform)) && doc.getMIMEType().equals(Constants.MIME_TYPE_FORM)) {
				obsah = Utils.canonicalize(Utils.removeBOM(doc.getOriginalContent()), transform);
				obsah = Base64.getEncoder().encodeToString(obsah.getBytes());
			}

			switch (algorithm) {
			case Constants.DIGES_MD5:
				digest = DigestUtils.md5(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA1:
				digest = DigestUtils.sha1(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA256:
				digest = DigestUtils.sha256(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA384:
				digest = DigestUtils.sha384(Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA512:
				digest = DigestUtils.sha512(Base64.getDecoder().decode(obsah));
				break;
			default:
				digest = null;
			}
			actualDigetstValue = Base64.getEncoder().encodeToString(digest);
		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
		}
		return actualDigetstValue;
	}
	

}
