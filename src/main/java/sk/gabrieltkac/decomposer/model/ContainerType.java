package sk.gabrieltkac.decomposer.model;

public enum ContainerType {
	
	ASiCe_CAdEs("AsiceCades"),
	CAdES("CAdES"),
	PAdES("Pades"),
	ASiCe_XAdES("AsiceXades"),
	XAdES("XAdES");
	
	private String name;

	private ContainerType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
