package services.publix.group;

import play.mvc.Result;
import play.mvc.WebSocket;
import services.publix.group.akka.actors.GroupChannel;
import akka.actor.ActorRef;
import akka.actor.Props;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Builds new WebSockets for group channel.
 * 
 * @author Kristian Lange
 */
public class WebSocketBuilder {

	public static WebSocket<JsonNode> withGroupChannel(long studyResultId,
			ActorRef groupDispatcher) {
		return new WebSocket<JsonNode>() {
			public void onReady(In<JsonNode> in, Out<JsonNode> out) {
			}

			public boolean isActor() {
				return true;
			}

			public Props actorProps(ActorRef out) {
				try {
					return Props.create(GroupChannel.class, out, studyResultId,
							groupDispatcher);
				} catch (RuntimeException e) {
					throw e;
				} catch (Error e) {
					throw e;
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
			}
		};
	}

	/**
	 * Rejects a WebSocket.
	 *
	 * @param result
	 *            The Result that will be returned.
	 * @return A rejected WebSocket.
	 */
	public static <T> WebSocket<T> reject(final Result result) {
		return new WebSocket<T>() {
			public void onReady(In<T> in, Out<T> out) {
			}

			public Result rejectWith() {
				return result;
			}
		};
	}

}
