package session;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import play.mvc.LegacyWebSocket;
import play.mvc.Result;
import play.mvc.WebSocket.In;
import play.mvc.WebSocket.Out;
import session.batch.BatchChannel;
import session.group.akka.actors.GroupChannel;

/**
 * Builds new WebSockets for either group or batch channel.
 *
 * @author Kristian Lange
 */
public class WebSocketBuilder {

    public static LegacyWebSocket<JsonNode> withGroupChannel(long studyResultId,
            ActorRef groupDispatcher) {
        return withChannel(studyResultId, groupDispatcher, GroupChannel.class);
    }

    public static LegacyWebSocket<JsonNode> withBatchChannel(long studyResultId,
            ActorRef batchDispatcher) {
        return new LegacyWebSocket<JsonNode>() {
            public void onReady(In<JsonNode> in, Out<JsonNode> out) {}

            public boolean isActor() {
                return true;
            }

            public Props actorProps(ActorRef out) {
                try {
                    return Props.create(BatchChannel.class, out, studyResultId,
                            batchDispatcher);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        };

    }

    public static <T> LegacyWebSocket<JsonNode> withChannel(long studyResultId,
            ActorRef dispatcher, Class<T> channelClass) {
        return new LegacyWebSocket<JsonNode>() {
            public void onReady(In<JsonNode> in, Out<JsonNode> out) {
            }

            public boolean isActor() {
                return true;
            }

            public Props actorProps(ActorRef out) {
                try {
                    return Props.create(channelClass, out, studyResultId,
                            dispatcher);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        };
    }

    /**
     * Rejects a WebSocket.
     *
     * @param result The Result that will be returned.
     * @return A rejected WebSocket.
     */
    public static <T> LegacyWebSocket<T> reject(final Result result) {
        return new LegacyWebSocket<T>() {
            public void onReady(In<T> in, Out<T> out) {
            }

            public Result rejectWith() {
                return result;
            }
        };
    }

}
