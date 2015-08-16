package publix.controllers.actors;

import play.mvc.WebSocket;
import publix.controllers.actors.actors.GroupChannel;
import publix.controllers.actors.actors.SystemChannel;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Builds new WebSockets on the GroupChannelActor and SystemChannelActor.
 * 
 * @author Kristian Lange
 */
public class WebSocketBuilder {

	public static <A> WebSocket<A> withSystemChannelActor(
			ActorRef systemDispatcherActor, long studyResultId) {
		return new WebSocket<A>() {
			public void onReady(In<A> in, Out<A> out) {
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

	public static <A> WebSocket<A> withGroupChannelActor(long studyResultId,
			ActorRef groupDispatcher, ActorRef systemChannel) {
		return new WebSocket<A>() {
			public void onReady(In<A> in, Out<A> out) {
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

}
