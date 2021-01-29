package sk.gabrieltkac.decomposer.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import sk.gabrieltkac.decomposer.Logging;
import sk.gabrieltkac.decomposer.XAdES;
import sk.gabrieltkac.decomposer.model.ContainerType;

public class Utils {
	
	public static SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
	public static SimpleDateFormat sdf0 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public static SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSSS");
	
	/**
	 * Resolves input sign container type
	 * @param data - input byte array - container content
	 * @return ContainerType enumeration item
	 */
	public static ContainerType getContainerType(byte[] data) {
		if (containsAsiceEntry(data))
			return ContainerType.ASiCe_XAdES;
		if (containsCadesEntry(data))
			return ContainerType.CAdES;
		if (containsPadesEntry(data))
			return ContainerType.PAdES;
		if (containsXadesEntry(data))
			return ContainerType.XAdES;
		return ContainerType.ASiCe_XAdES;
	}

	/**
	 * Resolves, whether input object is XAdES container
	 * @param data - input byte array - container content
	 * @return boolean is XAdES container
	 */
	private static boolean containsXadesEntry(byte[] data) {
		DocumentBuilderFactory dbf;
		DocumentBuilder db;
		InputSource source;
		@SuppressWarnings("unused")
		Document domDocument = null;
		dbf = DocumentBuilderFactory.newInstance();
		try {
			db = dbf.newDocumentBuilder();
			source = new InputSource(IOUtils.toInputStream(new String(data)));
			domDocument = db.parse(source);
			return true;
		} 
		catch (IOException | SAXException | ParserConfigurationException e) {
			return false;
		}
	}

	/**
	 * Resolves, whether input object is PAdES container
	 * @param data - input byte array - container content
	 * @return boolean boolean is PAdES container
	 */
	private static boolean containsPadesEntry(byte[] data) {
		PDDocument documentPDF = null;
		try {
			documentPDF = PDDocument.load(data);
			documentPDF.getClass();
			documentPDF.close();
			return true;
		} 
		catch (IOException e) {
			return false;
		}
	}

	/**
	 * Resolves, whether input object is CAdES container
	 * @param data - input byte array - container content
	 * @return boolean boolean is CAdES container
	 */
	private static boolean containsCadesEntry(byte[] data) {
		File temp = null;
		ZipFile tempZip = null;
		int lastIndex = 0;
		try {
			temp = createTempFile(data);
			tempZip = new ZipFile(temp);
			if (tempZip != null) {
				Enumeration<? extends ZipEntry> entries = tempZip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry e = entries.nextElement();
					lastIndex = e.getName().lastIndexOf(".");
					if (lastIndex > 0) {
						if ((e.getName().substring(lastIndex + 1)).equalsIgnoreCase(Constants.CADES_p7s)) {
							return true;
						}
					}
				}
			}
		} 
		catch (IOException e1) {
			return false;
		} 
		finally {
			if (tempZip != null) {
				try {
					tempZip.close();
				} 
				catch (IOException e) {
					e.printStackTrace();
				}
				temp.delete();
			}
		}
		return false;
	}

	/**
	 * Resolves, whether input object is ASiCx container
	 * @param data - input byte array - container content
	 * @return boolean boolean is ASiC container
	 */
	private static boolean containsAsiceEntry(byte[] data) {
		File temp = null;
		ZipFile tempZip = null;
		try {
			temp = createTempFile(data);
			tempZip = new ZipFile(temp);
			if (tempZip != null) {
				Enumeration<? extends ZipEntry> entries = tempZip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry e = entries.nextElement();
					if (e.getName().contains(Constants.MIME_TYPE_FILE)) {
						try {
							InputStream stream = tempZip.getInputStream(e);
							String asice = new String(IOUtils.toString(stream));
							if (asice.contains(Constants.MIME_TYPE_ASICE) || asice.contains(Constants.MIME_TYPE_ASICS))
								return true;
						} 
						catch (IOException ex) {
							return false;
						}
					}
				}
			}
		} 
		catch (Exception e1) {
			return false;
		} 
		finally {
			if (tempZip != null) {
				try {
					tempZip.close();
				} 
				catch (IOException e) {
					e.printStackTrace();
				}
				temp.delete();
			}
		}
		return false;
	}
	
	/**
	 * Creates temporary file based on byte array
	 * @param data - byte array (file content)
	 * @return temp.data temporary file
	 */
	public static File createTempFile(byte[] data) {
		File outFile = null;
		try {
			outFile = File.createTempFile("temp", ".data");
			outFile.deleteOnExit();
			if (data != null) {
				BufferedOutputStream output = null;
				try {
					output = new BufferedOutputStream(new FileOutputStream(outFile));
					output.write(data);
				} 
				finally {
					output.close();
				}
			}
			return outFile;
		} 
		catch (IOException e) {
			return null;
		}
	}
	
	/**
	 * Gets text interpretation of XML node
	 * @param paNode - XML node
	 * @return node text
	 * @throws TransformerException
	 */
	public static String getTextNode(Node paNode) throws TransformerException {
		NodeList nodes = paNode.getChildNodes();
		StringWriter sw = new StringWriter();
		Transformer transformer = getTransformer();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			transformer.transform(new DOMSource(node), new StreamResult(sw));
		}
		return sw.toString();
	}

	/**
	 * Gets a text representation of XML Node  
	 * @param pomNode - XML Node
	 * @param document - source org.w3c.dom.Document instance
	 * @return text representation of input node
	 * @throws TransformerException
	 */
	private static String getTextNode(Node pomNode, Document document) throws TransformerException {
		NodeList nodes = pomNode.getChildNodes();
		StringWriter sw = new StringWriter();
		Transformer transformer = getTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, document.getInputEncoding());
		transformer.setOutputProperty(OutputKeys.VERSION, document.getXmlVersion());
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		for (int i = 0; i < nodes.getLength(); i++) {
			if (i > 0)
				transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			Node node = nodes.item(i);
			transformer.transform(new DOMSource(node), new StreamResult(sw));
		}
		return sw.toString();
	}
	
	/**
	 * Creates and returns javax.xml.transform.Transformer instance<br> 
	 * Parameters: indent = 4, encoding = UTF-8, without header
	 * @return javax.xml.transform.Transformer instance
	 * @throws TransformerFactoryConfigurationError
	 * @throws TransformerConfigurationException
	 */
	private static Transformer getTransformer()
			throws TransformerFactoryConfigurationError, TransformerConfigurationException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		return transformer;
	}
	
	public static boolean isSameDigestValue(Node refElement, sk.gabrieltkac.decomposer.model.Document doc) {
		byte[] digest;
		if (doc.getContent().isEmpty() || "".equals(doc.getContent()))
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
			if ((!transform.isEmpty() || !"".equals(transform)) && docMimeType != null && docMimeType.equals(Constants.MIME_TYPE_FORM)) {
				obsah = XAdES.canonicalize(XAdES.removeBOM(doc.getOriginalContent()), transform);
				obsah = java.util.Base64.getEncoder().encodeToString(obsah.getBytes());
			}

			switch (algorithm) {
			case Constants.DIGES_MD5:
				digest = DigestUtils.md5(java.util.Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA1:
				digest = DigestUtils.sha1(java.util.Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA256:
				digest = DigestUtils.sha256(java.util.Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA384:
				digest = DigestUtils.sha384(java.util.Base64.getDecoder().decode(obsah));
				break;
			case Constants.DIGES_SHA512:
				digest = DigestUtils.sha512(java.util.Base64.getDecoder().decode(obsah));
				break;
			default:
				digest = null;
			}
			if (java.util.Base64.getEncoder().encodeToString(digest).equals(digestValue))
				return true;
			else
				return false;
		} 
		catch (Exception e) {
			Logging.logException(e, false, Constants.logFilepath);
			return false;
		}
	}
	
	/**
	 * Extracts element or attribute value from XML document
	 * @param token - element name to evaluate
	 * @param xml - byte array - XML Document content
	 * @param plainText - parse only plain text
	 * @param attribute - attribute to read
	 * @return element content
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws XPathExpressionException
	 * @throws TransformerException
	 */
	public static String getValueByToken(String token, byte[] xml, boolean plainText, String attribute)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException,
			TransformerException {

		String result = "";
		String xpathString = "";
		if (token == null)
			return result;

		xpathString = token;
		InputSource source = new InputSource(new ByteArrayInputStream(xml));
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document document = db.parse(source);
		XPathExpression xpath = XPathFactory.newInstance().newXPath().compile(xpathString);

		if (attribute != null) {
			Node pomNode = (Node) xpath.evaluate(document, XPathConstants.NODE);
			if (pomNode instanceof Element && !((Element) pomNode).getAttribute(attribute).isEmpty()) {
				result = ((Element) pomNode).getAttribute(attribute);
			}
		} 
		else {
			if (plainText) {
				Node pomNode = (Node) xpath.evaluate(document, XPathConstants.NODE);
				if (pomNode == null)
					return result;
				if (token.equals(Constants.DATA_FROM_DATA_CONTAINER)) {
					result = getTextNode(pomNode, document);
				} 
				else {
					result = pomNode.getTextContent();
				}
			} 
			else {
				NodeList nodes = (NodeList) xpath.evaluate(document, XPathConstants.NODESET);
				StringWriter sw = new StringWriter();
				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					Transformer transformer = getTransformer();
					transformer.transform(new DOMSource(node), new StreamResult(sw));
				}
				result = sw.toString();
			}
		}
		return result;
	}
	
	public static Date dateToUTC(Calendar signDate) {
		String timeZone = signDate.getTimeZone().getID();
		Date utc = new Date(
				signDate.getTimeInMillis() - TimeZone.getTimeZone(timeZone).getOffset(signDate.getTimeInMillis()));
		return utc;
	}
	
	/**
	 * Transforms formatted string of yyyy-MM-dd'T'HH:mm:ss to java.util.Date 
	 * @param dateString - input string
	 * @return java.uitl.Date instance
	 */
	public static Date stringToDate(String dateString) {
		Date date = null;
		try {
			date = sdf0.parse(dateString);
		} 
		catch (ParseException e) {
			Logging.logException(e, false, Constants.logFilepath);
		}
		return date;
	}
	
	public static Date dateToUTC(String dateStr) {
		if(dateStr.isEmpty())
			return null;
		DateTime dateTime = DateTime.parse(dateStr);
		DateTime dateTimeUTC = dateTime.withZone(DateTimeZone.UTC);
		return dateTimeUTC.toDate();
	}
	

	public static String dateToUTCString(Calendar signDate) {
		return dateToString(new Date(
				signDate.getTimeInMillis()),
				sdf0);
	}
	
	public static String dateToUTCString(String dateStr) {
		if(dateStr.isEmpty())
			return "";
		DateTime dateTime = DateTime.parse(dateStr);
		DateTime dateTimeUTC = dateTime.withZone(DateTimeZone.UTC);
		return dateToString(dateTimeUTC.toDate(), sdf0);
	}
	
	/**
	 * Transforms Date to String dd.MM.yyyy HH:mm:ss
	 * @param date - date instance
	 * @return formatted date string
	 */
	public static String dateToString(Date date) {
		if (date != null)
			return sdf.format(date);
		return "";
	}

	public static String dateToString(Date date, SimpleDateFormat sdf) {
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		if (date != null)
			return sdf.format(date);
		return "";
	}
	
	public static String checkFileNameFromEmail(String fileName) {
		String regex = "=\\?{1}(.+)\\?{1}([B|Q])\\?{1}(.+)\\?{1}=";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(fileName);
		if (matcher.find()) {
			if ("B".equalsIgnoreCase(matcher.group(2)))
				fileName = new String(java.util.Base64.getDecoder().decode(matcher.group(3)));
		}
		return fileName;
	}
	
	public static LocalDateTime dateToDateTime(Date dateToConvert) {
	    return dateToConvert.toInstant()
	      .atZone(ZoneId.systemDefault())
	      .toLocalDateTime();
	}
	
	/**
	 * Check whether string contains som of the following  xml, pdf, png, xsl, text, tif, jpg
	 * @param textContent - string to evaluate
	 * @return extension
	 */
	public static String parseExtension(String textContent) {
		List<String> extensionNames = Arrays.asList("xml", "pdf", "png", "xsl", "text", "tif", "jpg");
		for (String extensionName : extensionNames) {
			if (textContent.contains(extensionName)) {
				if ("text".equals(extensionName))
					return "txt";
				if ("xsl".equals(extensionName))
					return "xslt";
				return extensionName;
			}
		}
		return null;
	}
	
	/**
	 * Evaluates document MIME Type
	 * @param filename - file name
	 * @return MIME Type
	 * @throws IOException
	 */
	public static String detectMime(String filename) throws IOException {
		String mimeType = Files.probeContentType(Paths.get(filename));
		if (mimeType == null) {
			mimeType = URLConnection.getFileNameMap().getContentTypeFor(filename);
		}
		if (mimeType == null) {
			throw new IllegalStateException(String.format("Unable to determine MIME type of %s", filename));
		}
		return mimeType;
	}

}
