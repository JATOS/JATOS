package services.gui;

import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;
import com.diffplug.common.base.Errors;
import general.common.Common;
import org.apache.commons.io.input.ReversedLinesFileReader;

import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

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
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                    Errors.rethrow().run(() -> streamLogFile(writer, filename, lineLimit));
                    Errors.rethrow().run(writer::flush);
                    Errors.rethrow().run(writer::close);
                }));
    }

    /**
     * This method is very touchy. Change only if you know what you are doing.
     */
    private void streamLogFile(Writer writer, String filename, int lineLimit) throws IOException {
        File logFile = new File(Common.getBasepath() + "/logs/" + filename);
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile, Charset.defaultCharset())) {
            String oneLine = reader.readLine();
            int lineNumber = 1;
            while (oneLine != null && (lineLimit == -1 || lineNumber <= lineLimit)) {
                writer.write(oneLine + System.lineSeparator());
                oneLine = reader.readLine();
                lineNumber++;
            }
        } catch (IOException e) {
            writer.write("Could not open log file '" + filename + "'");
        }
    }
}
