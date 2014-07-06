package models;

import java.sql.Timestamp;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class MAResult {

	@Id
	@GeneratedValue
	public Long id;
	
	public Timestamp date;
	
	@JsonIgnore
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "component_id")
	public MAComponent component;
	
	public String result;
	
	public MAResult() {
	}
	
	public MAResult(String result, MAComponent component) {
		this.date = new Timestamp(new Date().getTime());
		this.result = result;
		this.component = component;
	}
	
	@Override
	public String toString() {
		return id + ", " + component.id + ", " + date + ", " + result;
	}
	
	public static MAResult findById(Long id) {
		return JPA.em().find(MAResult.class, id);
	}
	
	public void persist() {
		JPA.em().persist(this);
	}
	
	public void remove() {
		JPA.em().remove(this);
	}

}
