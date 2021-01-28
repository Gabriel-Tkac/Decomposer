package sk.gabrieltkac.decomposer.model;

public class CRL {
	
	private String id;
	private String publisher;
	private Object content;
	
	public CRL(String id, String publisher, Object content) {
		this.id = id;
		this.publisher = publisher;
		this.content = content;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getPublisher() {
		return publisher;
	}
	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}
	public Object getContent() {
		return content;
	}
	public void setContent(Object content) {
		this.content = content;
	}
	
	

}
