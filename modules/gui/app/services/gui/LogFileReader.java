package services.gui;

import java.io.File;
import java.io.IOException;

import javax.inject.Singleton;

import org.apache.commons.io.input.ReversedLinesFileReader;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import general.common.Common;
import general.common.MessagesStrings;

/**
 * Class responsible for reading JATOS log file. It's not part of
 * utils.common.IOUtils because it uses Akka Streams.
 * 
 * @author Kristian Lange (2017)
 */
@Singleton
public class LogFileReader {

	/**
	 * Reads logs/application.log file in reverse order and returns it as Akka
	 * Stream source. It maximal reads until line specified in lineLimit.
	 */
	public Source<ByteString, ?> read(int lineLimit) {
		// Prepare a chunked text stream (I have no idea what I'm doing here -
		// https://www.playframework.com/documentation/2.5.x/JavaStream)
		Source<ByteString, ?> source = Source
				.<ByteString>actorRef(256, OverflowStrategy.dropNew())
				.mapMaterializedValue(
						sourceActor -> fillSource(sourceActor, lineLimit));
		return source;
	}

	private Object fillSource(ActorRef sourceActor, int lineLimit) {
		File logFile = new File(Common.getBasepath() + "/logs/application.log");
		try (ReversedLinesFileReader reader = new ReversedLinesFileReader(
				logFile)) {
			String oneLine = reader.readLine();
			int lineNumber = 1;
			while (oneLine != null && lineNumber <= lineLimit) {
				ByteString msg = ByteString
						.fromString(oneLine + System.lineSeparator());
				sourceActor.tell(msg, null);
				oneLine = reader.readLine();
				lineNumber++;
			}
		} catch (IOException e) {
			sourceActor.tell(
					ByteString.fromString(MessagesStrings.COULDNT_OPEN_LOG),
					null);
		} finally {
			sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
		}
		return null;
	}
}
