package services.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

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
 * Class responsible for reading JATOS log files. It's not part of
 * utils.common.IOUtils because it uses Akka Streams.
 *
 * @author Kristian Lange (2017)
 */
@Singleton
public class LogFileReader {

    /**
     * Reads from the logs folder the given log file in reverse order and returns it as Akka
     * Stream source. It maximal reads until line specified in lineLimit.
     */
    public Source<ByteString, ?> read(String filename, int lineLimit) {
        // Prepare a chunked text stream (I have no idea what I'm doing here -
        // https://www.playframework.com/documentation/2.5.x/JavaStream)
        return Source.<ByteString>actorRef(lineLimit, OverflowStrategy.dropNew())
                .mapMaterializedValue(sourceActor -> fillSource(sourceActor, filename, lineLimit));
    }

    /**
     * This method is very touchy. Change only if you know what you are doing.
     */
    private Object fillSource(ActorRef sourceActor, String filename, int lineLimit) {
        File logFile = new File(Common.getBasepath() + "/logs/" + filename);
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile, Charset.defaultCharset())) {
            String oneLine = reader.readLine();
            int lineNumber = 1;
            while (oneLine != null && (lineLimit == -1 || lineNumber <= lineLimit)) {
                ByteString msg = ByteString.fromString(oneLine + System.lineSeparator());
                sourceActor.tell(msg, null);
                oneLine = reader.readLine();
                lineNumber++;
            }
        } catch (IOException e) {
            sourceActor.tell(ByteString.fromString(MessagesStrings.COULDNT_OPEN_LOG), null);
        } finally {
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
        }
        return NotUsed.getInstance();
    }
}
