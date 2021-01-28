package sk.gabrieltkac.decomposer.model;

public class DataObject {
	
	private String filename;
	private byte[] data;
	private String extension;
	
	public DataObject(String filename, byte[] data, String extension) {
		this.filename = filename;
		this.data = data;
		this.extension = extension;
	}
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public byte[] getData() {
		return data;
	}
	public void setData(byte[] data) {
		this.data = data;
	}
	public String getExtension() {
		return extension;
	}
	public void setExtension(String extension) {
		this.extension = extension;
	}
	
	
	

}
