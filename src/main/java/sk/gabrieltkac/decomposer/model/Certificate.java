package sk.gabrieltkac.decomposer.model;

import java.util.Date;

public class Certificate {
	
	private String publisher;
	private String serialNumber;
	private String holderNumber;
	private String holderId;
	private String assignedPerson;
	private Date validFrom;
	private Date validTo;
	private String content;
	
	public Certificate() {
	}

	public Certificate(String publisher, String serialNumber, String holderNumber) {
		this.publisher = publisher;
		this.serialNumber = serialNumber;
		this.holderNumber = holderNumber;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	public String getHolderNumber() {
		return holderNumber;
	}

	public void setHolderNumber(String holderNumber) {
		this.holderNumber = holderNumber;
	}

	public String getHolderId() {
		return holderId;
	}

	public void setHolderId(String holderId) {
		this.holderId = holderId;
	}

	public String getAssignedPerson() {
		return assignedPerson;
	}

	public void setAssignedPerson(String assignedPerson) {
		this.assignedPerson = assignedPerson;
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

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	

}
