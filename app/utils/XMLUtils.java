package utils;

import java.io.StringWriter;

import javax.inject.Singleton;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import play.Logger;
import publix.services.PublixUtils;

/**
 * Utils around XML
 * 
 * @author Kristian Lange
 */
@Singleton
public class XMLUtils {

	private static final String CLASS_NAME = PublixUtils.class.getSimpleName();

	/**
	 * Convert XML-Document into a String
	 */
	public static String asString(Document doc) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			return writer.toString();
		} catch (TransformerException e) {
			Logger.info(CLASS_NAME + ".asString: XML to String conversion: ", e);
			return null;
		}
	}

}
