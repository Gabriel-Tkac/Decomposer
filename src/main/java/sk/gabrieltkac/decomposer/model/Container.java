package sk.gabrieltkac.decomposer.model;

import java.util.List;

public class Container {
	
	private String id;
	private ContainerType containerType;
	private List<Document> documents;
	private List<Signature> signatures;
	
	public Container() {
	}

	public Container(String id, ContainerType containerType) {
		this.id = id;
		this.containerType = containerType;
	}

	public String getId() {
		return id;
	}


	public void setId(String id) {
		this.id = id;
	}


	public List<Document> getDocuments() {
		return documents;
	}


	public void setDocuments(List<Document> documents) {
		this.documents = documents;
	}


	public List<Signature> getSignatures() {
		return signatures;
	}


	public void setSignatures(List<Signature> signatures) {
		this.signatures = signatures;
	}


	public ContainerType getContainerType() {
		return containerType;
	}


	public void setContainerType(ContainerType containerType) {
		this.containerType = containerType;
	}
	
	/**
	 * Vymaze udaje casovej peciatky v kazdom podpise podpisoveho kontajnera
	 */
	public void deleteTimeStamps() {
		for (Signature p : signatures) {
			p.clearTimestamp();
		}
	}

}
