package session;

import java.util.Collection;
import java.util.Set;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import akka.actor.ActorRef;

/**
 * This class stores the members of a Dispatcher. It's mostly a wrapper around a
 * map that stores the study result IDs together with their corresponding
 * channels specified by an ActorRef.
 * 
 * A Registry does not define who is member - it just stores the open channels.
 * It's possible that a client is a member but currently doesn't have an open
 * channel.
 * 
 * @author Kristian Lange (2016, 2017)
 */
public class Registry {

	/**
	 * Contains the members that are handled by a dispatcher. Maps StudyResult's
	 * IDs <-> ActorRefs. Using a bidirectional map because it's a one-to-one
	 * relationship.
	 */
	private final BidiMap<Long, ActorRef> channelMap = new DualHashBidiMap<>();

	public void register(long studyResultId, ActorRef channel) {
		channelMap.put(studyResultId, channel);
	}

	public void unregister(long studyResultId) {
		channelMap.remove(studyResultId);
	}

	public ActorRef getChannel(long studyResultId) {
		return channelMap.get(studyResultId);
	}

	public boolean isEmpty() {
		return channelMap.isEmpty();
	}

	public boolean containsStudyResult(Long studyResultId) {
		return channelMap.containsKey(studyResultId);
	}

	public Set<Long> getAllStudyResultIds() {
		return channelMap.keySet();
	}

	public Collection<ActorRef> getAllChannels() {
		return channelMap.values();
	}

	public long getStudyResult(ActorRef channel) {
		return channelMap.getKey(channel);
	}

}