package sk.gabrieltkac.decomposer.model;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public class Signature {
	
	private String id;
	private String name;
	private String certificateHolder;
	private String holderId;
	private LocalDateTime signatureTime;
	private String signatureTimeUTC;
	private LocalDateTime timestamp;
	private String timestampUTC;
	
	private Date validFrom;
	private Date validTo;
	private Certificate signatureCertificate;
	private Certificate timestampCertificate;
	private CRL clr;
	private String assignedPerson;
	private List<Document> documents;
	
	private String containerId;
	private String BPLevel;
	private boolean signatureTimeHasBeehShifted;
	
	public Signature() {
	}
	
	public Signature(String name, String certificateHolder) {
		this.name = name;
		this.certificateHolder = certificateHolder;
	}
	
	/**
	 * Clears all Timestamp data
	 */
	public void clearTimestamp() {
		this.timestamp = null;
		this.timestampCertificate = null;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCertificateHolder() {
		return certificateHolder;
	}
	public void setCertificateHolder(String certificateHolder) {
		this.certificateHolder = certificateHolder;
	}
	public String getHolderId() {
		return holderId;
	}
	public void setHolderId(String holderId) {
		this.holderId = holderId;
	}
	public LocalDateTime getSignatureTime() {
		return signatureTime;
	}
	public void setSignatureTime(LocalDateTime signatureTime) {
		this.signatureTime = signatureTime;
	}
	public String getSignatureTimeUTC() {
		return signatureTimeUTC;
	}
	public void setSignatureTimeUTC(String signatureTimeUTC) {
		this.signatureTimeUTC = signatureTimeUTC;
	}
	public LocalDateTime getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}
	public String getTimestampUTC() {
		return timestampUTC;
	}
	public void setTimestampUTC(String timestampUTC) {
		this.timestampUTC = timestampUTC;
	}
	public String getAssignedPerson() {
		return assignedPerson;
	}
	public void setAssignedPerson(String assignedPerson) {
		this.assignedPerson = assignedPerson;
	}
	public List<Document> getDocuments() {
		return documents;
	}
	public void setDocuments(List<Document> documents) {
		this.documents = documents;
	}
	public Date getValidFrom() {
		return validFrom;
	}
	public void setValidFrom(Date validFrom) {
		this.validFrom = validFrom;
	}
	public Date getValidTo() {
		return validTo;
	}
	public void setValidTo(Date validTo) {
		this.validTo = validTo;
	}
	public Certificate getSignatureCertificate() {
		return signatureCertificate;
	}
	public void setSignatureCertificate(Certificate signatureCertificate) {
		this.signatureCertificate = signatureCertificate;
	}
	public Certificate getTimestampCertificate() {
		return timestampCertificate;
	}
	public void setTimestampCertificate(Certificate timestampCertificate) {
		this.timestampCertificate = timestampCertificate;
	}
	public CRL getClr() {
		return clr;
	}
	public void setClr(CRL clr) {
		this.clr = clr;
	}
	public String getContainerId() {
		return containerId;
	}
	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}
	public String getBPLevel() {
		return BPLevel;
	}
	public void setBPLevel(String bPLevel) {
		BPLevel = bPLevel;
	}
	public boolean isSignatureTimeHasBeehShifted() {
		return signatureTimeHasBeehShifted;
	}
	public void setSignatureTimeHasBeehShifted(boolean signatureTimeHasBeehShifted) {
		this.signatureTimeHasBeehShifted = signatureTimeHasBeehShifted;
	}
	
}
