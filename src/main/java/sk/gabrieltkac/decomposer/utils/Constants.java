package sk.gabrieltkac.decomposer.utils;

public class Constants {
	
	public static final String logFilepath = "D:/Decomposer";
	
	public final static int BUFFER_SIZE = 2048;
	
	public static final String UTF8_BOM = "\uFEFF";
	
	public static final String CADES_p7s = "p7s";
	
	public static final CharSequence FOLDER_FOR_POLICY = "Policy";
	public static final String FOLDER_FOR_DOCUMENTS = "META-INF";
	public static final String FOLDER_FOR_ASIC_MANIFEST = "ASiCManifest";
	public static final String FOLDER_FOR_SIGNATURES = "signature";
	public static final String FOLDER_FOR_ASIC_MANIFEST01 = "manifest";
	
	public static final String DATA_FROM_DATA_CONTAINER = "XMLDataContainer/XMLData";
	
	public static final String DIGES_MD5 = "http://www.w3.org/2001/04/xmldsig-more#md5";
	public static final String DIGES_SHA1 = "http://www.w3.org/2000/09/xmldsig#sha1";
	public static final String DIGES_SHA256 = "http://www.w3.org/2001/04/xmlenc#sha256";
	public static final String DIGES_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#sha384";
	public static final String DIGES_SHA512 = "http://www.w3.org/2001/04/xmlenc#sha512";
	
	public static final String XADES_SIGNTIME = "DataEnvelope/Signature/Object/QualifyingProperties/SignedProperties/SignedSignatureProperties/SigningTime";
	
	public static final String XADESSIGNTIME = "QualifyingProperties/SignedProperties/SignedSignatureProperties/SigningTime";
	public static final String XADESTIMESTAMP = "QualifyingProperties/UnsignedProperties/UnsignedSignatureProperties/SignatureTimeStamp/EncapsulatedTimeStamp";
	
	public static final String MIME_TYPE_FORM = "application/x-eform-xml";
	public static final String MIME_TYPE_PDF = "application/pdf";
	public static final String MIME_TYPE_FILE = "mimetype";
	public static final String MIME_TYPE_ASICE = "application/vnd.etsi.asic-e+zip";
	public static final String MIME_TYPE_ASICS = "application/vnd.etsi.asic-s+zip";
	
	public static final String XML_CONTAINER = "http://data.gov.sk/def/container/xmldatacontainer+xml/1.1";
	
	
	// XAdES BP Level resolving elements
	public static final String LEVEL_B = "UnsignedProperties";
	public static final String[] LEVEL_LT = {"CertificatesValues","RevocationValues"};
	public static final String LEVEL_LTA = "ArchiveTimeStamp";

}
