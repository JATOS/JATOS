package publix.services;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;
import play.mvc.WebSocket;
import publix.akka.actors.GroupChannel;
import publix.akka.actors.SystemChannel;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Builds new WebSockets on the GroupChannelActor and SystemChannelActor.
 * 
 * @author Kristian Lange
 */
public class WebSocketBuilder {

	public static WebSocket<String> withSystemChannelActor(
			ActorRef systemDispatcherActor, long studyResultId) {
		return new WebSocket<String>() {
			public void onReady(In<String> in, Out<String> out) {
			}

			public boolean isActor() {
				return true;
			}

			public Props actorProps(ActorRef out) {
				try {
					return Props.create(SystemChannel.class, out,
							systemDispatcherActor, studyResultId);
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

	public static WebSocket<JsonNode> withGroupChannelActor(long studyResultId,
			ActorRef groupDispatcher, ActorRef systemChannel) {
		return new WebSocket<JsonNode>() {
			public void onReady(In<JsonNode> in, Out<JsonNode> out) {
			}

			public boolean isActor() {
				return true;
			}

			public Props actorProps(ActorRef out) {
				try {
					return Props.create(GroupChannel.class, out, studyResultId,
							groupDispatcher, systemChannel);
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
     * @param result The result that will be returned.
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
