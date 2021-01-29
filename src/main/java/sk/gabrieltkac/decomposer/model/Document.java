package sk.gabrieltkac.decomposer.model;

import java.time.LocalDateTime;
import java.util.List;

public class Document {
	
	private String id;
	private String name;
	private String createdBy;
	private LocalDateTime date;
	private String extension;
	private String MIMEType;
	private String content;
	private String originalContent;
	private List<Signature> signatures;
	private String description;
	
	private String processId;
	private String appicationId;
	private String serviceId;
	
	public Document() {
	}

	public Document(String name, String extension, String mIMEType) {
		this.name = name;
		this.extension = extension;
		MIMEType = mIMEType;
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

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public void setDate(LocalDateTime date) {
		this.date = date;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public String getMIMEType() {
		return MIMEType;
	}

	public void setMIMEType(String mIMEType) {
		MIMEType = mIMEType;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public List<Signature> getSignatures() {
		return signatures;
	}

	public void setSignatures(List<Signature> signatures) {
		this.signatures = signatures;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getProcessId() {
		return processId;
	}

	public String getOriginalContent() {
		return originalContent;
	}

	public void setOriginalContent(String originalContent) {
		this.originalContent = originalContent;
	}

	public void setProcessId(String processId) {
		this.processId = processId;
	}

	public String getAppicationId() {
		return appicationId;
	}

	public void setAppicationId(String appicationId) {
		this.appicationId = appicationId;
	}

	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}
	

}
